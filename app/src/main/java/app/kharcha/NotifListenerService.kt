package app.kharcha

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Reads payment-app notifications and logs the spend.
// The user switches this on under Settings > Notification access.
class NotifListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = PAYMENT_APPS[sbn.packageName] ?: return

        val extras = sbn.notification.extras
        val full = listOf("android.title", "android.text", "android.bigText")
            .joinToString(" ") { extras.getCharSequence(it)?.toString().orEmpty() }
        val parsed = SmsParser.parse(full) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(applicationContext).expenseDao()
            // Skip if the bank SMS already logged this amount.
            if (dao.countRecent(parsed.amount, System.currentTimeMillis() - 120_000) > 0) return@launch
            dao.insert(
                Expense(
                    amount = parsed.amount, merchant = parsed.merchant,
                    source = source, category = parsed.category
                )
            )
        }
    }
}

// Payment apps we watch, and the label we tag their spends with.
val PAYMENT_APPS = mapOf(
    "com.google.android.apps.nbu.paisa.user" to "GPay",
    "com.phonepe.app" to "PhonePe",
    "net.one97.paytm" to "Paytm",
    "in.org.npci.upiapp" to "BHIM"
)
