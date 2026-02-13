package com.example.hotspot_bypass_vpn

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.*
import androidx.core.app.NotificationCompat

class HostService : Service() {

    private var proxyServer: ProxyServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var p2pManager: WifiP2pManager
    private lateinit var p2pChannel: WifiP2pManager.Channel

    override fun onCreate() {
        super.onCreate()
        p2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        p2pChannel = p2pManager.initialize(this, mainLooper, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopGroupAndService()
            return START_NOT_STICKY
        }

        startForeground(2, createNotification("Initializing Wi-Fi Group..."))
        acquireLocks()

        // Start Proxy
        if (proxyServer == null) {
            proxyServer = ProxyServer()
            proxyServer?.start()
        }

        // Start Wi-Fi Direct Group inside the Service
        setupWifiDirectGroup()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun setupWifiDirectGroup() {
        // Remove existing group first to ensure a clean start
        p2pManager.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { createGroup() }
            override fun onFailure(reason: Int) { createGroup() }
        })
    }

    @SuppressLint("MissingPermission")
    private fun createGroup() {
        p2pManager.createGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                updateNotification("Sharing Active (SOCKS5: 8080)")
                // Broadcast to UI if it's open
                sendBroadcast(Intent("HOST_STATS_UPDATE").putExtra("status", "Running"))
            }
            override fun onFailure(reason: Int) {
                updateNotification("Error: Failed to create Wi-Fi Group ($reason)")
            }
        })
    }

    private fun stopGroupAndService() {
        p2pManager.removeGroup(p2pChannel, null)
        proxyServer?.stop()
        stopSelf()
    }

    private fun createNotification(content: String): Notification {
        val channelId = "host_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Host Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, HostService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Hotspot Bypass: Host Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        getSystemService(NotificationManager::class.java).notify(2, notification)
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BypassVPN::HostWakeLock")
        wakeLock?.acquire()

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BypassVPN::WifiLock")
        wifiLock?.acquire()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // This keeps the service alive when swiped away
        val restartServiceIntent = Intent(applicationContext, this.javaClass).also { it.setPackage(packageName) }
        val restartServicePendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        val alarmService = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}