package app.kharcha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Fires whenever a new SMS arrives (even with the app closed). Logs it if it's a spend.
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // A long SMS arrives in parts — stitch it back together.
        val body = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.joinToString("") { it.messageBody ?: "" } ?: return
        val parsed = SmsParser.parse(body) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).expenseDao()
                // Skip if the GPay/PhonePe notification already logged this amount.
                if (dao.countRecent(parsed.amount, System.currentTimeMillis() - 120_000) > 0) return@launch
                dao.insert(
                    Expense(
                        amount = parsed.amount, merchant = parsed.merchant,
                        source = "SMS", category = parsed.category
                    )
                )
            } finally {
                pending.finish()
            }
        }
    }
}
