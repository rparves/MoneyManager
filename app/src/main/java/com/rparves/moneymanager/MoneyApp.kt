package com.rparves.moneymanager

import android.app.Application
import android.content.Context
import com.rparves.moneymanager.data.AppDatabase
import com.rparves.moneymanager.data.MoneyDao
import com.rparves.moneymanager.data.Prefs
import com.rparves.moneymanager.data.Seed
import com.rparves.moneymanager.reminder.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoneyApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var prefs: Prefs
        private set
    val dao: MoneyDao get() = db.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        prefs = Prefs(this)
        Reminder.createChannel(this)
        Reminder.schedule(this)
        scope.launch { Seed.ensure(dao) }
    }
}

fun Context.app(): MoneyApp = applicationContext as MoneyApp
