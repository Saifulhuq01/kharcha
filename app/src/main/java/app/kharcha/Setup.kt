package app.kharcha

import android.Manifest
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// What the user has switched on. The setup card shows only the steps still missing.
data class SetupState(val sms: Boolean, val notif: Boolean, val usage: Boolean, val overlay: Boolean) {
    val all: Boolean get() = sms && notif && usage && overlay
    val done: Int get() = listOf(sms, notif, usage, overlay).count { it }
}

object Setup {

    // Reads the real permission state from the system every time — never cached.
    fun state(ctx: Context) = SetupState(
        sms = smsGranted(ctx),
        notif = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName),
        usage = usageGranted(ctx),
        overlay = Settings.canDrawOverlays(ctx)
    )

    private fun smsGranted(ctx: Context) =
        listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS).all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun usageGranted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // Starts the exit watcher by itself once its two permissions are on — no button needed.
    fun startWatcherIfReady(ctx: Context) {
        if (WatcherService.isRunning) return
        if (!usageGranted(ctx) || !Settings.canDrawOverlays(ctx)) return
        try {
            ctx.startForegroundService(Intent(ctx, WatcherService::class.java))
        } catch (_: Exception) {
            // Android may refuse a background start; the next app open retries.
        }
    }
}

// Restarts the watcher after a phone reboot or an app update.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Setup.startWatcherIfReady(context)
        }
    }
}
