package com.rparves.moneymanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rparves.moneymanager.app
import com.rparves.moneymanager.data.Bill
import com.rparves.moneymanager.data.Goal
import com.rparves.moneymanager.data.Txn
import com.rparves.moneymanager.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun PlannerScreen() {
    val app = LocalContext.current.app()
    val dao = app.dao
    val cur = app.prefs.currency
    val ex = LocalExtra.current
    val scope = rememberCoroutineScope()
    val goals by remember { dao.goals() }.collectAsState(emptyList())
    val bills by remember { dao.bills() }.collectAsState(emptyList())
    val today = LocalDate.now()
    var addGoal by remember { mutableStateOf(false) }
    var goalFor by remember { mutableStateOf<Goal?>(null) }
    var addBill by remember { mutableStateOf(false) }
    var payBill by remember { mutableStateOf<Bill?>(null) }
    val sortedBills = bills.sortedBy { it.nextDue(today) }
    val dueTotal = bills.filter { !it.isPaid(today) }.sumOf { it.amount }
    val reminderAt = timeLabel(app.prefs.reminderHour, app.prefs.reminderMinute)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Planner", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        item { SectionHeader("Savings goals", "Add goal") { addGoal = true } }
        if (goals.isEmpty()) item { EmptyNote("Set a goal, like an emergency fund, and add to it as you save.") }
        items(goals, key = { "g" + it.id }) { g -> GoalCard(g, cur) { goalFor = g } }
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("Bills & reminders", "Add bill") { addBill = true }
        }
        if (bills.isEmpty()) {
            item { EmptyNote("Add recurring bills like rent or internet. Your $reminderAt reminder mentions them the day before they're due.") }
        } else {
            item {
                Text("${money(dueTotal, cur)} left to pay this month", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Panel {
                    sortedBills.forEach { b ->
                        BillRow(b, today, cur) {
                            if (b.isPaid(today)) scope.launch { dao.upsertBill(b.copy(paidMonth = "")) } else payBill = b
                        }
                    }
                }
            }
        }
    }

    if (addGoal) {
        var name by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var saved by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addGoal = false },
            title = { Text("New savings goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    AmountField(target, { target = it }, "Target amount", cur)
                    AmountField(saved, { saved = it }, "Already saved (optional)", cur)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && parseMinor(target) != null,
                    onClick = {
                        val g = Goal(name = name.trim(), target = parseMinor(target) ?: 0, saved = parseMinor(saved) ?: 0)
                        scope.launch { dao.upsertGoal(g) }
                        addGoal = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { addGoal = false }) { Text("Cancel") } },
        )
    }

    goalFor?.let { g ->
        var add by remember(g.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { goalFor = null },
            title = { Text(g.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${money(g.saved, cur)} saved of ${money(g.target, cur)}")
                    AmountField(add, { add = it }, "Add to savings", cur)
                }
            },
            confirmButton = {
                TextButton(enabled = parseMinor(add) != null, onClick = {
                    val a = parseMinor(add) ?: 0
                    scope.launch { dao.upsertGoal(g.copy(saved = g.saved + a)) }
                    goalFor = null
                }) { Text("Add") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { scope.launch { dao.deleteGoal(g) }; goalFor = null }) { Text("Delete", color = ex.expense) }
                    TextButton(onClick = { goalFor = null }) { Text("Close") }
                }
            },
        )
    }

    if (addBill) {
        var name by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var day by remember { mutableStateOf("") }
        val dayNum = day.toIntOrNull()?.takeIf { it in 1..31 }
        AlertDialog(
            onDismissRequest = { addBill = false },
            title = { Text("New recurring bill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Bill name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    AmountField(amount, { amount = it }, "Amount", cur)
                    OutlinedTextField(
                        day, { v -> if (v.length <= 2 && v.all { it.isDigit() }) day = v },
                        label = { Text("Due day of month (1–31)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && parseMinor(amount) != null && dayNum != null,
                    onClick = {
                        val b = Bill(name = name.trim(), amount = parseMinor(amount) ?: 0, dueDay = dayNum ?: 1)
                        scope.launch { dao.upsertBill(b) }
                        addBill = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { addBill = false }) { Text("Cancel") } },
        )
    }

    payBill?.let { b ->
        var record by remember(b.id) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { payBill = null },
            title = { Text("Pay ${b.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mark ${money(b.amount, cur)} as paid for ${YearMonth.now().label()}?")
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = record, role = Role.Checkbox, onValueChange = { record = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = record, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Also record it as an expense")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rec = record
                    scope.launch {
                        dao.upsertBill(b.copy(paidMonth = YearMonth.now().toString()))
                        if (rec) {
                            val cat = dao.categoryByName("Bills", false) ?: dao.firstCategory(false)
                            val acc = dao.firstAccount()
                            if (cat != null && acc != null) {
                                dao.upsertTxn(Txn(isIncome = false, amount = b.amount, categoryId = cat.id, accountId = acc.id, note = b.name, date = System.currentTimeMillis()))
                            }
                        }
                    }
                    payBill = null
                }) { Text("Mark paid") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { scope.launch { dao.deleteBill(b) }; payBill = null }) { Text("Delete bill", color = ex.expense) }
                    TextButton(onClick = { payBill = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun GoalCard(g: Goal, cur: String, onClick: () -> Unit) {
    val ex = LocalExtra.current
    val pct = if (g.target > 0) (g.saved * 100 / g.target).toInt().coerceIn(0, 100) else 0
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${money(g.saved, cur)} of ${money(g.target, cur)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (pct >= 100) "Reached" else "$pct%", fontWeight = FontWeight.ExtraBold, color = ex.income)
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(pct / 100f, 0f..1f) },
            ) {
                Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(ex.income))
            }
        }
    }
}

@Composable
private fun BillRow(b: Bill, today: LocalDate, cur: String, onToggle: () -> Unit) {
    val ex = LocalExtra.current
    val paid = b.isPaid(today)
    val due = b.nextDue(today)
    val days = ChronoUnit.DAYS.between(today, due)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ex.warnBg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(due.fmt("MMM").uppercase(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ex.warnSub, lineHeight = 11.sp)
            Text(due.dayOfMonth.toString(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ex.warnSub, lineHeight = 17.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(b.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (paid) "Paid · next due ${due.fmt("MMM d")}" else dueLabel(days),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = when {
                    paid -> ex.income
                    days <= 1 -> ex.expense
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(money(b.amount, cur), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            if (paid) {
                FilledTonalButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) { Text("Paid", fontSize = 12.sp) }
            } else {
                OutlinedButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) { Text("Mark paid", fontSize = 12.sp) }
            }
        }
    }
}
