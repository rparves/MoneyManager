package com.rparves.moneymanager.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A small .xlsx reader/writer (first sheet only), so the app doesn't need a heavy spreadsheet library. */
object Xlsx {
    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    // ---------------- writing ----------------

    /** Cells may be String, Number/BigDecimal (shown as #,##0.00) or LocalDateTime (shown as a real Excel date). */
    fun write(out: OutputStream, sheetName: String, header: List<String>, rows: List<List<Any>>) {
        val zip = ZipOutputStream(out)
        fun put(name: String, body: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(body.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        put(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""",
        )
        put(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""",
        )
        put(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="${esc(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets></workbook>""",
        )
        put(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""",
        )
        // Styles: 0 normal, 1 header, 2 date, 3 money
        put(
            "xl/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="$MAIN">
<numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy-mm-dd hh:mm"/><numFmt numFmtId="165" formatCode="#,##0.00"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF15151C"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""",
        )

        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        val w = zip.bufferedWriter(Charsets.UTF_8)
        w.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        w.write("""<worksheet xmlns="$MAIN" xmlns:r="$REL">""")
        w.write("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        val widths = listOf(18, 10, 16, 16, 16, 14, 40)
        w.write("<cols>")
        header.indices.forEach { i -> w.write("""<col min="${i + 1}" max="${i + 1}" width="${widths.getOrElse(i) { 16 }}" customWidth="1"/>""") }
        w.write("</cols><sheetData>")
        fun row(r: Int, cells: List<Any>, header: Boolean) {
            w.write("""<row r="$r">""")
            cells.forEachIndexed { i, v ->
                val ref = colName(i) + r
                when {
                    header -> w.write("""<c r="$ref" t="inlineStr" s="1"><is><t>${esc(v.toString())}</t></is></c>""")
                    v is LocalDateTime -> w.write("""<c r="$ref" s="2"><v>${serial(v)}</v></c>""")
                    v is BigDecimal -> w.write("""<c r="$ref" s="3"><v>${v.toPlainString()}</v></c>""")
                    v is Number -> w.write("""<c r="$ref" s="3"><v>$v</v></c>""")
                    v.toString().isEmpty() -> {}
                    else -> w.write("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            w.write("</row>")
        }
        row(1, header, true)
        rows.forEachIndexed { i, cells -> row(i + 2, cells, false) }
        w.write("</sheetData></worksheet>")
        w.flush()
        zip.closeEntry()
        zip.finish()
        zip.flush()
    }

    private fun serial(t: LocalDateTime): String {
        val base = LocalDate.of(1899, 12, 30).atStartOfDay()
        val secs = ChronoUnit.SECONDS.between(base, t)
        return BigDecimal(secs).divide(BigDecimal(86400), 8, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.append('A' + r); n = (n - 1) / 26 }
        return sb.reverse().toString()
    }

    private fun esc(s: String): String = buildString {
        s.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\t' || c == '\n' || c == '\r' -> append(c)
                c < ' ' || c == '\uFFFE' || c == '\uFFFF' -> {}
                else -> append(c)
            }
        }
    }

    // ---------------- reading ----------------

    /** Returns the first worksheet as rows of text. Dates come back as Excel day numbers. */
    fun read(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name.trimStart('/')
                if (n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || n == "xl/sharedStrings.xml" || n.startsWith("xl/worksheets/sheet")) {
                    files[n] = z.readBytes()
                }
                e = z.nextEntry
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val sheet = firstSheetPath(files) ?: throw IllegalArgumentException("No worksheet found in this Excel file")
        return sheetRows(files[sheet] ?: throw IllegalArgumentException("No worksheet found in this Excel file"), shared)
    }

    private fun parser(b: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(b), null)
        }

    private fun XmlPullParser.local(): String = name.substringAfter(':')
    private fun XmlPullParser.attr(local: String): String? =
        (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfter(':') == local }?.let { getAttributeValue(it) }

    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        val rid = files["xl/workbook.xml"]?.let { b ->
            val p = parser(b)
            var found: String? = null
            while (p.next() != XmlPullParser.END_DOCUMENT && found == null) {
                if (p.eventType == XmlPullParser.START_TAG && p.local() == "sheet") found = p.attr("id")
            }
            found
        }
        val target = if (rid != null) files["xl/_rels/workbook.xml.rels"]?.let { b ->
            val p = parser(b)
            var t: String? = null
            while (p.next() != XmlPullParser.END_DOCUMENT && t == null) {
                if (p.eventType == XmlPullParser.START_TAG && p.local() == "Relationship" && p.getAttributeValue(null, "Id") == rid) {
                    t = p.getAttributeValue(null, "Target")
                }
            }
            t
        } else null
        val path = target?.let { if (it.startsWith("/")) it.trimStart('/') else "xl/" + it.removePrefix("./") }
        return path?.takeIf { files.containsKey(it) } ?: files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minOrNull()
    }

    private fun sharedStrings(b: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = parser(b)
        var cur: StringBuilder? = null
        var inT = false
        var inPhonetic = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.local()) {
                    "si" -> cur = StringBuilder()
                    "t" -> inT = true
                    "rPh" -> inPhonetic = true
                }
                XmlPullParser.TEXT -> if (inT && !inPhonetic) cur?.append(p.text)
                XmlPullParser.END_TAG -> when (p.local()) {
                    "si" -> { out.add(cur?.toString() ?: ""); cur = null }
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                }
            }
        }
        return out
    }

    private fun colIndex(ref: String?): Int? {
        if (ref == null) return null
        var n = 0
        for (c in ref) { if (c.isLetter()) n = n * 26 + (c.uppercaseChar() - 'A' + 1) else break }
        return if (n == 0) null else n - 1
    }

    private fun sheetRows(b: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val p = parser(b)
        var row: MutableList<String>? = null
        var col = 0
        var type: String? = null
        var value = StringBuilder()
        var inValue = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.local()) {
                    "row" -> { row = ArrayList(); col = 0 }
                    "c" -> {
                        col = colIndex(p.attr("r")) ?: col
                        type = p.attr("t")
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.local()) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val r = row
                        if (r != null) {
                            val raw = value.toString()
                            val text = when (type) {
                                "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            while (r.size < col) r.add("")
                            if (r.size == col) r.add(text) else r[col] = text
                        }
                        col++
                    }
                    "row" -> { row?.let { if (it.any { c -> c.isNotBlank() }) rows.add(it) }; row = null }
                }
            }
        }
        return rows
    }
}
