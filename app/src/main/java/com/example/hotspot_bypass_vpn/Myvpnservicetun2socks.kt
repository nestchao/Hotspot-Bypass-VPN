package com.example.hotspot_bypass_vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import engine.Engine
import kotlin.concurrent.thread

class MyVpnServiceTun2Socks : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var proxyIp = ""
    private var proxyPort = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        proxyIp = intent?.getStringExtra("PROXY_IP") ?: "192.168.49.1"
        proxyPort = intent?.getIntExtra("PROXY_PORT", 8080) ?: 8080

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

    private fun startVpnWithTun2Socks() {
        try {
            DebugUtils.log("Setting up VPN interface...")

            val builder = Builder()
                .setMtu(1500)
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setSession("Hotspot Bypass VPN")
                .setBlocking(true)

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
            key.setMTU(1500L)
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
                Thread.sleep(1000)
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

    override fun onDestroy() {
        DebugUtils.log("Stopping VPN service...")
        isRunning = false

        try {
            DebugUtils.log("Stopping tun2socks engine...")
            Engine.stop() // Change Tun2socks.stop to Engine.stop
            DebugUtils.log("✓ tun2socks stopped")
        } catch (e: Exception) {
            DebugUtils.error("Error stopping tun2socks", e)
        }

        try {
            vpnInterface?.close()
            vpnInterface = null
            DebugUtils.log("✓ VPN interface closed")
        } catch (e: Exception) {
            DebugUtils.error("Error closing VPN interface", e)
        }

        stopForeground(true) // Removes the notification
        stopSelf()           // Ensures the service is killed

        super.onDestroy()
    }

    override fun onRevoke() {
        DebugUtils.log("VPN permission revoked by user")
        stopSelf()
        super.onRevoke()
    }
}