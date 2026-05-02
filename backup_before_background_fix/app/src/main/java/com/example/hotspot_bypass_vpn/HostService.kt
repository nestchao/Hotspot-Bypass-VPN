package com.example.hotspot_bypass_vpn

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.*
import androidx.core.app.NotificationCompat
import android.util.Log
import android.os.SystemClock
import android.app.AlarmManager
import android.app.PendingIntent

class HostService : Service() {

    private var proxyServer: ProxyServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var p2pManager: WifiP2pManager
    private lateinit var p2pChannel: WifiP2pManager.Channel
    private var preferredBand = 1 // 1 for 2.4GHz, 2 for 5GHz

    companion object {
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        p2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        p2pChannel = p2pManager.initialize(applicationContext, mainLooper, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            isServiceRunning = false
            stopGroupAndService()
            return START_NOT_STICKY
        }
        isServiceRunning = true

        // Get the band preference from MainActivity
        preferredBand = intent?.getIntExtra("WIFI_BAND", 1) ?: 1

        startForeground(2, createNotification("Host is active with custom password"))
        acquireLocks()

        if (proxyServer == null) {
            proxyServer = ProxyServer()
            proxyServer?.start()
        }

        setupWifiDirectGroup()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun setupWifiDirectGroup() {
        // First, remove any existing group to avoid "Busy" errors
        p2pManager.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                createGroupWithPassword()
            }
            override fun onFailure(reason: Int) {
                // If no group exists, just create the new one
                createGroupWithPassword()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun createGroupWithPassword() {
        // Use the Builder to set the specific password (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // 2437MHz = 2.4GHz Channel 6 | 5180MHz = 5GHz Channel 36
                val frequency = if (preferredBand == 1) 2437 else 5180

                val config = WifiP2pConfig.Builder()
                    .setNetworkName("DIRECT-HotspotBypass")
                    .setPassphrase("87654321") // <--- YOUR NEW PASSWORD
                    .setGroupOperatingFrequency(frequency)
                    .build()

                p2pManager.createGroup(p2pChannel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        updateNotification("✓ Sharing Active | Pass: 87654321")
                    }
                    override fun onFailure(reason: Int) {
                        Log.e("HostService", "Config Group failed ($reason), trying legacy...")
                        createLegacyGroup()
                    }
                })
                return
            } catch (e: Exception) {
                Log.e("HostService", "Builder error: ${e.message}")
            }
        }
        createLegacyGroup()
    }

    @SuppressLint("MissingPermission")
    private fun createLegacyGroup() {
        p2pManager.createGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                updateNotification("✓ Sharing Active (Auto Password)")
            }
            override fun onFailure(reason: Int) {
                updateNotification("✗ Failed to create group")
            }
        })
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("HostService", "App swiped — scheduling restart...")
        val restartIntent = Intent(applicationContext, HostService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = SystemClock.elapsedRealtime() + 1000L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm if permission is denied
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("HostService", "Exact alarm permission missing, using inexact fallback", e)
            // Safety fallback for unexpected cases
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }

        super.onTaskRemoved(rootIntent)
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
            .setContentTitle("Hotspot Bypass Host")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        getSystemService(NotificationManager::class.java).notify(2, notification)
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BypassVPN::HostWakeLock")
        wakeLock?.acquire()

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BypassVPN::WifiLock")
        wifiLock?.acquire()
    }

    private fun stopGroupAndService() {
        p2pManager.removeGroup(p2pChannel, null)
        proxyServer?.stop()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        isServiceRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}