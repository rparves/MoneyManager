package com.rparves.moneymanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ROW = "SELECT transactions.*, c.name AS catName, c.color AS catColor, s.name AS subName, a.name AS accName " +
    "FROM transactions LEFT JOIN categories c ON c.id = transactions.categoryId " +
    "LEFT JOIN categories s ON s.id = transactions.subId " +
    "LEFT JOIN accounts a ON a.id = transactions.accountId"

@Dao
abstract class MoneyDao {
    // Transactions
    @Query("$ROW WHERE transactions.date >= :from AND transactions.date < :to ORDER BY transactions.date DESC, transactions.id DESC")
    abstract fun txnsBetween(from: Long, to: Long): Flow<List<TxnRow>>

    @Query("$ROW ORDER BY transactions.date DESC, transactions.id DESC LIMIT :n")
    abstract fun recent(n: Int): Flow<List<TxnRow>>

    @Query("$ROW WHERE transactions.note LIKE :q OR c.name LIKE :q OR s.name LIKE :q OR a.name LIKE :q ORDER BY transactions.date DESC LIMIT 300")
    abstract fun search(q: String): Flow<List<TxnRow>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    abstract suspend fun txn(id: Long): Txn?

    @Query("SELECT * FROM transactions ORDER BY date, id")
    abstract suspend fun allTxns(): List<Txn>

    @Upsert abstract suspend fun upsertTxn(t: Txn): Long
    @Insert abstract suspend fun insertTxns(list: List<Txn>)
    @Delete abstract suspend fun deleteTxn(t: Txn)

    @Query("SELECT COALESCE(SUM(CASE WHEN isIncome THEN amount ELSE -amount END), 0) FROM transactions")
    abstract fun net(): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE date >= :from AND date < :to")
    abstract suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE isIncome = 0 AND date >= :from AND date < :to")
    abstract suspend fun expenseBetween(from: Long, to: Long): Long

    // Accounts
    @Query("SELECT * FROM accounts ORDER BY id")
    abstract fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY id")
    abstract suspend fun allAccounts(): List<Account>

    @Query("SELECT * FROM accounts ORDER BY id LIMIT 1")
    abstract suspend fun firstAccount(): Account?

    @Query("SELECT COALESCE(SUM(initial), 0) FROM accounts")
    abstract fun initialSum(): Flow<Long>

    @Query(
        "SELECT a.id AS id, a.name AS name, a.type AS type, a.initial AS initial, " +
            "a.initial + COALESCE((SELECT SUM(CASE WHEN t.isIncome THEN t.amount ELSE -t.amount END) " +
            "FROM transactions t WHERE t.accountId = a.id), 0) AS balance FROM accounts a ORDER BY a.id"
    )
    abstract fun accountBalances(): Flow<List<AccountBalance>>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id")
    abstract suspend fun txnCountForAccount(id: Long): Int

    @Insert abstract suspend fun insertAccount(a: Account): Long
    @Update abstract suspend fun updateAccount(a: Account)
    @Delete abstract suspend fun deleteAccount(a: Account)

    // Categories
    @Query("SELECT * FROM categories ORDER BY id")
    abstract fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY id")
    abstract suspend fun allCategories(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    abstract suspend fun categoryCount(): Int

    @Query("SELECT * FROM categories WHERE name = :name AND isIncome = :income AND parentId IS NULL LIMIT 1")
    abstract suspend fun categoryByName(name: String, income: Boolean): Category?

    @Query("SELECT * FROM categories WHERE isIncome = :income AND parentId IS NULL ORDER BY id LIMIT 1")
    abstract suspend fun firstCategory(income: Boolean): Category?

    @Insert abstract suspend fun insertCategories(list: List<Category>)
    @Insert abstract suspend fun insertCategory(c: Category): Long
    @Update abstract suspend fun updateCategory(c: Category)
    @Delete abstract suspend fun deleteCategoryRow(c: Category)

    @Query("UPDATE categories SET color = :color WHERE parentId = :parent")
    abstract suspend fun recolorChildren(parent: Long, color: Long)

    @Query("UPDATE transactions SET subId = NULL WHERE subId = :id")
    abstract suspend fun clearSub(id: Long)

    @Query("UPDATE transactions SET categoryId = :to, subId = NULL WHERE categoryId = :from")
    abstract suspend fun moveCategory(from: Long, to: Long)

    @Query("DELETE FROM categories WHERE parentId = :id")
    abstract suspend fun deleteChildren(id: Long)

    /** Subcategory: its transactions keep the main category. Main category: transactions move to [fallback]. */
    @Transaction
    open suspend fun deleteCategory(c: Category, fallback: Long?) {
        if (c.parentId != null) {
            clearSub(c.id)
        } else {
            if (fallback != null) moveCategory(c.id, fallback)
            deleteChildren(c.id)
        }
        deleteCategoryRow(c)
    }

    // Goals
    @Query("SELECT * FROM goals ORDER BY id")
    abstract fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals ORDER BY id")
    abstract suspend fun allGoals(): List<Goal>

    @Upsert abstract suspend fun upsertGoal(g: Goal)
    @Delete abstract suspend fun deleteGoal(g: Goal)

    // Bills
    @Query("SELECT * FROM bills ORDER BY dueDay")
    abstract fun bills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills")
    abstract suspend fun billsList(): List<Bill>

    @Upsert abstract suspend fun upsertBill(b: Bill)
    @Delete abstract suspend fun deleteBill(b: Bill)

    // Restore
    @Query("DELETE FROM transactions") abstract suspend fun clearTxns()
    @Query("DELETE FROM categories") abstract suspend fun clearCategories()
    @Query("DELETE FROM accounts") abstract suspend fun clearAccounts()
    @Query("DELETE FROM goals") abstract suspend fun clearGoals()
    @Query("DELETE FROM bills") abstract suspend fun clearBills()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putAccounts(l: List<Account>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putCategories(l: List<Category>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putTxns(l: List<Txn>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putGoals(l: List<Goal>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putBills(l: List<Bill>)

    @Transaction
    open suspend fun replaceAll(a: List<Account>, c: List<Category>, t: List<Txn>, g: List<Goal>, b: List<Bill>) {
        clearTxns(); clearCategories(); clearAccounts(); clearGoals(); clearBills()
        putAccounts(a); putCategories(c); putTxns(t); putGoals(g); putBills(b)
    }
}
