package com.rparves.moneymanager.ai

import com.rparves.moneymanager.data.Account
import com.rparves.moneymanager.data.AccountTypes
import com.rparves.moneymanager.data.Category
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** What a scan or voice note produced. Names are matched to the user's categories and accounts afterwards. */
data class Draft(
    val amount: Long? = null,
    val isIncome: Boolean? = null,
    val date: LocalDate? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val account: String? = null,
    val note: String? = null,
    val lowConfidence: Boolean = false,
)

data class SmartResult(val draft: Draft, val usedAi: Boolean, val heard: String? = null, val warning: String? = null)

class Catalog(val cats: List<Category>, val accounts: List<Account>)

object Prompts {
    private const val JSON_SPEC = "Reply with only one JSON object and nothing else (no markdown), in exactly this shape:\n" +
        "{\"type\":\"expense or income\",\"amount\":number,\"date\":\"YYYY-MM-DD or null\"," +
        "\"category\":\"exact category name from the lists, or null\",\"subcategory\":\"exact subcategory name from the lists, or null\"," +
        "\"account\":\"exact account name from the list, or null\",\"note\":\"short note\",\"confidence\":\"high, medium or low\"}"

    private fun catalog(c: Catalog, cur: String, today: LocalDate): String = buildString {
        val day = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        append("Today is ").append(today).append(" (").append(day).append("). The user's currency symbol is ").append(cur).append(".\n")
        listOf(false, true).forEach { income ->
            append(if (income) "Income categories" else "Expense categories")
            append(" (subcategories in brackets): ")
            append(
                c.cats.filter { it.isIncome == income && it.parentId == null }.joinToString("; ") { top ->
                    val subs = c.cats.filter { it.parentId == top.id }.joinToString(", ") { it.name }
                    if (subs.isEmpty()) top.name else "${top.name} [$subs]"
                }
            )
            append("\n")
        }
        append("Accounts: ").append(c.accounts.joinToString("; ") { "${it.name} (${AccountTypes.label(it.type)})" }).append("\n")
    }

    fun receipt(c: Catalog, cur: String, today: LocalDate): String =
        "You read a receipt, invoice or bill from the attached image and turn it into one entry for a personal money manager app.\n" +
            catalog(c, cur, today) +
            "Rules:\n" +
            "- amount: the final total actually paid (after discounts, VAT and service charge) as a plain number, without currency symbols or thousands separators.\n" +
            "- date: the purchase or invoice date; null if none is printed.\n" +
            "- type: income only if the document shows money the user received (a salary slip, a refund); otherwise expense.\n" +
            "- category: the best match from the lists for what was bought; subcategory only if one clearly fits.\n" +
            "- account: only if the payment method is printed (a card payment means a Credit card account, bKash/Nagad/Rocket means a Mobile wallet account, cash means a Cash account); otherwise null.\n" +
            "- note: the shop or company name, plus a 2 to 4 word summary if helpful.\n" +
            JSON_SPEC

    fun voice(c: Catalog, cur: String, today: LocalDate, spoken: String): String =
        "You turn a short spoken note into one entry for a personal money manager app. " +
            "The speech may be in English, Bangla or a mix, and may contain speech-recognition mistakes.\n" +
            catalog(c, cur, today) +
            "Rules:\n" +
            "- amount: the money amount mentioned; if several items of one purchase are listed, add them up.\n" +
            "- date: resolve words like today, yesterday or last Friday relative to today; null if no day is mentioned.\n" +
            "- type: income for money received (salary, got paid, received, refund); otherwise expense.\n" +
            "- category, subcategory and account: choose only from the lists above; null if unclear.\n" +
            "- note: a short clean description such as the shop or item, not the whole sentence.\n" +
            JSON_SPEC + "\nSpoken text: \"" + spoken.replace("\"", "'") + "\""

    fun parse(reply: String): Draft {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        require(start >= 0 && end > start) { "The AI reply didn't contain an entry" }
        val o = JSONObject(reply.substring(start, end + 1))
        fun str(k: String): String? =
            if (!o.has(k) || o.isNull(k)) null else o.optString(k).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
        val amountText = str("amount")?.replace(",", "")?.filter { it.isDigit() || it == '.' }
        val amount = amountText?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            ?.multiply(BigDecimal(100))?.setScale(0, RoundingMode.HALF_UP)?.toLong()
        val date = str("date")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return Draft(
            amount = amount,
            isIncome = str("type")?.lowercase()?.startsWith("inc"),
            date = date,
            category = str("category"),
            subcategory = str("subcategory"),
            account = str("account"),
            note = str("note"),
            lowConfidence = str("confidence")?.lowercase() == "low",
        )
    }
}
