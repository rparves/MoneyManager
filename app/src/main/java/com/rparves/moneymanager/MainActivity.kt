package com.rparves.moneymanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.runtime.DisposableEffect
import com.rparves.moneymanager.ui.theme.isDarkMode
import androidx.compose.runtime.mutableStateOf
import com.rparves.moneymanager.ui.AppRoot
import com.rparves.moneymanager.ui.theme.MoneyTheme

const val EXTRA_ADD = "open_add"

class MainActivity : ComponentActivity() {
    private val openAdd = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openAdd.value = intent?.getBooleanExtra(EXTRA_ADD, false) == true
        val prefs = app().prefs
        setContent {
            val dark = isDarkMode(prefs.themeMode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            MoneyTheme(dark) { AppRoot(openAdd) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ADD, false)) openAdd.value = true
    }
}
