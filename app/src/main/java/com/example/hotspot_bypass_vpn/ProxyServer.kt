package com.example.hotspot_bypass_vpn

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class ProxyServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val PORT = 8080

    fun start() {
        if (isRunning) return
        isRunning = true

        thread {
            try {
                // FORCE bind to 0.0.0.0 to listen on all interfaces (Hotspot + Mobile Data)
                serverSocket = ServerSocket()
                serverSocket?.reuseAddress = true
                serverSocket?.bind(InetSocketAddress("0.0.0.0", PORT))

                Log.d("ProxyServer", "SERVER STARTED on 0.0.0.0:$PORT")

                while (isRunning) {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        Log.d("ProxyServer", "NEW CLIENT CONNECTED: ${client.inetAddress.hostAddress}")
                        thread { handleClient(client) }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProxyServer", "Critical Server Error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 10000 // 10 second timeout for handshake
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // --- SOCKS5 Handshake ---
            val version = input.read()
            if (version != 5) {
                Log.e("ProxyServer", "Invalid Protocol: Received $version (Expected 5). Note: Manual Wi-Fi Proxy won't work because it uses HTTP, not SOCKS5.")
                client.close()
                return
            }

            val nMethods = input.read()
            input.read(ByteArray(nMethods)) // skip methods
            output.write(byteArrayOf(0x05, 0x00)) // No Auth
            output.flush()

            // --- Request ---
            input.read() // skip ver
            val cmd = input.read()
            input.read() // skip rsv
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
                    client.close()
                    return
                }
            }

            val portBytes = ByteArray(2)
            input.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            Log.d("ProxyServer", "Forwarding request to: $targetHost:$targetPort")

            val targetSocket = Socket(targetHost, targetPort)

            // Success response
            val response = byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
            output.write(response)
            output.flush()

            thread { pipe(client.getInputStream(), targetSocket.getOutputStream()) }
            pipe(targetSocket.getInputStream(), client.getOutputStream())

        } catch (e: Exception) {
            Log.e("ProxyServer", "Client Error: ${e.message}")
        } finally {
            client.close()
        }
    }

    private fun pipe(ins: InputStream, out: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            var len: Int
            while (ins.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
                out.flush()
            }
        } catch (e: Exception) {}
    }
}