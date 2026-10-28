package com.rparves.moneymanager.ai

import com.rparves.moneymanager.data.Account
import java.time.DayOfWeek
import java.time.LocalDate

/** Rule-based reading used when no AI key is set (or the AI is unreachable). */
object LocalParser {
    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    fun normalize(s: String): String = buildString {
        s.forEach { c -> val i = BN_DIGITS.indexOf(c); append(if (i >= 0) '0' + i else c) }
    }

    private val keywordCats = listOf(
        "Food" to listOf("lunch", "dinner", "breakfast", "restaurant", "cafe", "café", "coffee", "tea", "food", "pizza", "burger", "biryani", "kacchi", "kabab", "খাবার", "নাস্তা", "দুপুরের খাবার"),
        "Snacks" to listOf("snack", "snacks", "chips", "chocolate", "ice cream", "নাস্তা"),
        "Groceries" to listOf("grocery", "groceries", "bazar", "bazaar", "vegetable", "vegetables", "fish", "meat", "rice", "super shop", "supershop", "mart", "shwapno", "agora", "বাজার"),
        "Transport" to listOf("bus", "uber", "pathao", "rickshaw", "cng", "taxi", "train", "metro", "fuel", "petrol", "octane", "diesel", "fare", "ride", "রিকশা", "ভাড়া"),
        "Bills" to listOf("bill", "electricity", "electric", "internet", "wifi", "gas", "water", "recharge", "desco", "dpdc", "বিল"),
        "Rent" to listOf("rent", "house rent", "বাসা ভাড়া"),
        "Health" to listOf("medicine", "pharmacy", "doctor", "hospital", "clinic", "diagnostic", "ঔষধ", "ওষুধ", "ডাক্তার"),
        "Shopping" to listOf("shirt", "clothes", "dress", "shoe", "shoes", "shopping", "daraz", "mall", "jacket", "panjabi"),
        "Education" to listOf("tuition", "book", "books", "course", "school", "college", "university", "fees", "exam"),
        "Leisure" to listOf("movie", "cinema", "netflix", "game", "trip", "tour", "concert"),
        "Salary" to listOf("salary", "বেতন"),
        "Freelance" to listOf("freelance", "upwork", "fiverr", "client payment"),
        "Refund" to listOf("refund", "cashback"),
        "Gift" to listOf("gift", "উপহার"),
    )
    private val incomeWords = listOf("received", "receive", "got paid", "salary", "income", "earned", "refund", "cashback", "পেয়েছি", "আয়", "বেতন")

    private fun has(text: String, word: String): Boolean {
        val w = Regex.escape(word.lowercase())
        return Regex("(^|[^\\p{L}\\p{M}])$w($|[^\\p{L}\\p{M}])").containsMatchIn(text)
    }

    private fun matchCategory(text: String, c: Catalog, income: Boolean): Pair<String?, String?> {
        val subs = c.cats.filter { it.parentId != null && it.isIncome == income }.sortedByDescending { it.name.length }
        subs.firstOrNull { has(text, it.name) }?.let { s -> return c.cats.firstOrNull { it.id == s.parentId }?.name to s.name }
        val tops = c.cats.filter { it.parentId == null && it.isIncome == income }.sortedByDescending { it.name.length }
        tops.firstOrNull { has(text, it.name) }?.let { return it.name to null }
        keywordCats.firstOrNull { (cat, words) -> tops.any { it.name.equals(cat, true) } && words.any { has(text, it) } }
            ?.let { return it.first to null }
        return null to null
    }

    private fun matchAccount(text: String, accounts: List<Account>): String? {
        accounts.sortedByDescending { it.name.length }.firstOrNull { has(text, it.name) }?.let { return it.name }
        fun ofType(t: String) = accounts.firstOrNull { it.type == t }?.name
        return when {
            listOf("bkash", "nagad", "rocket", "upay", "wallet", "বিকাশ", "নগদ").any { has(text, it) } -> ofType("wallet")
            listOf("credit card", "card", "visa", "mastercard", "amex").any { has(text, it) } -> ofType("card")
            listOf("bank", "transfer", "cheque").any { has(text, it) } -> ofType("bank")
            listOf("cash", "ক্যাশ").any { has(text, it) } -> ofType("cash")
            else -> null
        }
    }

    private val numberRe = Regex("(\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:\\.(\\d{1,2}))?(\\s*k\\b)?")

    private fun toMinor(m: MatchResult): Long {
        val whole = m.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
        val frac = m.groupValues[2].padEnd(2, '0').toLongOrNull() ?: 0L
        val minor = whole * 100 + frac
        return if (m.groupValues[3].isNotBlank()) minor * 1000 else minor
    }

    // ---------- Voice ----------
    fun voice(spoken: String, c: Catalog, today: LocalDate): Draft {
        val text = normalize(spoken).lowercase()
        val income = incomeWords.any { has(text, it) || text.contains(it) }
        val numbers = numberRe.findAll(text).map { toMinor(it) }.filter { it > 0 }.toList()
        val amount = when {
            numbers.isEmpty() -> null
            numbers.size > 1 && (has(text, "and") || text.contains("+") || has(text, "plus") || text.contains(" আর ")) -> numbers.sum()
            else -> numbers.first()
        }
        val date = when {
            text.contains("day before yesterday") || text.contains("পরশু") -> today.minusDays(2)
            has(text, "yesterday") || text.contains("গতকাল") -> today.minusDays(1)
            has(text, "today") || text.contains("আজ") -> today
            else -> DayOfWeek.values().firstOrNull { has(text, it.name.lowercase()) }?.let { d ->
                var x = today.minusDays(1)
                while (x.dayOfWeek != d) x = x.minusDays(1)
                x
            }
        }
        val (cat, sub) = matchCategory(text, c, income)
        val merchant = Regex("\\b(?:at|from)\\s+([\\p{L}][\\p{L}\\p{M}'&.-]*(?:\\s+[\\p{L}][\\p{L}\\p{M}'&.-]*){0,2})")
            .find(normalize(spoken))?.groupValues?.get(1)
            ?.split(" ")?.takeWhile { it.lowercase() !in setOf("for", "with", "by", "using", "on", "yesterday", "today", "and", "taka", "tk") }
            ?.joinToString(" ")?.takeIf { it.isNotBlank() }
        return Draft(
            amount = amount,
            isIncome = income,
            date = date,
            category = cat,
            subcategory = sub,
            account = matchAccount(text, c.accounts),
            note = merchant ?: spoken.trim().replaceFirstChar { it.uppercase() }.take(60),
            lowConfidence = amount == null || cat == null,
        )
    }

    // ---------- Receipt text from on-device OCR ----------
    private val totalKeys = listOf(
        "grand total", "net payable", "total payable", "amount payable", "net amount", "total amount",
        "amount due", "balance due", "total due", "payable", "net total", "total",
    )
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun findTotal(lines: List<String>): Long? {
        for (key in totalKeys) {
            for ((i, line) in lines.withIndex()) {
                val l = line.lowercase()
                if (!l.contains(key)) continue
                if (key == "total" && listOf("sub total", "subtotal", "sub-total", "total qty", "total item", "total quantity").any { l.contains(it) }) continue
                val here = numberRe.findAll(l.substringAfter(key)).map { toMinor(it) }.filter { it > 0 }.toList()
                if (here.isNotEmpty()) return here.last()
                val next = lines.getOrNull(i + 1)?.let { n -> numberRe.findAll(n).map { toMinor(it) }.filter { it > 0 }.lastOrNull() }
                if (next != null) return next
            }
        }
        return lines.flatMap { l -> Regex("\\d[\\d,]*\\.\\d{2}\\b").findAll(l).mapNotNull { m -> numberRe.find(m.value)?.let { toMinor(it) } }.toList() }
            .maxOrNull()
    }

    private fun findDate(text: String, today: LocalDate): LocalDate? {
        fun ok(y: Int, m: Int, d: Int): LocalDate? = runCatching {
            val yy = if (y < 100) 2000 + y else y
            LocalDate.of(yy, m, d).takeIf { it.year >= 2000 && !it.isAfter(today.plusDays(1)) }
        }.getOrNull()
        Regex("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b").find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            ok(y.toInt(), mo.toInt(), d.toInt())?.let { return it }
        }
        Regex("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})\\b").find(text)?.let { m ->
            val (a, b, y) = m.destructured
            (if (b.toInt() > 12) ok(y.toInt(), a.toInt(), b.toInt()) else ok(y.toInt(), b.toInt(), a.toInt()))?.let { return it }
        }
        Regex("\\b(\\d{1,2})[\\s-]*([a-z]{3})[a-z]*[\\s,.-]*(\\d{2,4})\\b").find(text)?.let { m ->
            val (d, mon, y) = m.destructured
            val mi = months.indexOf(mon)
            if (mi >= 0) ok(y.toInt(), mi + 1, d.toInt())?.let { return it }
        }
        Regex("\\b([a-z]{3})[a-z]*\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})\\b").find(text)?.let { m ->
            val (mon, d, y) = m.destructured
            val mi = months.indexOf(mon)
            if (mi >= 0) ok(y.toInt(), mi + 1, d.toInt())?.let { return it }
        }
        return null
    }

    fun receipt(ocrText: String, c: Catalog, today: LocalDate): Draft {
        val text = normalize(ocrText)
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lower = text.lowercase()
        val skip = listOf("invoice", "receipt", "tax", "vat", "bill no", "date", "tel", "phone", "mobile", "www", "http", "bin", "mushak", "cash memo", "welcome", "order")
        val merchant = lines.take(5).firstOrNull { l ->
            l.count { it.isLetter() } >= 3 && skip.none { l.lowercase().contains(it) } && l.length <= 40
        }
        val (cat, sub) = matchCategory(lower, c, false)
        val total = findTotal(lines)
        return Draft(
            amount = total,
            isIncome = false,
            date = findDate(lower, today),
            category = cat,
            subcategory = sub,
            account = matchAccount(lower.replace("cash memo", ""), c.accounts),
            note = merchant,
            lowConfidence = total == null,
        )
    }
}
