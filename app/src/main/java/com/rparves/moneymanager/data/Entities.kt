package com.rparves.moneymanager.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val initial: Long = 0,
    /** cash, bank, wallet or card */
    val type: String = "cash",
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isIncome: Boolean,
    val color: Long,
    /** null for a main category, otherwise the id of its parent */
    val parentId: Long? = null,
)

/** Amounts are stored in minor units (1/100 of the currency). */
@Entity(tableName = "transactions", indices = [Index("date"), Index("categoryId"), Index("accountId")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isIncome: Boolean,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String,
    val date: Long,
    val subId: Long? = null,
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    val saved: Long = 0,
)

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val dueDay: Int,
    val paidMonth: String = "",
) {
    fun isPaid(today: LocalDate): Boolean = paidMonth == YearMonth.from(today).toString()

    fun nextDue(today: LocalDate): LocalDate {
        val ym = YearMonth.from(today)
        val m = if (isPaid(today)) ym.plusMonths(1) else ym
        return m.atDay(min(dueDay, m.lengthOfMonth()))
    }
}

data class TxnRow(
    @Embedded val t: Txn,
    val catName: String?,
    val catColor: Long?,
    val subName: String?,
    val accName: String?,
)

data class AccountBalance(val id: Long, val name: String, val type: String, val initial: Long, val balance: Long)

object AccountTypes {
    val all = listOf("cash" to "Cash", "bank" to "Bank", "wallet" to "Mobile wallet", "card" to "Credit card")
    fun label(type: String) = all.firstOrNull { it.first == type }?.second ?: "Cash"
}
