package com.rparves.moneymanager.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.rparves.moneymanager.ai.Llm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rparves.moneymanager.BuildConfig
import com.rparves.moneymanager.app
import com.rparves.moneymanager.data.Account
import com.rparves.moneymanager.data.AccountBalance
import com.rparves.moneymanager.data.AccountTypes
import com.rparves.moneymanager.data.Backup
import com.rparves.moneymanager.reminder.Reminder
import com.rparves.moneymanager.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val currencies = listOf("৳", "$", "€", "£", "₹")

/** null = adding a new account */
private data class AccountEdit(val existing: AccountBalance?)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val prefs = app.prefs
    val dao = app.dao
    val ex = LocalExtra.current
    val scope = rememberCoroutineScope()
    val balances by remember { dao.accountBalances() }.collectAsState(emptyList())
    var refresh by remember { mutableIntStateOf(0) }
    var showTime by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf<AccountEdit?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    fun runTask(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val msg = runCatching { block() }.getOrElse { "Something went wrong: ${it.message ?: it.javaClass.simpleName}" }
            busy = false
            toast(msg)
        }
    }

    LifecycleResumeEffect(Unit) {
        refresh++
        Reminder.schedule(ctx)
        onPauseOrDispose { }
    }
    val notifGranted = remember(refresh) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val exactOk = remember(refresh) { Reminder.canExact(ctx) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val today = LocalDate.now().toString()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runTask { "Exported ${Backup.exportCsv(ctx, uri)} transactions" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runTask {
            val r = Backup.importCsv(ctx, uri)
            buildString {
                append("Imported ${r.added} transactions")
                if (r.duplicates > 0) append(", ${r.duplicates} already existed")
                if (r.skipped > 0) append(", ${r.skipped} rows skipped")
            }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runTask { Backup.writeBackup(ctx, uri); "Backup saved" }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            prefs.backupTree = uri.toString()
            prefs.autoBackup = true
            Reminder.schedule(ctx)
            runTask { if (Backup.autoBackup(ctx)) "Daily backup is on. First backup saved." else "Couldn't write to that folder" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopRow("Settings", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Profile", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    OutlinedTextField(prefs.name, { prefs.name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Currency", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        currencies.forEach { c ->
                            FilterChip(selected = prefs.currency == c, onClick = { prefs.currency = c }, label = { Text(c, fontWeight = FontWeight.Bold) })
                        }
                    }
                }
            }

            // Categories & accounts
            Panel {
                NavRow(Icons.Outlined.Category, "Categories & subcategories", "Add, rename, recolor or delete", onCategories)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Accounts", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editAccount = AccountEdit(null) }) { Text("Add account", fontWeight = FontWeight.Bold) }
                }
                balances.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editAccount = AccountEdit(b) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(accountIcon(b.type), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontWeight = FontWeight.SemiBold)
                            Text(AccountTypes.label(b.type), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (b.type == "card") {
                            Text(
                                when {
                                    b.balance < 0 -> "Owed " + money(-b.balance, prefs.currency)
                                    b.balance == 0L -> "Nothing owed"
                                    else -> money(b.balance, prefs.currency) + " credit"
                                },
                                fontWeight = FontWeight.ExtraBold, color = if (b.balance < 0) ex.expense else MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                money(b.balance, prefs.currency), fontWeight = FontWeight.ExtraBold,
                                color = if (b.balance < 0) ex.expense else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(6.dp))
            }

            // Reminder
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Daily reminder", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text("A nudge to record the day's expenses", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = prefs.reminderOn, onCheckedChange = {
                            prefs.reminderOn = it
                            Reminder.schedule(ctx)
                            if (it && !notifGranted) askNotifications()
                        })
                    }
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Every day at " + timeLabel(prefs.reminderHour, prefs.reminderMinute), fontWeight = FontWeight.Bold)
                    }
                    if (prefs.reminderOn && !notifGranted) {
                        Notice("Notifications are turned off for this app, so the reminder can't appear.", "Allow") { askNotifications() }
                    }
                    if ((prefs.reminderOn || prefs.autoBackup) && !exactOk && Build.VERSION.SDK_INT >= 31) {
                        Notice("Allow alarms so the reminder arrives exactly on time. Without it, Android may deliver it a few minutes late.", "Allow") {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                        }
                    }
                    if (prefs.reminderOn) TextButton(onClick = { scope.launch { Reminder.notifyNow(ctx) } }) { Text("Send a test reminder") }
                }
            }

            // Smart entry
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Scan & voice (AI)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(
                        "Receipt scanning and voice entry work on this phone without a key. Adding an AI key makes them far more accurate.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("none" to "On-device only", "gemini" to "Google Gemini", "claude" to "Anthropic Claude").forEach { (key, label) ->
                            FilterChip(
                                selected = prefs.aiProvider == key,
                                onClick = { if (prefs.aiProvider != key) { prefs.aiProvider = key; prefs.aiModel = "" } },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (prefs.aiProvider != "none") {
                        var showKey by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = prefs.aiKey, onValueChange = { prefs.aiKey = it.trim() },
                            label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showKey) "Hide key" else "Show key",
                                    )
                                }
                            },
                        )
                        OutlinedTextField(
                            value = prefs.aiModel, onValueChange = { prefs.aiModel = it.trim() },
                            label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(Llm.defaultModel(prefs.aiProvider)) },
                            supportingText = { Text("Leave blank to use " + Llm.defaultModel(prefs.aiProvider)) },
                        )
                        Text(
                            if (prefs.aiProvider == "gemini") "Get a free key at aistudio.google.com → Get API key. On the free tier, Google may use the data to improve its models."
                            else "Get a key at console.anthropic.com → API keys. Usage is billed to your Anthropic account.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Receipt images and what you say are sent to this provider to fill in the entry. The key stays on this phone and isn't included in backups.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            enabled = prefs.aiKey.isNotBlank(),
                            onClick = { runTask { "Connected: " + Llm.ask(ctx, "Reply with the single word OK.", null).trim().take(40) } },
                        ) { Text("Test connection") }
                    }
                    Text("Voice language", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("" to "Phone default", "en-US" to "English", "bn-BD" to "বাংলা").forEach { (key, label) ->
                            FilterChip(selected = prefs.voiceLang == key, onClick = { prefs.voiceLang = key }, label = { Text(label) })
                        }
                    }
                }
            }

            // Data
            Panel {
                Text("Export & import", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
                NavRow(Icons.Outlined.FileUpload, "Export to CSV", "Opens in Excel or Google Sheets") {
                    exportLauncher.launch("MoneyManager-transactions-$today.csv")
                }
                NavRow(Icons.Outlined.FileDownload, "Import from CSV", "Columns: Date, Type, Account, Category, Subcategory, Amount, Note") {
                    importLauncher.launch(arrayOf("*/*"))
                }
            }

            Panel {
                Text("Backup", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
                NavRow(Icons.Outlined.Backup, "Back up to a file", "Everything: transactions, categories, accounts, goals, bills, settings") {
                    backupLauncher.launch("MoneyManager-backup-$today.json")
                }
                NavRow(Icons.Outlined.Restore, "Restore from a backup", "Replaces the data on this phone") {
                    restoreLauncher.launch(arrayOf("*/*"))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily automatic backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            if (prefs.backupTree.isBlank()) "Saves a copy to a folder you choose, every night"
                            else "Folder: " + Backup.folderLabel(prefs.backupTree),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = prefs.autoBackup, onCheckedChange = { on ->
                        if (on && prefs.backupTree.isBlank()) folderLauncher.launch(null)
                        else { prefs.autoBackup = on; Reminder.schedule(ctx) }
                    })
                }
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { folderLauncher.launch(null) }) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (prefs.backupTree.isBlank()) "Choose folder" else "Change folder")
                    }
                    if (prefs.autoBackup) TextButton(onClick = { runTask { if (Backup.autoBackup(ctx)) "Backup saved" else "Couldn't write to the backup folder" } }) {
                        Text("Back up now")
                    }
                }
                Text(
                    if (prefs.lastBackup == 0L) "No backup yet" else "Last backup: " + Instant.ofEpochMilli(prefs.lastBackup)
                        .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US)),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }

            Text(
                "Money Manager ${BuildConfig.VERSION_NAME} · your data stays on this device",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
            )
        }
    }

    if (showTime) {
        val st = rememberTimePickerState(initialHour = prefs.reminderHour, initialMinute = prefs.reminderMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.reminderHour = st.hour
                    prefs.reminderMinute = st.minute
                    Reminder.schedule(ctx)
                    showTime = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything currently in the app will be replaced by the backup. Consider backing up first.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    runTask { "Restored ${Backup.restore(ctx, uri)} transactions" }
                }) { Text("Restore", color = ex.expense, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }

    editAccount?.let { e -> AccountDialog(e.existing, balances.size, prefs.currency) { editAccount = null } }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AccountDialog(existing: AccountBalance?, accountCount: Int, cur: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dao = ctx.app().dao
    val scope = rememberCoroutineScope()
    val ex = LocalExtra.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "cash") }
    val startOpening = existing?.initial ?: 0L
    var opening by remember {
        mutableStateOf(if (startOpening == 0L) "" else plainAmount(if ((existing?.type ?: "cash") == "card") -startOpening else startOpening))
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val isCard = type == "card"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New account" else if (confirmDelete) "Delete ${existing.name}?" else "Edit account") },
        text = {
            if (confirmDelete) {
                Text("This account will be removed. Accounts with transactions can't be deleted.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Account name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Type", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountTypes.all.forEach { (key, label) ->
                            FilterChip(
                                selected = type == key, onClick = { type = key },
                                label = { Text(label) },
                                leadingIcon = { Icon(accountIcon(key), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                    AmountField(opening, { opening = it }, if (isCard) "Amount owed right now (optional)" else "Opening balance (optional)", cur)
                    if (isCard) {
                        Text(
                            "Expenses paid with this card increase what you owe.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (confirmDelete && existing != null) {
                TextButton(onClick = {
                    scope.launch {
                        val n = dao.txnCountForAccount(existing.id)
                        if (n > 0) {
                            Toast.makeText(ctx, "${existing.name} has $n transactions, so it can't be deleted", Toast.LENGTH_LONG).show()
                        } else {
                            dao.deleteAccount(Account(existing.id, existing.name, existing.initial, existing.type))
                        }
                    }
                    onDismiss()
                }) { Text("Delete", color = ex.expense, fontWeight = FontWeight.Bold) }
            } else {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val entered = parseMinor(opening) ?: 0L
                    val initial = if (isCard) -entered else entered
                    scope.launch {
                        if (existing == null) dao.insertAccount(Account(name = name.trim(), initial = initial, type = type))
                        else dao.updateAccount(Account(existing.id, name.trim(), initial, type))
                    }
                    onDismiss()
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            Row {
                if (existing != null && !confirmDelete && accountCount > 1) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = ex.expense) }
                }
                TextButton(onClick = { if (confirmDelete) confirmDelete = false else onDismiss() }) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun Notice(message: String, action: String, onAction: () -> Unit) {
    val ex = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ex.warnBg).padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f), fontSize = 12.sp, color = ex.warnSub)
        TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.Bold, color = ex.warnOn) }
    }
}
