package com.rparves.moneymanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rparves.moneymanager.app
import com.rparves.moneymanager.data.Bill
import com.rparves.moneymanager.data.Category
import com.rparves.moneymanager.data.Txn
import com.rparves.moneymanager.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(id: Long?, startIncome: Boolean, onDone: () -> Unit) {
    val app = LocalContext.current.app()
    val dao = app.dao
    val cur = app.prefs.currency
    val ex = LocalExtra.current
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<Txn?>(null) }
    var isIncome by remember { mutableStateOf(startIncome) }
    var amount by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<Long?>(null) }
    var accId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cats by remember { dao.categories() }.collectAsState(emptyList())
    val accounts by remember { dao.accounts() }.collectAsState(emptyList())
    val focus = remember { FocusRequester() }
    val accent = if (isIncome) ex.income else ex.expense

    LaunchedEffect(id) {
        if (id != null) {
            dao.txn(id)?.let { t ->
                original = t; isIncome = t.isIncome; amount = plainAmount(t.amount)
                catId = t.categoryId; accId = t.accountId; date = t.date.toLocalDate(); note = t.note
            }
        } else {
            runCatching { focus.requestFocus() }
        }
    }
    LaunchedEffect(accounts) {
        if (accId == null && accounts.isNotEmpty()) accId = accounts.first().id
    }

    fun save() {
        val a = parseMinor(amount) ?: run { error = "Enter an amount greater than zero"; return }
        val c = catId ?: run { error = "Pick a category"; return }
        val acc = accId ?: run { error = "Add an account in Settings first"; return }
        val o = original
        val millis = when {
            o != null && o.date.toLocalDate() == date -> o.date
            date == LocalDate.now() -> System.currentTimeMillis()
            else -> date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val catName = cats.firstOrNull { it.id == c }?.name ?: "Bill"
        val makeBill = monthly && !isIncome && o == null
        val txn = Txn(id = o?.id ?: 0, isIncome = isIncome, amount = a, categoryId = c, accountId = acc, note = note.trim(), date = millis)
        scope.launch {
            dao.upsertTxn(txn)
            if (makeBill) {
                dao.upsertBill(
                    Bill(name = note.trim().ifBlank { catName }, amount = a, dueDay = date.dayOfMonth, paidMonth = YearMonth.from(date).toString())
                )
            }
            onDone()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopRow(if (id == null) "New transaction" else "Edit transaction", onBack = onDone, backIcon = Icons.Filled.Close) {
            if (id != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Segmented(
                listOf("Expense", "Income"), if (isIncome) 1 else 0,
                { i ->
                    val inc = i == 1
                    if (inc != isIncome) { isIncome = inc; catId = null; monthly = false; error = null }
                },
                accent,
            )

            Panel {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Amount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cur, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = amount,
                            onValueChange = { v ->
                                if (v.length <= 12 && v.all { it.isDigit() || it == '.' || it == ',' } && v.count { it == '.' } <= 1) {
                                    amount = v; error = null
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.width(200.dp).focusRequester(focus).semantics { contentDescription = "Amount" },
                            decorationBox = { inner ->
                                Box {
                                    if (amount.isEmpty()) {
                                        Text("0", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }

            Text("Category", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            val shown = cats.filter { it.isIncome == isIncome }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.chunked(4).forEach { rowCats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCats.forEach { c ->
                            CategoryTile(c, c.id == catId, accent, Modifier.weight(1f)) { catId = c.id; error = null }
                        }
                        repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Text("Account", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a ->
                    FilterChip(selected = a.id == accId, onClick = { accId = a.id }, label = { Text(a.name, fontWeight = FontWeight.SemiBold) })
                }
            }

            OutlinedButton(
                onClick = { showDate = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Date: " + relativeDay(date), fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optional)") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            )

            if (!isIncome && id == null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                        .toggleable(value = monthly, role = Role.Checkbox, onValueChange = { monthly = it })
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = monthly, onCheckedChange = null)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Repeat monthly", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Adds it to Planner as a bill with reminders", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            error?.let { Text(it, color = ex.expense, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp).height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = ex.onAccent),
        ) {
            Text(if (isIncome) "Save income" else "Save expense", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = st) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val o = original
                    confirmDelete = false
                    if (o != null) scope.launch { dao.deleteTxn(o); onDone() }
                }) { Text("Delete", color = ex.expense) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CategoryTile(c: Category, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 76.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CategoryBadge(c.name, c.color, 34.dp)
        Text(c.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
