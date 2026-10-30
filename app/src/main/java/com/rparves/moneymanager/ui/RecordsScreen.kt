package com.rparves.moneymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rparves.moneymanager.app
import com.rparves.moneymanager.ui.theme.LocalExtra
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun RecordsScreen(onEdit: (Long) -> Unit) {
    val app = LocalContext.current.app()
    val dao = app.dao
    val cur = app.prefs.currency
    val ex = LocalExtra.current
    var ym by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    val flow = remember(ym, searching, q) {
        if (searching && q.isNotBlank()) dao.search("%" + q.trim() + "%")
        else monthRange(ym).let { dao.txnsBetween(it.first, it.second) }
    }
    val rows by flow.collectAsState(emptyList())
    val inc = rows.filter { it.t.isIncome }.sumOf { it.t.amount }
    val exp = rows.filter { !it.t.isIncome }.sumOf { it.t.amount }

    Column(Modifier.fillMaxSize()) {
        if (searching) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search notes, categories, accounts") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                )
                IconButton(onClick = { searching = false; q = "" }) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                Text(ym.label(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { ym = ym.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Income", money(inc, cur), ex.income, Modifier.weight(1f))
            StatTile("Expenses", money(exp, cur), ex.expense, Modifier.weight(1f))
            StatTile("Balance", money(inc - exp, cur), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (searching) "No matches" else "Nothing recorded in ${ym.label()} yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rows.groupBy { it.t.date.toLocalDate() }.forEach { (d, list) ->
                    item(key = d.toString()) {
                        Panel {
                            DayHeader(
                                d,
                                list.filter { it.t.isIncome }.sumOf { it.t.amount },
                                list.filter { !it.t.isIncome }.sumOf { it.t.amount },
                                cur,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            list.forEach { r -> TxnItem(r, cur) { onEdit(r.t.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(d: LocalDate, inc: Long, exp: Long, cur: String) {
    val ex = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(d.dayOfMonth.toString(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.weight(1f)) {
            Text(d.fmt("EEEE"), fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(d.fmt("MMM yyyy"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (inc > 0) Text("+" + money(inc, cur), color = ex.income, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        if (exp > 0) Text("−" + money(exp, cur), color = ex.expense, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
