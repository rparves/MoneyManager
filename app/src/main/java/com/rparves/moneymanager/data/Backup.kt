package com.rparves.moneymanager.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.rparves.moneymanager.app
import com.rparves.moneymanager.reminder.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ImportResult(val added: Int, val duplicates: Int, val skipped: Int)

/** CSV export/import and full JSON backup/restore. Everything runs off the main thread. */
object Backup {
    const val AUTO_FILE = "MoneyManager-backup.json"
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    private fun Context.out(uri: Uri): OutputStream =
        runCatching { contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: contentResolver.openOutputStream(uri) ?: error("Couldn't open the file for writing")

    private fun Context.readText(uri: Uri): String =
        (contentResolver.openInputStream(uri) ?: error("Couldn't open the file"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix("\uFEFF")

    private fun minorToText(m: Long) = String.format(Locale.US, "%s%d.%02d", if (m < 0) "-" else "", kotlin.math.abs(m) / 100, kotlin.math.abs(m) % 100)

    private fun csvCell(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    // ---------- CSV export ----------
    suspend fun exportCsv(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val dao = ctx.app().dao
        val accs = dao.allAccounts().associateBy { it.id }
        val cats = dao.allCategories().associateBy { it.id }
        val txns = dao.allTxns()
        val zone = ZoneId.systemDefault()
        ctx.out(uri).bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("\uFEFF") // lets Excel read ৳ and other symbols correctly
            w.write("Date,Type,Account,Category,Subcategory,Amount,Note\n")
            txns.forEach { t ->
                val cells = listOf(
                    Instant.ofEpochMilli(t.date).atZone(zone).format(stamp),
                    if (t.isIncome) "Income" else "Expense",
                    accs[t.accountId]?.name ?: "",
                    cats[t.categoryId]?.name ?: "",
                    t.subId?.let { cats[it]?.name } ?: "",
                    minorToText(t.amount),
                    t.note,
                )
                w.write(cells.joinToString(",") { csvCell(it) })
                w.write("\n")
            }
        }
        txns.size
    }

    // ---------- CSV import ----------
    fun parseCsv(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delim = listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                delim -> { row.add(cell.toString()); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = mutableListOf() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private val dateTimeFormats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm", "dd-MM-yyyy HH:mm", "M/d/yyyy H:mm")
        .map { DateTimeFormatter.ofPattern(it, Locale.US) }
    private val dateFormats = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "d/M/yyyy", "dd.MM.yyyy", "MMM d, yyyy", "d MMM yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.US) }

    private fun parseDate(raw: String): Long? {
        val s = raw.trim()
        val zone = ZoneId.systemDefault()
        dateTimeFormats.forEach { f -> runCatching { return LocalDateTime.parse(s, f).atZone(zone).toInstant().toEpochMilli() } }
        dateFormats.forEach { f -> runCatching { return LocalDate.parse(s, f).atTime(12, 0).atZone(zone).toInstant().toEpochMilli() } }
        return null
    }

    private fun parseAmount(raw: String): Long? {
        val cleaned = raw.filter { it.isDigit() || it == '.' || it == '-' }
        val bd = cleaned.toBigDecimalOrNull() ?: return null
        return bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun guessType(name: String): String {
        val n = name.lowercase()
        return when {
            "card" in n || "credit" in n -> "card"
            "bank" in n -> "bank"
            "wallet" in n || "bkash" in n || "nagad" in n || "rocket" in n -> "wallet"
            else -> "cash"
        }
    }

    suspend fun importCsv(ctx: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val dao = ctx.app().dao
        val rows = parseCsv(ctx.readText(uri))
        require(rows.size >= 2) { "The file has no transactions" }
        val header = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String): Int {
            val exact = header.indexOfFirst { it in names }
            return if (exact >= 0) exact else header.indexOfFirst { h -> names.any { h.startsWith(it) } }
        }
        val iDate = col("date", "time", "date/time")
        val iType = col("type", "income/expense", "in/out", "kind")
        val iAcc = col("account", "accounts", "wallet")
        val iCat = col("category")
        val iSub = col("subcategory", "sub category", "sub-category")
        val iAmt = col("amount", "value", "sum")
        val iNote = col("note", "memo", "description", "notes")
        require(iDate >= 0 && iAmt >= 0) { "Couldn't find Date and Amount columns" }

        val accounts = dao.allAccounts().associateBy { it.name.lowercase() }.toMutableMap()
        val cats = dao.allCategories()
        val tops = cats.filter { it.parentId == null }.associateBy { it.name.lowercase() to it.isIncome }.toMutableMap()
        val subs = cats.filter { it.parentId != null }.associateBy { it.parentId!! to it.name.lowercase() }.toMutableMap()
        fun keyOf(t: Txn) = "${t.date / 60000}|${t.amount}|${t.isIncome}|${t.accountId}|${t.categoryId}|${t.note}"
        val seen = dao.allTxns().map { keyOf(it) }.toMutableSet()

        val out = mutableListOf<Txn>()
        var dup = 0
        var skipped = 0
        for (r in rows.drop(1)) {
            fun get(i: Int) = if (i in r.indices) r[i].trim() else ""
            val date = parseDate(get(iDate))
            val signed = parseAmount(get(iAmt))
            if (date == null || signed == null || signed == 0L) { skipped++; continue }
            val typeText = get(iType).lowercase()
            val isIncome = when {
                typeText.startsWith("inc") || typeText == "in" || typeText == "+" -> true
                typeText.startsWith("exp") || typeText == "out" || typeText == "-" -> false
                else -> false
            }
            val accName = get(iAcc).ifBlank { accounts.values.firstOrNull()?.name ?: "Cash" }
            val acc = accounts[accName.lowercase()] ?: Account(name = accName, type = guessType(accName)).let { a ->
                a.copy(id = dao.insertAccount(a)).also { accounts[accName.lowercase()] = it }
            }
            val catName = get(iCat).ifBlank { "Other" }
            val cat = tops[catName.lowercase() to isIncome] ?: Category(
                name = catName, isIncome = isIncome, color = Seed.palette[Math.floorMod(catName.lowercase().hashCode(), Seed.palette.size)],
            ).let { c -> c.copy(id = dao.insertCategory(c)).also { tops[catName.lowercase() to isIncome] = it } }
            val subName = get(iSub)
            val sub = if (subName.isBlank()) null else subs[cat.id to subName.lowercase()] ?: Category(
                name = subName, isIncome = isIncome, color = cat.color, parentId = cat.id,
            ).let { c -> c.copy(id = dao.insertCategory(c)).also { subs[cat.id to subName.lowercase()] = it } }

            val t = Txn(
                isIncome = isIncome, amount = kotlin.math.abs(signed), categoryId = cat.id, accountId = acc.id,
                note = get(iNote), date = date, subId = sub?.id,
            )
            if (!seen.add(keyOf(t))) { dup++; continue }
            out.add(t)
        }
        if (out.isNotEmpty()) dao.insertTxns(out)
        ImportResult(out.size, dup, skipped)
    }

    // ---------- JSON backup ----------
    suspend fun backupJson(ctx: Context): String = withContext(Dispatchers.IO) {
        val app = ctx.app()
        val dao = app.dao
        val p = app.prefs
        JSONObject().apply {
            put("app", "MoneyManager")
            put("format", 2)
            put("createdAt", System.currentTimeMillis())
            put("settings", JSONObject().apply {
                put("currency", p.currency); put("name", p.name)
                put("reminderOn", p.reminderOn); put("reminderHour", p.reminderHour); put("reminderMinute", p.reminderMinute)
            })
            put("accounts", JSONArray(dao.allAccounts().map {
                JSONObject().put("id", it.id).put("name", it.name).put("initial", it.initial).put("type", it.type)
            }))
            put("categories", JSONArray(dao.allCategories().map {
                JSONObject().put("id", it.id).put("name", it.name).put("isIncome", it.isIncome).put("color", it.color)
                    .put("parentId", it.parentId ?: JSONObject.NULL)
            }))
            put("transactions", JSONArray(dao.allTxns().map {
                JSONObject().put("id", it.id).put("isIncome", it.isIncome).put("amount", it.amount)
                    .put("categoryId", it.categoryId).put("accountId", it.accountId).put("note", it.note)
                    .put("date", it.date).put("subId", it.subId ?: JSONObject.NULL)
            }))
            put("goals", JSONArray(dao.allGoals().map {
                JSONObject().put("id", it.id).put("name", it.name).put("target", it.target).put("saved", it.saved)
            }))
            put("bills", JSONArray(dao.billsList().map {
                JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("dueDay", it.dueDay).put("paidMonth", it.paidMonth)
            }))
        }.toString()
    }

    suspend fun writeBackup(ctx: Context, uri: Uri) {
        val json = backupJson(ctx)
        withContext(Dispatchers.IO) { ctx.out(uri).bufferedWriter(Charsets.UTF_8).use { it.write(json) } }
        withContext(Dispatchers.Main) { ctx.app().prefs.lastBackup = System.currentTimeMillis() }
    }

    private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

    /** Replaces everything in the app with the backup. Returns the number of transactions restored. */
    suspend fun restore(ctx: Context, uri: Uri): Int {
        val app = ctx.app()
        val text = withContext(Dispatchers.IO) { ctx.readText(uri) }
        val o = runCatching { JSONObject(text) }.getOrNull()
        require(o != null && o.optString("app") == "MoneyManager") { "This isn't a Money Manager backup file" }
        val accounts = o.optJSONArray("accounts").mapObjects {
            Account(it.getLong("id"), it.getString("name"), it.optLong("initial"), it.optString("type", "cash"))
        }
        require(accounts.isNotEmpty()) { "The backup has no accounts" }
        val categories = o.optJSONArray("categories").mapObjects {
            Category(it.getLong("id"), it.getString("name"), it.getBoolean("isIncome"), it.getLong("color"), it.longOrNull("parentId"))
        }
        val txns = o.optJSONArray("transactions").mapObjects {
            Txn(
                it.getLong("id"), it.getBoolean("isIncome"), it.getLong("amount"), it.getLong("categoryId"),
                it.getLong("accountId"), it.optString("note"), it.getLong("date"), it.longOrNull("subId"),
            )
        }
        val goals = o.optJSONArray("goals").mapObjects { Goal(it.getLong("id"), it.getString("name"), it.getLong("target"), it.optLong("saved")) }
        val bills = o.optJSONArray("bills").mapObjects {
            Bill(it.getLong("id"), it.getString("name"), it.getLong("amount"), it.getInt("dueDay"), it.optString("paidMonth"))
        }
        withContext(Dispatchers.IO) { app.dao.replaceAll(accounts, categories, txns, goals, bills) }
        withContext(Dispatchers.Main) {
            o.optJSONObject("settings")?.let { s ->
                app.prefs.currency = s.optString("currency", app.prefs.currency)
                app.prefs.name = s.optString("name", app.prefs.name)
                app.prefs.reminderOn = s.optBoolean("reminderOn", app.prefs.reminderOn)
                app.prefs.reminderHour = s.optInt("reminderHour", app.prefs.reminderHour)
                app.prefs.reminderMinute = s.optInt("reminderMinute", app.prefs.reminderMinute)
            }
            Reminder.schedule(ctx)
        }
        return txns.size
    }

    // ---------- Automatic daily backup into a folder the user picked ----------
    suspend fun autoBackup(ctx: Context): Boolean {
        val prefs = ctx.app().prefs
        val treeText = withContext(Dispatchers.Main) { prefs.backupTree }
        if (treeText.isBlank()) return false
        val tree = Uri.parse(treeText)
        val target = withContext(Dispatchers.IO) {
            val cr = ctx.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
            var found: Uri? = null
            cr.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == AUTO_FILE) { found = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)); break }
                }
            }
            found ?: DocumentsContract.createDocument(
                cr, DocumentsContract.buildDocumentUriUsingTree(tree, rootId), "application/json", AUTO_FILE,
            )
        } ?: return false
        writeBackup(ctx, target)
        return true
    }

    fun folderLabel(treeText: String): String {
        if (treeText.isBlank()) return ""
        val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeText)) }.getOrDefault(treeText)
        return id.substringAfter(':').ifBlank { "Internal storage" }
    }
}
