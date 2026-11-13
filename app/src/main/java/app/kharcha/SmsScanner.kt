package app.kharcha

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// A bank debit SMS that isn't in Kharcha — offered in the Rescan pop-up for the user to tick.
data class SmsCandidate(
    val amount: Double,
    val merchant: String?,
    val category: String,
    val timestamp: Long,
    val preview: String,
    val maybeLogged: Boolean   // a same-amount spend exists within 5 min (e.g. from the GPay notification)
)

object SmsScanner {

    // Only one inbox read at a time — two overlapping scans would both import everything.
    private val lock = Mutex()

    // Automatic import of NEW debit SMS since the last scan. Deleted spends stay deleted.
    suspend fun scanInbox(context: Context): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.get(context).expenseDao()
            dao.removeDuplicates()
            val prefs = context.getSharedPreferences("sms_scan", Context.MODE_PRIVATE)
            val lastScan = prefs.getLong("lastScan", 0L)
            var maxDate = lastScan
            var added = 0

            readInbox(context, since = lastScan) { body, date ->
                if (date > maxDate) maxDate = date
                val parsed = SmsParser.parse(body) ?: return@readInbox
                if (dao.countSmsAt(date) > 0) return@readInbox
                // GPay notification (or a manual entry) already logged this payment.
                if (dao.countNearOtherSource(parsed.amount, date - 120_000, date + 120_000) > 0) return@readInbox
                dao.insert(
                    Expense(
                        amount = parsed.amount, merchant = parsed.merchant,
                        source = "SMS", category = parsed.category, timestamp = date
                    )
                )
                added++
            }
            prefs.edit().putLong("lastScan", maxDate).apply()
            added
        }
    }

    // Manual Rescan: every debit SMS in the inbox that has no matching spend — including ones you deleted.
    suspend fun findMissing(context: Context): List<SmsCandidate> = lock.withLock {
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.get(context).expenseDao()
            val found = mutableListOf<SmsCandidate>()
            readInbox(context, since = 0L) { body, date ->
                val parsed = SmsParser.parse(body) ?: return@readInbox
                if (dao.countSmsAt(date) > 0) return@readInbox
                found += SmsCandidate(
                    amount = parsed.amount,
                    merchant = parsed.merchant,
                    category = parsed.category,
                    timestamp = date,
                    preview = body.replace(Regex("\\s+"), " ").trim().take(100),
                    maybeLogged = dao.countNear(parsed.amount, date - 300_000, date + 300_000) > 0
                )
            }
            found.sortedByDescending { it.timestamp }
        }
    }

    // Adds the SMS spends the user ticked, dated when the SMS arrived.
    suspend fun addCandidates(context: Context, picked: List<SmsCandidate>) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).expenseDao()
        picked.forEach { c ->
            if (dao.countSmsAt(c.timestamp) == 0) {
                dao.insert(
                    Expense(
                        amount = c.amount, merchant = c.merchant,
                        source = "SMS", category = c.category, timestamp = c.timestamp
                    )
                )
            }
        }
    }

    // Calls onMessage(body, date) for each inbox SMS newer than `since`, oldest first.
    private suspend fun readInbox(context: Context, since: Long, onMessage: suspend (String, Long) -> Unit) {
        val cursor = try {
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("body", "date"),
                "date > ?",
                arrayOf(since.toString()),
                "date ASC"
            )
        } catch (_: SecurityException) {
            null // SMS permission not granted yet
        } ?: return

        cursor.use {
            val bodyIdx = it.getColumnIndexOrThrow("body")
            val dateIdx = it.getColumnIndexOrThrow("date")
            while (it.moveToNext()) {
                val body = it.getString(bodyIdx) ?: continue
                onMessage(body, it.getLong(dateIdx))
            }
        }
    }
}
