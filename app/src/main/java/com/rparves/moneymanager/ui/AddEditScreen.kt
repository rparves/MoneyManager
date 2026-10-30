package com.rparves.moneymanager.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import com.rparves.moneymanager.ai.Catalog
import com.rparves.moneymanager.ai.Draft
import com.rparves.moneymanager.ai.Llm
import com.rparves.moneymanager.ai.SmartEntry
import com.rparves.moneymanager.ai.SmartResult
import java.io.File
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
import androidx.compose.foundation.layout.size
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
fun AddEditScreen(id: Long?, startIncome: Boolean, onDone: () -> Unit, start: String? = null) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val dao = app.dao
    val cur = app.prefs.currency
    val ex = LocalExtra.current
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<Txn?>(null) }
    var isIncome by remember { mutableStateOf(startIncome) }
    var amount by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<Long?>(null) }
    var subId by remember { mutableStateOf<Long?>(null) }
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

    // ---------- Smart entry: receipt scan & voice ----------
    var busyMsg by remember { mutableStateOf<String?>(null) }
    var smart by remember { mutableStateOf<SmartResult?>(null) }
    var showScanChoice by remember { mutableStateOf(false) }
    val scanUri = remember {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "scan.jpg"))
    }

    fun applyDraft(d: Draft) {
        d.isIncome?.let { if (it != isIncome) { isIncome = it; monthly = false } }
        val inc = d.isIncome ?: isIncome
        d.amount?.let { amount = plainAmount(it) }
        d.date?.let { if (!it.isAfter(LocalDate.now())) date = it }
        val tops = cats.filter { it.parentId == null && it.isIncome == inc }
        val top = d.category?.let { n ->
            tops.firstOrNull { it.name.equals(n, true) }
                ?: tops.firstOrNull { it.name.contains(n, true) || n.contains(it.name, true) }
        }
        val sub = d.subcategory?.let { n ->
            cats.firstOrNull { it.parentId != null && it.isIncome == inc && (top == null || it.parentId == top.id) && it.name.equals(n, true) }
        }
        val finalTop = top ?: sub?.let { s -> tops.firstOrNull { it.id == s.parentId } }
        catId = finalTop?.id
        subId = if (finalTop != null && sub != null && sub.parentId == finalTop.id) sub.id else null
        d.account?.let { n ->
            (accounts.firstOrNull { it.name.equals(n, true) } ?: accounts.firstOrNull { it.name.contains(n, true) })?.let { accId = it.id }
        }
        d.note?.takeIf { it.isNotBlank() }?.let { note = it.take(80) }
        error = null
    }

    fun runSmart(label: String, block: suspend (Catalog) -> SmartResult) {
        if (busyMsg != null) return
        busyMsg = label
        scope.launch {
            try {
                val r = block(Catalog(cats, accounts))
                applyDraft(r.draft)
                smart = r
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read that"
            } finally {
                busyMsg = null
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) runSmart("Reading the receipt…") { SmartEntry.fromImage(ctx, scanUri, false, it) }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runSmart("Reading the receipt…") { SmartEntry.fromImage(ctx, uri, false, it) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runSmart("Reading the invoice…") { SmartEntry.fromImage(ctx, uri, true, it) }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) runSmart("Understanding what you said…") { SmartEntry.fromSpeech(ctx, heard, it) }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something like “Lunch 250 taka with bKash”")
        if (app.prefs.voiceLang.isNotBlank()) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, app.prefs.voiceLang)
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            error = "Speech recognition isn't available on this phone. Install or enable the Google app."
        }
    }

    LaunchedEffect(Unit) {
        when (start) {
            "voice" -> startVoice()
            "scan" -> showScanChoice = true
        }
    }

    LaunchedEffect(id) {
        if (id != null) {
            dao.txn(id)?.let { t ->
                original = t; isIncome = t.isIncome; amount = plainAmount(t.amount)
                catId = t.categoryId; subId = t.subId; accId = t.accountId; date = t.date.toLocalDate(); note = t.note
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
        val catName = cats.firstOrNull { it.id == subId }?.name ?: cats.firstOrNull { it.id == c }?.name ?: "Bill"
        val makeBill = monthly && !isIncome && o == null
        val txn = Txn(id = o?.id ?: 0, isIncome = isIncome, amount = a, categoryId = c, accountId = acc, note = note.trim(), date = millis, subId = subId)
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
        if (id == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showScanChoice = true }, enabled = busyMsg == null,
                    modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan receipt", fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = { startVoice() }, enabled = busyMsg == null,
                    modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Speak", fontWeight = FontWeight.Medium)
                }
            }
        }
        busyMsg?.let { msg ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(msg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        smart?.let { r ->
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (r.heard != null) Icons.Outlined.Mic else Icons.Outlined.AutoAwesome,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 2.dp).size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            (if (r.usedAi) "Filled in by AI" else "Filled in on this phone") +
                                (if (r.draft.lowConfidence) " · please double-check" else ""),
                            fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        r.heard?.let { Text("Heard: “$it”", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        r.warning?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Text("Check the details below, then save.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { smart = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Segmented(
                listOf("Expense", "Income"), if (isIncome) 1 else 0,
                { i ->
                    val inc = i == 1
                    if (inc != isIncome) { isIncome = inc; catId = null; subId = null; monthly = false; error = null }
                },
                accent,
            )

            Panel {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Amount", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cur, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = accent)
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = amount,
                            onValueChange = { v ->
                                if (v.length <= 12 && v.all { it.isDigit() || it == '.' || it == ',' } && v.count { it == '.' } <= 1) {
                                    amount = v; error = null
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.width(200.dp).focusRequester(focus).semantics { contentDescription = "Amount" },
                            decorationBox = { inner ->
                                Box {
                                    if (amount.isEmpty()) {
                                        Text("0", fontSize = 42.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }

            Text("Category", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            val shown = cats.filter { it.isIncome == isIncome && it.parentId == null }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.chunked(4).forEach { rowCats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCats.forEach { c ->
                            CategoryTile(c, c.id == catId, accent, Modifier.weight(1f)) {
                                if (catId != c.id) subId = null
                                catId = c.id; error = null
                            }
                        }
                        repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            val subs = cats.filter { catId != null && it.parentId == catId }
            if (subs.isNotEmpty()) {
                Text("Subcategory", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = subId == null, onClick = { subId = null }, label = { Text("None", fontWeight = FontWeight.SemiBold) })
                    subs.forEach { s ->
                        FilterChip(selected = subId == s.id, onClick = { subId = s.id }, label = { Text(s.name, fontWeight = FontWeight.SemiBold) })
                    }
                }
            }

            Text("Account", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a ->
                    FilterChip(
                        selected = a.id == accId, onClick = { accId = a.id },
                        label = { Text(a.name, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(accountIcon(a.type), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            OutlinedButton(
                onClick = { showDate = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Date: " + relativeDay(date), fontWeight = FontWeight.Medium)
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
                        Text("Repeat monthly", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Adds it to Planner as a bill with reminders", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            error?.let { Text(it, color = ex.expense, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = { save() },
            enabled = busyMsg == null,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp).height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = ex.onAccent),
        ) {
            Text(if (isIncome) "Save income" else "Save expense", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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

    if (showScanChoice) {
        AlertDialog(
            onDismissRequest = { showScanChoice = false },
            title = { Text("Scan a receipt or invoice") },
            text = {
                Column {
                    ScanOption(Icons.Outlined.PhotoCamera, "Take a photo") {
                        showScanChoice = false
                        try { cameraLauncher.launch(scanUri) } catch (_: ActivityNotFoundException) { error = "No camera app found" }
                    }
                    ScanOption(Icons.Outlined.Image, "Choose an image") {
                        showScanChoice = false
                        imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ScanOption(Icons.Outlined.PictureAsPdf, "Choose a PDF") {
                        showScanChoice = false
                        pdfLauncher.launch(arrayOf("application/pdf"))
                    }
                    if (!Llm.isConfigured(ctx)) {
                        Text(
                            "Tip: add an AI key in Settings for much more accurate reading.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showScanChoice = false }) { Text("Cancel") } },
        )
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
        Text(c.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScanOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
