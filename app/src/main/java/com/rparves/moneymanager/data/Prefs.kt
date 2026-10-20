package com.rparves.moneymanager.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** SharedPreferences backed by Compose state so the UI updates instantly. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _currency = mutableStateOf(sp.getString("currency", "৳") ?: "৳")
    var currency: String
        get() = _currency.value
        set(v) { _currency.value = v; sp.edit().putString("currency", v).apply() }

    private val _name = mutableStateOf(sp.getString("name", "") ?: "")
    var name: String
        get() = _name.value
        set(v) { _name.value = v; sp.edit().putString("name", v).apply() }

    private val _reminderOn = mutableStateOf(sp.getBoolean("reminder_on", true))
    var reminderOn: Boolean
        get() = _reminderOn.value
        set(v) { _reminderOn.value = v; sp.edit().putBoolean("reminder_on", v).apply() }

    private val _hour = mutableIntStateOf(sp.getInt("reminder_hour", 23))
    var reminderHour: Int
        get() = _hour.intValue
        set(v) { _hour.intValue = v; sp.edit().putInt("reminder_hour", v).apply() }

    private val _minute = mutableIntStateOf(sp.getInt("reminder_minute", 0))
    var reminderMinute: Int
        get() = _minute.intValue
        set(v) { _minute.intValue = v; sp.edit().putInt("reminder_minute", v).apply() }
}
