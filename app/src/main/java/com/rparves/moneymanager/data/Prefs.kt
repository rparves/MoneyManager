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

    private val _autoBackup = mutableStateOf(sp.getBoolean("auto_backup", false))
    var autoBackup: Boolean
        get() = _autoBackup.value
        set(v) { _autoBackup.value = v; sp.edit().putBoolean("auto_backup", v).apply() }

    private val _backupTree = mutableStateOf(sp.getString("backup_tree", "") ?: "")
    var backupTree: String
        get() = _backupTree.value
        set(v) { _backupTree.value = v; sp.edit().putString("backup_tree", v).apply() }

    private val _lastBackup = mutableStateOf(sp.getLong("last_backup", 0L))
    var lastBackup: Long
        get() = _lastBackup.value
        set(v) { _lastBackup.value = v; sp.edit().putLong("last_backup", v).apply() }

    private val _minute = mutableIntStateOf(sp.getInt("reminder_minute", 0))
    var reminderMinute: Int
        get() = _minute.intValue
        set(v) { _minute.intValue = v; sp.edit().putInt("reminder_minute", v).apply() }

    // AI settings live in their own file, which is excluded from Android cloud backup.
    private val ai = ctx.getSharedPreferences("ai", Context.MODE_PRIVATE)

    private val _aiProvider = mutableStateOf(ai.getString("provider", "none") ?: "none")
    /** none, gemini or claude */
    var aiProvider: String
        get() = _aiProvider.value
        set(v) { _aiProvider.value = v; ai.edit().putString("provider", v).apply() }

    private val _aiKey = mutableStateOf(ai.getString("key", "") ?: "")
    var aiKey: String
        get() = _aiKey.value
        set(v) { _aiKey.value = v; ai.edit().putString("key", v).apply() }

    private val _aiModel = mutableStateOf(ai.getString("model", "") ?: "")
    /** blank = provider default */
    var aiModel: String
        get() = _aiModel.value
        set(v) { _aiModel.value = v; ai.edit().putString("model", v).apply() }

    private val _voiceLang = mutableStateOf(sp.getString("voice_lang", "") ?: "")
    /** blank = phone default, otherwise a BCP-47 tag like en-US or bn-BD */
    var voiceLang: String
        get() = _voiceLang.value
        set(v) { _voiceLang.value = v; sp.edit().putString("voice_lang", v).apply() }
}
