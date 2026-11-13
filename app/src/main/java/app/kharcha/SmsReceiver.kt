package app.kharcha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Fires whenever a new SMS arrives (even with the app closed). If it's a debit, it imports it
// from the inbox, so the spend keeps the SMS's exact time and stays linked to it after edits.
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // A long SMS arrives in parts — stitch it back together.
        val body = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.joinToString("") { it.messageBody ?: "" } ?: return
        if (SmsParser.parse(body) == null) return  // not a spend

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Give the SMS app a moment to save the message, then import it.
                // If it isn't saved yet, the next time Kharcha opens picks it up.
                delay(6_000)
                SmsScanner.scanInbox(context)
            } finally {
                pending.finish()
            }
        }
    }
}
