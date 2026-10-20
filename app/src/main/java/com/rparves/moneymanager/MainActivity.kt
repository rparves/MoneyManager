package com.rparves.moneymanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent { MoneyTheme { AppRoot(openAdd) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ADD, false)) openAdd.value = true
    }
}
