package com.rparves.moneymanager.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rparves.moneymanager.BuildConfig
import com.rparves.moneymanager.app
import com.rparves.moneymanager.data.Account
import com.rparves.moneymanager.reminder.Reminder
import com.rparves.moneymanager.ui.theme.LocalExtra
import kotlinx.coroutines.launch

private val currencies = listOf("৳", "$", "€", "£", "₹")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val prefs = app.prefs
    val dao = app.dao
    val ex = LocalExtra.current
    val scope = rememberCoroutineScope()
    val balances by remember { dao.accountBalances() }.collectAsState(emptyList())
    var refresh by remember { mutableIntStateOf(0) }
    var showTime by remember { mutableStateOf(false) }
    var addAccount by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize()) {
        TopRow("Settings", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                    if (prefs.reminderOn) {
                        OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Every day at " + timeLabel(prefs.reminderHour, prefs.reminderMinute), fontWeight = FontWeight.Bold)
                        }
                        if (!notifGranted) {
                            Notice("Notifications are turned off for this app, so the reminder can't appear.", "Allow") { askNotifications() }
                        }
                        if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                            Notice("Allow alarms so the reminder arrives exactly on time. Without it, Android may deliver it a few minutes late.", "Allow") {
                                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                            }
                        }
                        TextButton(onClick = { scope.launch { Reminder.notifyNow(ctx) } }) { Text("Send a test reminder") }
                    }
                }
            }

            Panel {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Accounts", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { addAccount = true }) { Text("Add account", fontWeight = FontWeight.Bold) }
                    }
                    balances.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(b.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(
                                money(b.balance, prefs.currency), fontWeight = FontWeight.ExtraBold,
                                color = if (b.balance < 0) ex.expense else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
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

    if (addAccount) {
        var name by remember { mutableStateOf("") }
        var opening by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addAccount = false },
            title = { Text("New account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Account name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    AmountField(opening, { opening = it }, "Opening balance (optional)", prefs.currency)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val a = Account(name = name.trim(), initial = parseMinor(opening) ?: 0)
                    scope.launch { dao.insertAccount(a) }
                    addAccount = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { addAccount = false }) { Text("Cancel") } },
        )
    }
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
