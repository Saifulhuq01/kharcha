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

    // Any spend of this amount in a time window? Flags SMS the GPay notification already logged.
    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND timestamp BETWEEN :from AND :to")
    suspend fun countNear(amount: Double, from: Long, to: Long): Int

    // Is this SMS already in Kharcha? SMS rows keep the SMS's exact arrival time, so matching on
    // time alone still works after the user edits the amount (e.g. ₹160 GPay + ₹100 cash = ₹260).
    @Query("SELECT COUNT(*) FROM expenses WHERE source = 'SMS' AND timestamp = :ts")
    suspend fun countSmsAt(ts: Long): Int

    // Same amount logged by a payment app or by hand around this time? Then the SMS is a repeat.
    @Query("SELECT COUNT(*) FROM expenses WHERE source != 'SMS' AND amount = :amount AND timestamp BETWEEN :from AND :to")
    suspend fun countNearOtherSource(amount: Double, from: Long, to: Long): Int

    // Self-heal: keep the first copy of any row that was imported twice.
    @Query("DELETE FROM expenses WHERE id NOT IN (SELECT MIN(id) FROM expenses GROUP BY timestamp, source)")
    suspend fun removeDuplicates(): Int
}
