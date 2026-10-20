package com.rparves.moneymanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ROW = "SELECT transactions.*, c.name AS catName, c.color AS catColor, a.name AS accName " +
    "FROM transactions LEFT JOIN categories c ON c.id = transactions.categoryId " +
    "LEFT JOIN accounts a ON a.id = transactions.accountId"

@Dao
interface MoneyDao {
    // Transactions
    @Query("$ROW WHERE transactions.date >= :from AND transactions.date < :to ORDER BY transactions.date DESC, transactions.id DESC")
    fun txnsBetween(from: Long, to: Long): Flow<List<TxnRow>>

    @Query("$ROW ORDER BY transactions.date DESC, transactions.id DESC LIMIT :n")
    fun recent(n: Int): Flow<List<TxnRow>>

    @Query("$ROW WHERE transactions.note LIKE :q OR c.name LIKE :q OR a.name LIKE :q ORDER BY transactions.date DESC LIMIT 300")
    fun search(q: String): Flow<List<TxnRow>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun txn(id: Long): Txn?

    @Upsert suspend fun upsertTxn(t: Txn): Long
    @Delete suspend fun deleteTxn(t: Txn)

    @Query("SELECT COALESCE(SUM(CASE WHEN isIncome THEN amount ELSE -amount END), 0) FROM transactions")
    fun net(): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE date >= :from AND date < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE isIncome = 0 AND date >= :from AND date < :to")
    suspend fun expenseBetween(from: Long, to: Long): Long

    // Accounts
    @Query("SELECT * FROM accounts ORDER BY id")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY id LIMIT 1")
    suspend fun firstAccount(): Account?

    @Query("SELECT COALESCE(SUM(initial), 0) FROM accounts")
    fun initialSum(): Flow<Long>

    @Query(
        "SELECT a.id AS id, a.name AS name, a.initial + COALESCE((SELECT SUM(CASE WHEN t.isIncome THEN t.amount ELSE -t.amount END) " +
            "FROM transactions t WHERE t.accountId = a.id), 0) AS balance FROM accounts a ORDER BY a.id"
    )
    fun accountBalances(): Flow<List<AccountBalance>>

    @Insert suspend fun insertAccount(a: Account): Long

    // Categories
    @Query("SELECT * FROM categories ORDER BY id")
    fun categories(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Query("SELECT * FROM categories WHERE name = :name AND isIncome = :income LIMIT 1")
    suspend fun categoryByName(name: String, income: Boolean): Category?

    @Query("SELECT * FROM categories WHERE isIncome = :income ORDER BY id LIMIT 1")
    suspend fun firstCategory(income: Boolean): Category?

    @Insert suspend fun insertCategories(list: List<Category>)

    // Goals
    @Query("SELECT * FROM goals ORDER BY id")
    fun goals(): Flow<List<Goal>>

    @Upsert suspend fun upsertGoal(g: Goal)
    @Delete suspend fun deleteGoal(g: Goal)

    // Bills
    @Query("SELECT * FROM bills ORDER BY dueDay")
    fun bills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills")
    suspend fun billsList(): List<Bill>

    @Upsert suspend fun upsertBill(b: Bill)
    @Delete suspend fun deleteBill(b: Bill)
}
