package com.example.hotspot_bypass_vpn

import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

object DebugUtils {
    private const val TAG = "VPN_DEBUG"
    private val connectionCounter = AtomicInteger(0)

    fun log(message: String) {
        Log.d(TAG, "[${Thread.currentThread().name}] $message")
    }

    fun error(message: String, e: Throwable? = null) {
        Log.e(TAG, "[${Thread.currentThread().name}] $message", e)
    }

    fun testProxyConnection(proxyIp: String, proxyPort: Int): Boolean {
        return try {
            log("Testing connection to proxy: $proxyIp:$proxyPort")
            val socket = Socket()
            socket.connect(InetSocketAddress(proxyIp, proxyPort), 5000)
            socket.close()
            log("✓ Proxy connection test PASSED")
            true
        } catch (e: Exception) {
            error("✗ Proxy connection test FAILED: ${e.message}")
            false
        }
    }

    fun testInternetConnectivity(): Boolean {
        return try {
            log("Testing internet connectivity")
            val socket = Socket()
            socket.connect(InetSocketAddress("8.8.8.8", 53), 3000)
            socket.close()
            log("✓ Internet connectivity test PASSED")
            true
        } catch (e: Exception) {
            error("✗ Internet connectivity test FAILED: ${e.message}")
            false
        }
    }

    fun dumpVpnStats(
        tcpConnections: Map<*, *>,
        udpSockets: Map<*, *>,
        writeQueueSize: Int,
        poolActiveCount: Int,
        poolQueueSize: Int
    ) {
        log("VPN Stats:")
        log("  TCP Connections: ${tcpConnections.size}")
        log("  UDP Sockets: ${udpSockets.size}")
        log("  Write Queue: $writeQueueSize")
        log("  Pool Active: $poolActiveCount")
        log("  Pool Queue: $poolQueueSize")
    }
}