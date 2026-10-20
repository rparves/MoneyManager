package com.rparves.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Account::class, Category::class, Txn::class, Goal::class, Bill::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "money.db").build()
    }
}

object Seed {
    suspend fun ensure(dao: MoneyDao) {
        if (dao.categoryCount() > 0) return
        val expense = listOf(
            "Food" to 0xFF0F6E56, "Groceries" to 0xFF4F7A2E, "Snacks" to 0xFFB5651D, "Transport" to 0xFFA8440B,
            "Rent" to 0xFF3D5A80, "Bills" to 0xFF5B4B9A, "Shopping" to 0xFF2F5D8C, "Health" to 0xFFB23A48,
            "Education" to 0xFF1E7A8C, "Leisure" to 0xFF8A3A62, "Family" to 0xFF7A5A10, "Other" to 0xFF6B7570,
        )
        val income = listOf(
            "Salary" to 0xFF0F6E56, "Freelance" to 0xFF2F5D8C, "Business" to 0xFF7A5A10,
            "Gift" to 0xFF8A3A62, "Refund" to 0xFF5B4B9A, "Other" to 0xFF6B7570,
        )
        dao.insertCategories(
            expense.map { Category(name = it.first, isIncome = false, color = it.second) } +
                income.map { Category(name = it.first, isIncome = true, color = it.second) }
        )
        listOf("Cash", "Bank", "Mobile wallet").forEach { dao.insertAccount(Account(name = it)) }
    }
}
