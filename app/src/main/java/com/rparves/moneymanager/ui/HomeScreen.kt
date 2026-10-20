package com.rparves.moneymanager.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rparves.moneymanager.app
import com.rparves.moneymanager.ui.theme.LocalExtra
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(
    onAdd: (Boolean) -> Unit,
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
    val recent by remember { dao.recent(30) }.collectAsState(emptyList())
    val bills by remember { dao.bills() }.collectAsState(emptyList())

    val income = month.filter { it.t.isIncome }.sumOf { it.t.amount }
    val expense = month.filter { !it.t.isIncome }.sumOf { it.t.amount }
    val today = LocalDate.now()
    val nextBill = bills.filter { !it.isPaid(today) }.minByOrNull { it.nextDue(today) }
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(app.prefs.name.ifBlank { "Welcome" }, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedIconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = ex.heroBg) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Total balance", color = ex.heroSub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            ym.label(), color = ex.heroOn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(ex.heroTile).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    Text(money(initial + net, cur), color = ex.heroOn, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HeroTile("Income", money(income, cur), Icons.Filled.ArrowDownward, ex.heroIncome, Modifier.weight(1f))
                        HeroTile("Expenses", money(expense, cur), Icons.Filled.ArrowUpward, ex.heroExpense, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickButton("Add expense", ex.expense, Modifier.weight(1f)) { onAdd(false) }
                QuickButton("Add income", ex.income, Modifier.weight(1f)) { onAdd(true) }
            }
        }
        if (nextBill != null) item {
            val due = nextBill.nextDue(today)
            val days = ChronoUnit.DAYS.between(today, due)
            Surface(onClick = onPlanner, shape = RoundedCornerShape(18.dp), color = ex.warnBg) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = ex.warnOn) }
                    Column(Modifier.weight(1f)) {
                        Text(nextBill.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${dueLabel(days)} · ${due.fmt("MMM d")}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ex.warnSub)
                    }
                    Text(money(nextBill.amount, cur), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }
        item { SectionHeader("Recent transactions", if (recent.isNotEmpty()) "See all" else null, onSeeAll) }
        if (recent.isEmpty()) {
            item { EmptyNote("No transactions yet. Tap + to record your first expense.") }
        } else {
            item {
                Panel {
                    recent.groupBy { it.t.date.toLocalDate() }.forEach { (d, list) ->
                        Text(
                            relativeDay(d), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                        list.forEach { r -> TxnItem(r, cur) { onEdit(r.t.id) } }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroTile(label: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier) {
    val ex = LocalExtra.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(ex.heroTile).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(value, color = ex.heroOn, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontWeight = FontWeight.Bold)
    }
}
