package com.rparves.moneymanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rparves.moneymanager.app
import com.rparves.moneymanager.data.AccountBalance
import com.rparves.moneymanager.ui.theme.LocalExtra
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    onAdd: (Boolean) -> Unit,
    onSmart: (String) -> Unit,
    onEdit: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onSettings: () -> Unit,
    onPlanner: () -> Unit,
) {
    val app = LocalContext.current.app()
    val dao = app.dao
    val cur = app.prefs.currency
    val ex = LocalExtra.current
    val ym = remember { YearMonth.now() }
    val initial by remember { dao.initialSum() }.collectAsState(0L)
    val net by remember { dao.net() }.collectAsState(0L)
    val month by remember { val r = monthRange(ym); dao.txnsBetween(r.first, r.second) }.collectAsState(emptyList())
    val recent by remember { dao.recent(25) }.collectAsState(emptyList())
    val bills by remember { dao.bills() }.collectAsState(emptyList())
    val accounts by remember { dao.accountBalances() }.collectAsState(emptyList())

    val income = month.filter { it.t.isIncome }.sumOf { it.t.amount }
    val expense = month.filter { !it.t.isIncome }.sumOf { it.t.amount }
    val today = LocalDate.now()
    val monthFrac = today.dayOfMonth.toFloat() / ym.lengthOfMonth()
    val spentFrac = if (income > 0) (expense.toFloat() / income) else if (expense > 0) 1f else 0f
    val nextBill = bills.filter { !it.isPaid(today) }.minByOrNull { it.nextDue(today) }
    val balance = initial + net
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ym.label(), fontSize = 13.sp, color = ex.muted, letterSpacing = 0.3.sp)
                    Text(
                        if (app.prefs.name.isBlank()) greeting else "$greeting, ${app.prefs.name}",
                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                ) { Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = ex.expense) }
            }
        }

        // Ring hero
        item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                SpendRing(spentFrac, monthFrac, income > 0, Modifier.size(152.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Balance", fontSize = 12.sp, color = ex.muted)
                        val whole = money(balance, cur).substringBeforeLast('.')
                        val dec = money(balance, cur).substringAfterLast('.', "00")
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(whole, fontSize = 27.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp, maxLines = 1)
                            Text(".$dec", fontSize = 16.sp, fontWeight = FontWeight.Light, color = ex.muted, modifier = Modifier.padding(bottom = 3.dp))
                        }
                    }
                    Legend(ex.expense, "Spent", short(expense, cur))
                    Legend(ex.income, "Income", short(income, cur))
                    Text("${(monthFrac * 100).toInt()}% of the month gone", fontSize = 11.sp, color = ex.muted)
                }
            }
        }

        // Account cards
        if (accounts.isNotEmpty()) item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(accounts, key = { it.id }) { a -> AccountCard(a, cur, onSettings) }
            }
        }

        // Actions
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Scan", Icons.Outlined.DocumentScanner, Modifier.weight(1f)) { onSmart("scan") }
                ActionTile("Speak", Icons.Outlined.Mic, Modifier.weight(1f)) { onSmart("voice") }
                Surface(
                    onClick = { onAdd(false) },
                    shape = RoundedCornerShape(16.dp), color = ex.expense,
                    modifier = Modifier.size(54.dp).semantics { contentDescription = "Add transaction" },
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, contentDescription = null, tint = ex.onAccent, modifier = Modifier.size(28.dp)) }
                }
            }
        }

        if (nextBill != null) item {
            val due = nextBill.nextDue(today)
            val days = ChronoUnit.DAYS.between(today, due)
            Surface(
                onClick = onPlanner, shape = RoundedCornerShape(16.dp), color = ex.warnBg,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(ex.warnOn))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nextBill.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${dueLabel(days)} · ${due.fmt("MMM d")}", fontSize = 12.sp, color = ex.warnSub)
                    }
                    Text(money(nextBill.amount, cur), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (recent.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("See all", color = ex.expense) }
            }
        }
        if (recent.isEmpty()) {
            item {
                Text(
                    "Nothing recorded yet. Tap + to add your first expense, or scan a receipt.",
                    color = ex.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        } else {
            recent.groupBy { it.t.date.toLocalDate() }.forEach { (d, list) ->
                item(key = "d$d") {
                    Column {
                        Text(relativeDay(d), fontSize = 12.sp, color = ex.muted, modifier = Modifier.padding(start = 20.dp, bottom = 2.dp))
                        list.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp, end = 20.dp), color = ex.border)
                            TxnItem(r, cur) { onEdit(r.t.id) }
                        }
                    }
                }
            }
        }
    }
}

private fun short(minor: Long, cur: String): String {
    val v = abs(minor) / 100.0
    return cur + when {
        v >= 100_000 -> String.format(Locale.US, "%.1fL", v / 100_000)
        v >= 1_000 -> String.format(Locale.US, "%.1fk", v / 1_000)
        else -> String.format(Locale.US, "%.0f", v)
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = LocalExtra.current.muted)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SpendRing(spent: Float, month: Float, hasIncome: Boolean, modifier: Modifier) {
    val ex = LocalExtra.current
    val pct = (spent * 100).toInt()
    Box(
        modifier.semantics {
            contentDescription = if (hasIncome) "$pct percent of income spent, ${(month * 100).toInt()} percent of the month gone" else "No income recorded this month"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = 12.dp.toPx()
            val inner = 8.dp.toPx()
            fun ring(inset: Float, width: Float, frac: Float, color: Color) {
                val d = size.minDimension - inset * 2
                val tl = androidx.compose.ui.geometry.Offset((size.width - d) / 2, (size.height - d) / 2)
                val sz = androidx.compose.ui.geometry.Size(d, d)
                drawArc(ex.ringTrack, 0f, 360f, false, tl, sz, style = Stroke(width))
                if (frac > 0f) drawArc(color, -90f, 360f * frac.coerceIn(0.01f, 1f), false, tl, sz, style = Stroke(width, cap = StrokeCap.Round))
            }
            ring(outer / 2, outer, spent, if (spent > month && hasIncome) ex.expense else ex.expense)
            ring(outer + 10.dp.toPx() + inner / 2, inner, month, ex.income)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (hasIncome) "$pct%" else "—", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(if (hasIncome) "spent" else "no income", fontSize = 11.sp, color = ex.muted)
        }
    }
}

private fun cardBrush(type: String): Brush = Brush.linearGradient(
    when (type) {
        "card" -> listOf(Color(0xFFF07A33), Color(0xFFB8361B))
        "bank" -> listOf(Color(0xFF7C6EF5), Color(0xFF3E2FC2))
        "wallet" -> listOf(Color(0xFFE2478A), Color(0xFF9C1D55))
        else -> listOf(Color(0xFF22A876), Color(0xFF0B6B47))
    }
)

@Composable
private fun AccountCard(a: AccountBalance, cur: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Color.Transparent, modifier = Modifier.width(196.dp).height(116.dp)) {
        Column(
            Modifier.fillMaxSize().background(cardBrush(a.type)).padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(accountIcon(a.type), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(a.name.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val text = if (a.type == "card") {
                if (a.balance < 0) "Owed " + money(-a.balance, cur) else money(a.balance, cur)
            } else money(a.balance, cur)
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            Text(
                com.rparves.moneymanager.data.AccountTypes.label(a.type),
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val ex = LocalExtra.current
    Surface(
        onClick = onClick, modifier = modifier.height(54.dp), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, ex.border),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ex.expense)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
