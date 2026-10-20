package com.rparves.moneymanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rparves.moneymanager.app

sealed interface Route {
    data object Home : Route
    data object Records : Route
    data object Reports : Route
    data object Planner : Route
    data object Settings : Route
    data class Edit(val id: Long? = null, val income: Boolean = false) : Route
}

private val tabs = setOf(Route.Home, Route.Records, Route.Reports, Route.Planner)

@Composable
fun AppRoot(openAdd: MutableState<Boolean>) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val current = stack.last()

    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(r: Route) { stack.clear(); stack.add(Route.Home); if (r != Route.Home) stack.add(r) }

    LaunchedEffect(openAdd.value) {
        if (openAdd.value) { openAdd.value = false; push(Route.Edit()) }
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted && app.prefs.reminderOn) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (current in tabs) BottomBar(current, onTab = { tab(it) }, onAdd = { push(Route.Edit()) })
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            key(current) {
                when (val r = current) {
                    Route.Home -> HomeScreen(
                        onAdd = { push(Route.Edit(income = it)) },
                        onEdit = { push(Route.Edit(id = it)) },
                        onSeeAll = { tab(Route.Records) },
                        onSettings = { push(Route.Settings) },
                        onPlanner = { tab(Route.Planner) },
                    )
                    Route.Records -> RecordsScreen(onEdit = { push(Route.Edit(id = it)) })
                    Route.Reports -> ReportsScreen()
                    Route.Planner -> PlannerScreen()
                    Route.Settings -> SettingsScreen(onBack = { pop() })
                    is Route.Edit -> AddEditScreen(r.id, r.income, onDone = { pop() })
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Route, onTab: (Route) -> Unit, onAdd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavItem(Icons.Outlined.Home, "Home", current == Route.Home) { onTab(Route.Home) }
            NavItem(Icons.AutoMirrored.Outlined.ReceiptLong, "Records", current == Route.Records) { onTab(Route.Records) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FloatingActionButton(
                    onClick = onAdd,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp),
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Filled.Add, contentDescription = "Add transaction", modifier = Modifier.size(28.dp)) }
            }
            NavItem(Icons.Outlined.BarChart, "Reports", current == Route.Reports) { onTab(Route.Reports) }
            NavItem(Icons.Outlined.Savings, "Planner", current == Route.Planner) { onTab(Route.Planner) }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = c)
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, color = c)
    }
}
