package com.example.hotspot_bypass_vpn

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class ProxyServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val PORT = 8080

    // Thread pool for handling clients
    private val clientPool = Executors.newCachedThreadPool() // No fixed limit

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
                serverSocket?.receiveBufferSize = 65536 // Larger buffer

                val bindAddress = InetSocketAddress("0.0.0.0", PORT)
                serverSocket?.bind(bindAddress, 50)

                Log.d("ProxyServer", "✓ SERVER STARTED")
                Log.d("ProxyServer", "✓ Listening on 0.0.0.0:$PORT")

                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            // Configure socket for performance
                            client.tcpNoDelay = true
                            client.keepAlive = true
                            client.soTimeout = 30000
                            client.receiveBufferSize = 65536
                            client.sendBufferSize = 65536

                            Log.d("ProxyServer", "✓ Client: ${client.inetAddress.hostAddress}:${client.port}")

                            // Handle in thread pool
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
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // SOCKS5 Handshake
            val version = input.read()
            if (version == -1) return // Client closed connection during health check
            if (version != 5) {
                Log.e("ProxyServer", "[$clientId] ✗ Invalid version: $version")
                client.close()
                return
            }

            val nMethods = input.read()
            if (nMethods > 0) {
                val methods = ByteArray(nMethods)
                input.read(methods) // Read and discard the methods
            }

            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // SOCKS5 Request
            if (input.read() != 5) {
                client.close()
                return
            }

            val cmd = input.read()
            if (cmd != 1) {
                Log.e("ProxyServer", "[$clientId] ✗ Unsupported cmd: $cmd")
                client.close()
                return
            }

            input.read() // skip reserved
            val atyp = input.read()

            var targetHost = ""
            when (atyp) {
                1 -> { // IPv4
                    val ipBytes = ByteArray(4)
                    input.read(ipBytes)
                    targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                3 -> { // Domain
                    val len = input.read()
                    val domainBytes = ByteArray(len)
                    input.read(domainBytes)
                    targetHost = String(domainBytes)
                }
                else -> {
                    Log.e("ProxyServer", "[$clientId] ✗ Unsupported atyp: $atyp")
                    client.close()
                    return
                }
            }

            val portBytes = ByteArray(2)
            input.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            // Connect to target
            val targetSocket = Socket()
            try {
                // Optimize target socket too
                targetSocket.tcpNoDelay = true
                targetSocket.receiveBufferSize = 65536
                targetSocket.sendBufferSize = 65536

                targetSocket.connect(InetSocketAddress(targetHost, targetPort), 10000)
                Log.d("ProxyServer", "[$clientId] ✓ → $targetHost:$targetPort")

                // Send success
                val response = byteArrayOf(
                    0x05, 0x00, 0x00, 0x01,
                    0, 0, 0, 0, 0, 0
                )
                output.write(response)
                output.flush()

                // Bidirectional relay with larger buffers
                val clientToTarget = thread(name = "C2T-$clientId", isDaemon = true) {
                    pipe(input, targetSocket.getOutputStream(), 16384)
                    try { targetSocket.shutdownOutput() } catch (e: Exception) {}
                }

                val targetToClient = thread(name = "T2C-$clientId", isDaemon = true) {
                    pipe(targetSocket.getInputStream(), output, 16384)
                    try { client.shutdownOutput() } catch (e: Exception) {}
                }

                clientToTarget.join()
                targetToClient.join()

            } catch (e: Exception) {
                Log.e("ProxyServer", "[$clientId] ✗ Connect failed: ${e.message}")
                try {
                    val response = byteArrayOf(0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                    output.write(response)
                    output.flush()
                } catch (e2: Exception) {}
            } finally {
                targetSocket.close()
            }

        } catch (e: Exception) {
            Log.e("ProxyServer", "[$clientId] ✗ Error: ${e.message}")
        } finally {
            try {
                client.close()
            } catch (e: Exception) {}
        }
    }

    private fun pipe(ins: InputStream, out: OutputStream, bufferSize: Int = 16384) {
        val buffer = ByteArray(bufferSize) // Larger buffer = better throughput
        try {
            var len: Int
            while (ins.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
                // Don't flush on every write - let TCP do batching
                if (len < bufferSize / 2) {
                    out.flush() // Only flush on small writes
                }
            }
        } catch (e: Exception) {
            // Connection closed
        }
    }
}