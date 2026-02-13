package com.example.hotspot_bypass_vpn

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.*
import androidx.core.app.NotificationCompat
import android.net.wifi.p2p.WifiP2pConfig
import android.util.Log
import android.os.Build

class HostService : Service() {

    private var proxyServer: ProxyServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var p2pManager: WifiP2pManager
    private lateinit var p2pChannel: WifiP2pManager.Channel

    private var preferredBand = 1

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

        // Get the band from intent (default to 1 which is 2.4GHz)
        preferredBand = intent?.getIntExtra("WIFI_BAND", 1) ?: 1
        Log.d("HostService", "⚡ Starting with band preference: $preferredBand (${if(preferredBand == 1) "2.4GHz" else "5GHz"})")

        startForeground(2, createNotification("Initializing Wi-Fi Group..."))
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
        // Remove existing group first to ensure a clean start
        p2pManager.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("HostService", "✓ Old group removed, creating new one...")
                Thread.sleep(500) // Small delay to ensure clean state
                createGroup()
            }
            override fun onFailure(reason: Int) {
                Log.d("HostService", "No existing group to remove (reason: $reason), creating new one...")
                createGroup()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun createGroup() {
        // 1. Logic for Android 10 (API 29) and above - Use the Official Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val frequency = if (preferredBand == 1) 2437 else 5180 // 2437 = 2.4GHz Ch 6, 5180 = 5GHz Ch 36

                val config = WifiP2pConfig.Builder()
                    .setGroupOperatingFrequency(frequency) // This is the official way!
                    .setNetworkName("DIRECT-HotspotBypass") // Optional: custom SSID
                    .setPassphrase("password123")         // Optional: custom password
                    .build()

                p2pManager.createGroup(p2pChannel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d("HostService", "✓ Group created via Builder on $frequency MHz")
                        Handler(Looper.getMainLooper()).postDelayed({ checkActualGroupFrequency() }, 1000)
                    }
                    override fun onFailure(reason: Int) {
                        Log.e("HostService", "✗ Builder Group failed: $reason")
                        // Fallback to legacy if builder fails
                        createLegacyGroup()
                    }
                })
                return // Exit early
            } catch (e: Exception) {
                Log.e("HostService", "Builder error, falling back: ${e.message}")
            }
        }

        // 2. Fallback for older Android versions
        createLegacyGroup()
    }

    @SuppressLint("MissingPermission")
    private fun createLegacyGroup() {
        p2pManager.createGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d("HostService", "Legacy Group Success") }
            override fun onFailure(reason: Int) { Log.e("HostService", "Legacy Group Failed: $reason") }
        })
    }

    @SuppressLint("MissingPermission")
    private fun checkActualGroupFrequency() {
        p2pManager.requestGroupInfo(p2pChannel) { group ->
            if (group != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val freq = group.frequency
                    val band = if (freq > 4000) "5GHz" else "2.4GHz"
                    val bandEmoji = if (freq > 4000) "⚠️" else "✓"

                    Log.d("HostService", "═══════════════════════════════")
                    Log.d("HostService", "$bandEmoji ACTUAL OPERATING BAND: $band")
                    Log.d("HostService", "   Frequency: $freq MHz")
                    Log.d("HostService", "   Expected: ${if(preferredBand == 1) "2.4GHz" else "5GHz"}")
                    Log.d("HostService", "═══════════════════════════════")

                    if ((preferredBand == 1 && freq > 4000) || (preferredBand == 2 && freq < 4000)) {
                        Log.w("HostService", "⚠️ WARNING: Band mismatch detected!")
                        updateNotification("⚠️ Active: $band (Expected: ${if(preferredBand == 1) "2.4GHz" else "5GHz"})")
                    } else {
                        updateNotification("✓ Sharing Active: $band")
                    }
                } else {
                    updateNotification("Sharing Active")
                }
            }
        }
    }

    private fun handleGroupSuccess() {
        val bandName = if (preferredBand == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "5GHz" else "2.4GHz/Auto"
        updateNotification("Sharing Active ($bandName)")
        sendBroadcast(Intent("HOST_STATS_UPDATE").putExtra("status", "Running"))
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