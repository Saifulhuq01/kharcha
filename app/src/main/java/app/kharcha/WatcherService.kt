package app.kharcha

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Foreground service: notices when you LEAVE a payment app. If the spend wasn't already
// caught by SMS or notification, it opens the quick "how much?" pop-up.
class WatcherService : Service() {

    companion object {
        // Lets the home screen know the watcher is alive without querying the system.
        @Volatile var isRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg: String? = null

    private val poll = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("watcher", "Spend watcher", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val note = Notification.Builder(this, "watcher")
            .setContentTitle("Kharcha is catching your spends")
            .setSmallIcon(R.drawable.ic_stat)
            .build()
        startForeground(1, note)
        isRunning = true
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    private fun check() {
        val now = System.currentTimeMillis()
        val usm = getSystemService(UsageStatsManager::class.java) ?: return
        val events = usm.queryEvents(now - 5000, now)
        val ev = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            @Suppress("DEPRECATION")
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) latest = ev.packageName
        }
        if (latest == null || latest == lastPkg) return

        val left = lastPkg
        lastPkg = latest
        if (left != null && PAYMENT_APPS.containsKey(left) && !PAYMENT_APPS.containsKey(latest)) {
            promptAfterDelay(PAYMENT_APPS[left] ?: "GPay")
        }
    }

    // Give the notification/SMS a few seconds to land; prompt only if nothing was logged.
    private fun promptAfterDelay(source: String) {
        handler.postDelayed({
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.get(applicationContext).expenseDao()
                if (dao.countSince(System.currentTimeMillis() - 30_000) > 0) return@launch
                val i = Intent(applicationContext, QuickAddActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("source", source)
                applicationContext.startActivity(i)
            }
        }, 6000)
    }
}
