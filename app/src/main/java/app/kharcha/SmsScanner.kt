package app.kharcha

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Imports spends already sitting in the SMS inbox.
// Remembers the newest message it read, so running it again never duplicates.
object SmsScanner {

    // Only one scan at a time — two overlapping scans would both import everything.
    private val lock = Mutex()

    suspend fun scanInbox(context: Context): Int = lock.withLock { scan(context) }

    private suspend fun scan(context: Context): Int = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).expenseDao()
        dao.removeDuplicates()
        val prefs = context.getSharedPreferences("sms_scan", Context.MODE_PRIVATE)
        val lastScan = prefs.getLong("lastScan", 0L)
        var maxDate = lastScan
        var added = 0

        val cursor = try {
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("body", "date"),
                "date > ?",
                arrayOf(lastScan.toString()),
                "date ASC"
            )
        } catch (_: SecurityException) {
            null // SMS permission not granted yet
        } ?: return@withContext 0

        cursor.use {
            val bodyIdx = it.getColumnIndexOrThrow("body")
            val dateIdx = it.getColumnIndexOrThrow("date")
            while (it.moveToNext()) {
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)
                if (date > maxDate) maxDate = date

                val parsed = SmsParser.parse(body) ?: continue
                if (dao.countExact(parsed.amount, date, "SMS") > 0) continue
                dao.insert(
                    Expense(
                        amount = parsed.amount, merchant = parsed.merchant,
                        source = "SMS", category = parsed.category, timestamp = date
                    )
                )
                added++
            }
        }
        prefs.edit().putLong("lastScan", maxDate).apply()
        added
    }
}
