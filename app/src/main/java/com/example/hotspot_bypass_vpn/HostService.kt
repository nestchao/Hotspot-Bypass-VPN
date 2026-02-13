package com.example.hotspot_bypass_vpn

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.SystemClock

class HostService : Service() {

    private var proxyServer: ProxyServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()
        acquireLocks()

        if (proxyServer == null) {
            proxyServer = ProxyServer()
            proxyServer?.start()
        }

        // START_STICKY tells Android: "If you kill this process for memory,
        // recreate the service as soon as possible."
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "host_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Host Proxy Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, HostService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bypass VPN: Host Mode Active")
            .setContentText("Keeping proxy alive while screen is off...")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopPendingIntent)
            .build()

        startForeground(2, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // This is called when the user swipes the app out of the Recents list
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)

        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule the service to restart in 1 second
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )

        super.onTaskRemoved(rootIntent)
    }

    private fun acquireLocks() {
        // Keep CPU running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BypassVPN::HostWakeLock")
        wakeLock?.acquire()

        // Keep Wi-Fi at high performance
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BypassVPN::WifiLock")
        wifiLock?.acquire()
    }

    override fun onDestroy() {
        proxyServer?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}