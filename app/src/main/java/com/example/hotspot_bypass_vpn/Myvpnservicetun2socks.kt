package com.example.hotspot_bypass_vpn

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.*
import androidx.core.app.NotificationCompat
import engine.Engine
import kotlin.concurrent.thread

class MyVpnServiceTun2Socks : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var proxyIp = ""
    private var proxyPort = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val ACTION_STOP = "com.example.hotspot_bypass_vpn.STOP"
        var isServiceRunning = false // ADD THIS
    }

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BypassVPN::VpnWakeLock")
        wakeLock?.acquire()

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BypassVPN::VpnWifiLock")
        wifiLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE)

        if (intent?.action == ACTION_STOP) {
            isServiceRunning = false
            prefs.edit().putBoolean("vpn_is_running_flag", false).apply()
            DebugUtils.log("Stop Action Received via Intent")
            shutdownService()
            return START_NOT_STICKY
        }
        isServiceRunning = true
        prefs.edit().putBoolean("vpn_is_running_flag", true).apply()

        if (isRunning) {
            DebugUtils.log("Service already running - resetting connections")
            resetConnections()
        }

        if (intent != null) {
            proxyIp = intent.getStringExtra("PROXY_IP") ?: "192.168.49.1"
            proxyPort = intent.getIntExtra("PROXY_PORT", 8080)
            prefs.edit()
                .putString("last_proxy_ip", proxyIp)
                .putInt("last_proxy_port", proxyPort)
                .apply()
        } else {
            proxyIp = prefs.getString("last_proxy_ip", "192.168.49.1") ?: "192.168.49.1"
            proxyPort = prefs.getInt("last_proxy_port", 8080)
            DebugUtils.log("Service restarted by system. Restored config: $proxyIp:$proxyPort")
        }

        startForegroundNotification()

        thread(name = "ProxyTest") {
            if (DebugUtils.testProxyConnection(proxyIp, proxyPort)) {
                startVpnWithTun2Socks()
            } else {
                updateNotification("Error: Cannot reach proxy at $proxyIp:$proxyPort")
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun resetConnections() {
        try {
            Engine.stop()
            Thread.sleep(500)
            vpnInterface?.close()
            vpnInterface = null
            DebugUtils.log("✓ Connections reset")
        } catch (e: Exception) {
            DebugUtils.error("Error resetting connections", e)
        }
    }

    private fun shutdownService() {
        isRunning = false

        // 1. Close the interface first to drop the system VPN route
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {}

        // 2. Stop the native engine
        try {
            Engine.stop()
        } catch (e: Exception) {}

        // 3. Remove foreground status and stop
        stopForeground(true)
        stopSelf()

        DebugUtils.log("Service shutdown sequence complete")
    }

    private fun startVpnWithTun2Socks() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Thread.sleep(500)

            DebugUtils.log("Setting up fresh VPN interface...")

            DebugUtils.log("Setting up VPN interface...")

            val builder = Builder()
                .setMtu(1350) // LOWER MTU: 1350 is the "sweet spot" for gaming over proxies
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0) // Route EVERYTHING
                // .addRoute("::", 0) // Uncomment if you want to block IPv6 leaks
                .addDisallowedApplication(packageName)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setSession("Hotspot Bypass VPN")
                .setBlocking(true) // Crucial for gaming stability

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                DebugUtils.error("Failed to establish VPN interface")
                updateNotification("Error: VPN interface creation failed")
                stopSelf()
                return
            }

            val fd = vpnInterface!!.fd
            DebugUtils.log("VPN interface established with fd: $fd")

            isRunning = true
            updateNotification("VPN Active - Routing through tun2socks")

            // Start tun2socks in a separate thread
            thread(name = "tun2socks-engine", isDaemon = false) {
                runTun2Socks(fd)
            }

        } catch (e: Exception) {
            DebugUtils.error("Failed to start VPN", e)
            updateNotification("Error: ${e.message}")
            stopSelf()
        }
    }

    private fun runTun2Socks(fd: Int) {
        try {
            val socksProxy = "socks5://$proxyIp:$proxyPort"

            DebugUtils.log("Configuring tun2socks engine...")

            // 1. Create a Key object for configuration
            val key = engine.Key()

            // 2. Set the parameters using the Key object
            // Note: The device must be "fd://<number>" for Android
            key.setDevice("fd://$fd")
            key.setProxy(socksProxy)
            key.setMTU(1420L)
            key.setLogLevel("info")

            // Optional: Some versions allow setting DNS here,
            // but often it's handled by the VPN Builder routes
            // key.setDNS("8.8.8.8,8.8.4.4")

            // 3. Register the configuration and start the engine
            Engine.insert(key)
            Engine.start()

            DebugUtils.log("✓ tun2socks started successfully!")
            updateNotification("✓ VPN Active - Connected to $proxyIp:$proxyPort")

            // Keep the thread alive while VPN is running
            while (isRunning) {
                Thread.sleep(500)
            }

        } catch (e: Exception) {
            DebugUtils.error("tun2socks error", e)
            updateNotification("Error: ${e.message}")
        } finally {
            DebugUtils.log("tun2socks engine stopped")
        }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "vpn_channel",
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("Hotspot Bypass VPN")
            .setContentText("Initializing tun2socks...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("Hotspot Bypass VPN")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        getSystemService(NotificationManager::class.java).notify(1, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        DebugUtils.log("App swiped — scheduling VPN restart...")
        val prefs = getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("last_proxy_ip", proxyIp) ?: proxyIp
        val savedPort = prefs.getInt("last_proxy_port", proxyPort)

        val restartIntent = Intent(applicationContext, MyVpnServiceTun2Socks::class.java).apply {
            putExtra("PROXY_IP", savedIp)
            putExtra("PROXY_PORT", savedPort)
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                applicationContext, 2, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                applicationContext, 2, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = SystemClock.elapsedRealtime() + 1000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        DebugUtils.log("CRITICAL: Executing Stop Sequence...")
        isServiceRunning = false
        val prefs = getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("vpn_is_running_flag", false).apply()

        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }

        try {
            vpnInterface?.close()
            vpnInterface = null
            DebugUtils.log("✓ VPN Interface closed (Routing removed)")
        } catch (e: Exception) {
            DebugUtils.error("Error closing interface", e)
        }

        try {
            Engine.stop()
            DebugUtils.log("✓ tun2socks engine stopped")
        } catch (e: Exception) {
            DebugUtils.error("Error stopping engine", e)
        }

        stopForeground(true)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)

        DebugUtils.log("VPN Service fully destroyed")

        super.onDestroy()
    }


    override fun onRevoke() {
        DebugUtils.log("VPN Permission revoked by system")
        stopSelf()
        super.onRevoke()
    }
}