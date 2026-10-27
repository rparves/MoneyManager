package com.rparves.moneymanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rparves.moneymanager.app
import com.rparves.moneymanager.ui.theme.LocalExtra
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private enum class Period { Week, Month, Year }
private data class Span(val start: LocalDate, val end: LocalDate, val label: String)
private data class Slice(val id: Long, val name: String, val color: Long, val amount: Long)

@Composable
fun ReportsScreen() {
    val app = LocalContext.current.app()
    val dao = app.dao
    val cur = app.prefs.currency
    val ex = LocalExtra.current
    var period by rememberSaveable { mutableStateOf(Period.Month) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var incomeView by rememberSaveable { mutableStateOf(false) }
    var openCat by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = remember { LocalDate.now() }

    val span = remember(period, offset) {
        when (period) {
            Period.Week -> {
                val s = today.with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())
                val e = s.plusDays(7)
                Span(s, e, s.fmt("MMM d") + " – " + e.minusDays(1).fmt("MMM d"))
            }
            Period.Month -> {
                val m = YearMonth.from(today).plusMonths(offset.toLong())
                Span(m.atDay(1), m.plusMonths(1).atDay(1), m.label())
            }
            Period.Year -> {
                val y = today.year + offset
                Span(LocalDate.of(y, 1, 1), LocalDate.of(y + 1, 1, 1), y.toString())
            }
        }
    }
    val rows by remember(span) { dao.txnsBetween(span.start.startMillis(), span.end.startMillis()) }.collectAsState(emptyList())
    val nowYm = remember { YearMonth.now() }
    val months = remember { (5 downTo 0).map { nowYm.minusMonths(it.toLong()) } }
    val trend by remember {
        dao.txnsBetween(months.first().atDay(1).startMillis(), nowYm.plusMonths(1).atDay(1).startMillis())
    }.collectAsState(emptyList())

    val income = rows.filter { it.t.isIncome }.sumOf { it.t.amount }
    val expense = rows.filter { !it.t.isIncome }.sumOf { it.t.amount }
    val savedPct = if (income > 0) "${(income - expense) * 100 / income}%" else "—"
    val slices = remember(rows, incomeView) {
        val grouped = rows.filter { it.t.isIncome == incomeView }
            .groupBy { it.t.categoryId }
            .map { (id, l) -> Slice(id, l.first().catName ?: "Other", l.first().catColor ?: 0xFF6B7570, l.sumOf { it.t.amount }) }
            .sortedByDescending { it.amount }
        if (grouped.size <= 5) grouped else grouped.take(4) + Slice(-1, "Everything else", 0xFF8A9690, grouped.drop(4).sumOf { it.amount })
    }
    val total = slices.sumOf { it.amount }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Reports", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Segmented(listOf("Week", "Month", "Year"), period.ordinal, { period = Period.entries[it]; offset = 0 })
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period") }
            Text(span.label, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            IconButton(onClick = { offset++ }, enabled = offset < 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Income", money(income, cur), ex.income, Modifier.weight(1f))
            StatTile("Spent", money(expense, cur), ex.expense, Modifier.weight(1f))
            StatTile("Saved", savedPct, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }

        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (incomeView) "Income by category" else "Spending by category", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    TextButton(onClick = { incomeView = !incomeView }) { Text(if (incomeView) "Show spending" else "Show income") }
                }
                if (total == 0L) {
                    Text(
                        "No ${if (incomeView) "income" else "spending"} recorded in this period.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Donut(slices, total, cur, Modifier.size(132.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slices.forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(s.color)))
                                    Text(s.name, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${s.amount * 100 / total}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    slices.forEach { s ->
                        val open = openCat == s.id
                        val canOpen = s.id >= 0
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .then(if (canOpen) Modifier.clickable { openCat = if (open) null else s.id } else Modifier)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(money(s.amount, cur), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            if (canOpen) Icon(
                                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (open) "Hide subcategories" else "Show subcategories",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (open) {
                            rows.filter { it.t.categoryId == s.id && it.t.isIncome == incomeView }
                                .groupBy { it.subName ?: "No subcategory" }
                                .map { (n, l) -> n to l.sumOf { it.t.amount } }
                                .sortedByDescending { it.second }
                                .forEach { (n, amt) ->
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 2.dp, bottom = 2.dp)) {
                                        Text(n, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            money(amt, cur) + "  ·  ${amt * 100 / s.amount}%",
                                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }

        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Income vs expenses", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    LegendDot(ex.income, "Income")
                    Spacer(Modifier.width(10.dp))
                    LegendDot(ex.expense, "Expenses")
                }
                val data = months.map { m ->
                    val r = monthRange(m)
                    val inM = trend.filter { it.t.date >= r.first && it.t.date < r.second }
                    Triple(m, inM.filter { it.t.isIncome }.sumOf { it.t.amount }, inM.filter { !it.t.isIncome }.sumOf { it.t.amount })
                }
                val maxV = max(1L, data.maxOf { max(it.second, it.third) })
                val incC = ex.income
                val expC = ex.expense
                val grid = MaterialTheme.colorScheme.outline
                Canvas(
                    Modifier.fillMaxWidth().height(140.dp)
                        .semantics { contentDescription = "Bar chart of income and expenses for the last six months" },
                ) {
                    val n = data.size
                    val gw = size.width / n
                    val bw = min(gw * 0.28f, 18.dp.toPx())
                    val gap = 3.dp.toPx()
                    val radius = CornerRadius(4.dp.toPx())
                    drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    data.forEachIndexed { i, (_, inc, exp) ->
                        val cx = gw * (i + 0.5f)
                        val hi = size.height * inc / maxV
                        val he = size.height * exp / maxV
                        if (hi > 0f) drawRoundRect(incC, Offset(cx - gap - bw, size.height - hi), Size(bw, hi), radius)
                        if (he > 0f) drawRoundRect(expC, Offset(cx + gap, size.height - he), Size(bw, he), radius)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    data.forEach { (m, _, _) ->
                        Text(
                            m.format(DateTimeFormatter.ofPattern("MMM", Locale.US)),
                            Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp,
                            fontWeight = if (m == nowYm) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (m == nowYm) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Donut(slices: List<Slice>, total: Long, cur: String, modifier: Modifier) {
    val desc = "Donut chart: " + slices.joinToString { "${it.name} ${it.amount * 100 / total}%" }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = desc }) {
            val stroke = 20.dp.toPx()
            val d = size.minDimension - stroke
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            var start = -90f
            slices.forEach { s ->
                val sweep = 360f * s.amount / total
                drawArc(Color(s.color), start, sweep, useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(moneyShort(total, cur), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
