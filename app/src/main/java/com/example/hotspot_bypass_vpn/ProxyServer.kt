package com.example.hotspot_bypass_vpn

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ProxyServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val PORT = 8080

    // ULTRA-OPTIMIZED: Massive thread pool for Instagram's parallel connections
    private val clientPool = ThreadPoolExecutor(
        100,  // Core threads
        2000, // Max threads (Instagram can open 100+ connections)
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(10000),
        ThreadFactory { r -> Thread(r).apply {
            priority = Thread.NORM_PRIORITY + 1
            name = "Proxy-${Thread.currentThread().id}"
        }},
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    // Statistics
    private val activeConnections = AtomicInteger(0)
    private val totalConnections = AtomicInteger(0)
    private val bytesTransferred = AtomicInteger(0)

    fun start() {
        if (isRunning) {
            Log.w("ProxyServer", "Server already running")
            return
        }
        isRunning = true

        thread(name = "ProxyServer-Main", isDaemon = true) {
            try {
                serverSocket = ServerSocket()
                serverSocket?.reuseAddress = true
                serverSocket?.receiveBufferSize = 131072 // 128KB

                val bindAddress = InetSocketAddress("0.0.0.0", PORT)
                serverSocket?.bind(bindAddress, 200) // Bigger backlog

                Log.d("ProxyServer", "✓ ULTRA-FAST SERVER STARTED")
                Log.d("ProxyServer", "✓ Listening on 0.0.0.0:$PORT")
                Log.d("ProxyServer", "✓ Ready for Instagram traffic")

                // Stats thread
                thread(name = "Stats", isDaemon = true) {
                    while (isRunning) {
                        Thread.sleep(10000)
                        Log.d("ProxyServer", "Stats: Active=${activeConnections.get()}, Total=${totalConnections.get()}, Data=${bytesTransferred.get()/1024}KB")
                    }
                }

                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            totalConnections.incrementAndGet()

                            // ULTRA-OPTIMIZED: Configure socket for max performance
                            client.tcpNoDelay = true
                            client.keepAlive = true
                            client.soTimeout = 120000 // 2 min timeout
                            client.receiveBufferSize = 262144 // 256KB
                            client.sendBufferSize = 262144   // 256KB

                            Log.d("ProxyServer", "✓ Client ${totalConnections.get()}: ${client.inetAddress.hostAddress}")

                            clientPool.execute {
                                handleClient(client)
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e("ProxyServer", "Accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProxyServer", "✗ Server Error", e)
            }
        }
    }

    fun stop() {
        Log.d("ProxyServer", "Stopping server...")
        isRunning = false
        clientPool.shutdown()
        try {
            serverSocket?.close()
            Log.d("ProxyServer", "Server stopped")
        } catch (e: Exception) {
            Log.e("ProxyServer", "Error stopping: ${e.message}")
        }
    }

    private fun handleClient(client: Socket) {
        val clientId = "${client.inetAddress.hostAddress}:${client.port}"
        activeConnections.incrementAndGet()

        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // SOCKS5 Handshake - Fast path
            val version = input.read()
            if (version == -1) return
            if (version != 5) {
                Log.e("ProxyServer", "[$clientId] ✗ Bad version: $version")
                return
            }

            val nMethods = input.read()
            if (nMethods > 0) {
                input.skip(nMethods.toLong()) // Fast skip
            }

            // Send response immediately
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // SOCKS5 Request - Fast path
            if (input.read() != 5) return

            val cmd = input.read()
            if (cmd != 1) {
                Log.w("ProxyServer", "[$clientId] Unsupported cmd: $cmd")
                return
            }

            input.read() // skip reserved
            val atyp = input.read()

            var targetHost = ""
            when (atyp) {
                1 -> { // IPv4 - Fast path
                    val ipBytes = ByteArray(4)
                    input.read(ipBytes)
                    targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                3 -> { // Domain - Common for Instagram CDN
                    val len = input.read()
                    val domainBytes = ByteArray(len)
                    input.read(domainBytes)
                    targetHost = String(domainBytes)
                }
                else -> {
                    Log.e("ProxyServer", "[$clientId] Bad atyp: $atyp")
                    return
                }
            }

            val portBytes = ByteArray(2)
            input.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            // Connect to target with timeout
            val targetSocket = Socket()
            try {
                // ULTRA-OPTIMIZED: Configure target socket
                targetSocket.tcpNoDelay = true
                targetSocket.keepAlive = true
                targetSocket.receiveBufferSize = 524288 // 512KB for Instagram media
                targetSocket.sendBufferSize = 524288
                targetSocket.soTimeout = 120000

                targetSocket.connect(InetSocketAddress(targetHost, targetPort), 15000)
                Log.d("ProxyServer", "[$clientId] ✓ → $targetHost:$targetPort")

                // Send success response
                val response = byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                output.write(response)
                output.flush()

                // ULTRA-OPTIMIZED: Zero-copy bidirectional relay
                val latch = CountDownLatch(2)

                // Client → Target
                val c2t = thread(name = "C2T-$clientId", isDaemon = true) {
                    try {
                        pipeOptimized(input, targetSocket.getOutputStream(), "C→T")
                    } finally {
                        try { targetSocket.shutdownOutput() } catch (e: Exception) {}
                        latch.countDown()
                    }
                }

                // Target → Client
                val t2c = thread(name = "T2C-$clientId", isDaemon = true) {
                    try {
                        pipeOptimized(targetSocket.getInputStream(), output, "T→C")
                    } finally {
                        try { client.shutdownOutput() } catch (e: Exception) {}
                        latch.countDown()
                    }
                }

                // Wait for both directions to complete
                latch.await(120, TimeUnit.SECONDS)

            } catch (e: Exception) {
                Log.e("ProxyServer", "[$clientId] Connect failed: ${e.message}")
                try {
                    val errorResponse = byteArrayOf(0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                    output.write(errorResponse)
                    output.flush()
                } catch (e2: Exception) {}
            } finally {
                try { targetSocket.close() } catch (e: Exception) {}
            }

        } catch (e: Exception) {
            Log.e("ProxyServer", "[$clientId] Error: ${e.message}")
        } finally {
            try { client.close() } catch (e: Exception) {}
            activeConnections.decrementAndGet()
        }
    }

    // ULTRA-OPTIMIZED: Large buffer with adaptive flushing
    private fun pipeOptimized(ins: InputStream, out: OutputStream, direction: String) {
        val buffer = ByteArray(65536) // 64KB buffer for max throughput
        var totalBytes = 0L

        try {
            var len: Int
            while (ins.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
                totalBytes += len

                // Adaptive flushing:
                // - Small writes: flush immediately (interactive)
                // - Large writes: let TCP buffer (bulk transfer)
                if (len < 8192) {
                    out.flush()
                }

                // Periodic flush for bulk transfers
                if (totalBytes % 131072 == 0L) { // Every 128KB
                    out.flush()
                }
            }
        } catch (e: Exception) {
            // Connection closed - normal
        } finally {
            try {
                out.flush()
            } catch (e: Exception) {}
        }

        bytesTransferred.addAndGet(totalBytes.toInt())
    }
}