package com.stepunlock.app

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ScreenTimeService : Service() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var screenReceiver: BroadcastReceiver? = null
    private val checkRunnable = object : Runnable {
        override fun run() {
            checkScreenTime()
            handler.postDelayed(this, 10_000)
        }
    }

    companion object {
        const val CHANNEL_ID = "stepunlock_channel"
        const val NOTIF_ID   = 1
    }

    override fun onCreate() {
        super.onCreate()
        prefs          = getSharedPreferences("stepunlock", Context.MODE_PRIVATE)
        dpm            = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, StepUnlockDeviceAdminReceiver::class.java)
        createChannel()
        startForeground(NOTIF_ID, buildNotification("StepUnlock is watching your screen time"))
        registerScreenReceiver()
        handler.post(checkRunnable)
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        // Screen turned on — start counting if we didn't lock it
                        if (!prefs.getBoolean("locked_by_app", false)) {
                            prefs.edit().putLong("screen_start_time", System.currentTimeMillis()).apply()
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        // Screen off by user — reset timer
                        if (!prefs.getBoolean("locked_by_app", false)) {
                            prefs.edit().putLong("screen_start_time", 0L).apply()
                            updateNotif("Screen off — timer reset")
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // Lock screen dismissed
                        if (prefs.getBoolean("locked_by_app", false)) {
                            // We locked it — show the step unlock screen
                            launchUnlockActivity()
                        } else {
                            prefs.edit().putLong("screen_start_time", System.currentTimeMillis()).apply()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun checkScreenTime() {
        val start = prefs.getLong("screen_start_time", 0L)
        if (start == 0L || prefs.getBoolean("locked_by_app", false)) return

        val limitMs   = prefs.getInt("time_limit_minutes", 30) * 60_000L
        val elapsed   = System.currentTimeMillis() - start
        val remaining = limitMs - elapsed

        if (remaining <= 0) {
            lockScreen()
        } else {
            val mins = (remaining / 60_000).toInt()
            val secs = ((remaining % 60_000) / 1000).toInt()
            val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            val goal = prefs.getInt("step_goal", 20)
            updateNotif("Locks in $timeStr — $goal steps to unlock")
        }
    }

    private fun lockScreen() {
        if (!dpm.isAdminActive(adminComponent)) return
        prefs.edit().putBoolean("locked_by_app", true).putLong("screen_start_time", 0L).apply()
        val goal = prefs.getInt("step_goal", 20)
        updateNotif("Locked — walk $goal steps to unlock")
        dpm.lockNow()
    }

    private fun launchUnlockActivity() {
        startActivity(Intent(this, UnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StepUnlock")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "StepUnlock", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Screen time monitoring"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    override fun onDestroy() {
        super.onDestroy()
        screenReceiver?.let { unregisterReceiver(it) }
        handler.removeCallbacks(checkRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
