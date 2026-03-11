package com.example.trufocus

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import java.util.*

class BlockerService : Service() {
    private var timer: Timer? = null
    private var lastBlockTime: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "trufocus_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Focus Shield", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TruFocus Active").setContentText("Monitoring distractions...").setSmallIcon(android.R.drawable.ic_lock_idle_lock).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, notification)

        startTimer()
        return START_STICKY
    }

    private fun startTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val now = System.currentTimeMillis()
                if (now - lastBlockTime < 10000) return
                checkAndBlock()
            }
        }, 0, 1500)
    }

    private fun checkAndBlock() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000, now)
        val currentApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: return

        if (currentApp != packageName) {
            val prefs = getSharedPreferences("TruFocusPrefs", Context.MODE_PRIVATE)
            val limit = prefs.getInt(currentApp, 0)
            if (limit > 0) {
                val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }
                val dailyStats = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
                val used = (dailyStats[currentApp]?.totalTimeInForeground ?: 0L) / 60000
                if (used >= limit) {
                    lastBlockTime = System.currentTimeMillis()
                    val blockIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("isBlocked", true)
                    }
                    startActivity(blockIntent)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
