package com.example.hotspot_bypass_vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val proxyIp = intent?.getStringExtra("PROXY_IP") ?: "192.168.49.1"
        val proxyPort = intent?.getIntExtra("PROXY_PORT", 8080) ?: 8080

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification())
        }

        // Establish VPN
        vpnInterface = Builder()
            .setMtu(1500)
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setSession("HotspotBypass")
            .establish()

        // START THE ENGINE
        runVpnEngine(proxyIp, proxyPort)

        return START_STICKY
    }

    private fun runVpnEngine(ip: String, port: Int) {
        vpnThread = thread {
            val fd = vpnInterface?.fileDescriptor ?: return@thread
            val input = FileInputStream(fd).channel
            val output = FileOutputStream(fd).channel
            val buffer = ByteBuffer.allocate(16384)

            try {
                Log.d("VPN_ENGINE", "VPN Engine is running and waiting for packets...")
                while (true) {
                    // Read a packet from the system
                    val readLen = input.read(buffer)
                    if (readLen > 0) {
                        Log.d("VPN_ENGINE", "Captured a packet of size $readLen. Need Tun2Socks to forward this to $ip:$port")
                        buffer.clear()
                    }
                    Thread.sleep(100) // Small delay to prevent CPU overheating
                }
            } catch (e: Exception) {
                Log.e("VPN_ENGINE", "Error", e)
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "VPN Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VPN Active")
            .setContentText("Checking for traffic...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        vpnThread?.interrupt()
        vpnInterface?.close()
        super.onDestroy()
    }
}