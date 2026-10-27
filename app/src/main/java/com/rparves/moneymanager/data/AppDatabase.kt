package com.rparves.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Category::class, Txn::class, Goal::class, Bill::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "money.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** Adds account types, subcategories and a few starter subcategories. Keeps all existing data. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'cash'")
        db.execSQL("UPDATE accounts SET type = 'bank' WHERE name = 'Bank'")
        db.execSQL("UPDATE accounts SET type = 'wallet' WHERE name = 'Mobile wallet'")
        db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN subId INTEGER")
        Seed.starterSubs.forEach { (parent, subs) ->
            subs.forEach { sub ->
                db.execSQL(
                    "INSERT INTO categories (name, isIncome, color, parentId) " +
                        "SELECT ?, 0, color, id FROM categories WHERE name = ? AND isIncome = 0 AND parentId IS NULL",
                    arrayOf<Any>(sub, parent),
                )
            }
        }
    }
}

object Seed {
    val palette = listOf(
        0xFF0F6E56, 0xFF4F7A2E, 0xFFB5651D, 0xFFA8440B, 0xFF3D5A80, 0xFF5B4B9A,
        0xFF2F5D8C, 0xFFB23A48, 0xFF1E7A8C, 0xFF8A3A62, 0xFF7A5A10, 0xFF6B7570,
    )

    val starterSubs = listOf(
        "Food" to listOf("Breakfast", "Lunch", "Dinner"),
        "Transport" to listOf("Bus", "Taxi / ride share", "Fuel"),
        "Bills" to listOf("Electricity", "Internet", "Water", "Phone"),
    )

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
        val subs = starterSubs.toMap()
        expense.forEach { (name, color) ->
            val id = dao.insertCategory(Category(name = name, isIncome = false, color = color))
            subs[name]?.forEach { dao.insertCategory(Category(name = it, isIncome = false, color = color, parentId = id)) }
        }
        income.forEach { (name, color) -> dao.insertCategory(Category(name = name, isIncome = true, color = color)) }
        listOf("Cash" to "cash", "Bank" to "bank", "Mobile wallet" to "wallet", "Credit card" to "card")
            .forEach { (n, t) -> dao.insertAccount(Account(name = n, type = t)) }
    }
}
