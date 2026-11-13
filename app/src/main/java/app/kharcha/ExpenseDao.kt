package app.kharcha

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    // Live list: the screen updates by itself whenever the table changes.
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    // One-shot snapshot for CSV export.
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<Expense>

    // Dedup guard: stops one payment being logged twice (bank SMS + GPay notification).
    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND timestamp >= :since")
    suspend fun countRecent(amount: Double, since: Long): Int

    // Lets the exit pop-up skip itself when the spend was already caught.
    @Query("SELECT COUNT(*) FROM expenses WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    // Same SMS already imported? (SMS timestamps are millisecond-exact, so this is safe.)
    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND timestamp = :ts AND source = :source")
    suspend fun countExact(amount: Double, ts: Long, source: String): Int

    // Self-heal: keep the first copy of any row that was imported twice.
    @Query("DELETE FROM expenses WHERE id NOT IN (SELECT MIN(id) FROM expenses GROUP BY amount, timestamp, source)")
    suspend fun removeDuplicates(): Int
}
