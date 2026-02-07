package com.example.hotspot_bypass_vpn

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class ProxyServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val PORT = 8080 // This is the port we will use

    fun start() {
        if (isRunning) return
        isRunning = true

        thread {
            try {
                // Bind to 0.0.0.0 so other devices (Phone B) can connect
                serverSocket = ServerSocket(PORT)
                Log.d("ProxyServer", "Server started on port $PORT")

                while (isRunning) {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        // Handle every new connection in its own thread
                        thread { handleClient(client) }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProxyServer", "Error starting server", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(client: Socket) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // --- STEP 1: SOCKS5 Handshake ---
            // Client sends: [VER, NMETHODS, METHODS...]
            // We just read 2 bytes to start
            if (readByte(input) != 5) { // Ver must be 5
                client.close()
                return
            }
            val nmethods = readByte(input)
            // Skip the methods list
            input.read(ByteArray(nmethods))

            // Server responds: [VER, METHOD] -> [0x05, 0x00] (No Auth)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // --- STEP 2: Request Details ---
            // Client sends: [VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT]
            val ver = readByte(input) // 5
            val cmd = readByte(input) // 1 = Connect (TCP)
            val rsv = readByte(input) // Reserved
            val atyp = readByte(input) // Address Type

            if (cmd != 1) { // We only support CONNECT (TCP) for now
                client.close()
                return
            }

            var targetHost = ""
            when (atyp) {
                1 -> { // IPv4
                    val ipBytes = ByteArray(4)
                    input.read(ipBytes)
                    targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                3 -> { // Domain Name
                    val len = readByte(input)
                    val domainBytes = ByteArray(len)
                    input.read(domainBytes)
                    targetHost = String(domainBytes)
                }
                4 -> { // IPv6 (Not supported in this basic example)
                    client.close()
                    return
                }
            }

            // Read Port (2 bytes)
            val portBytes = ByteArray(2)
            input.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            Log.d("ProxyServer", "Connecting to $targetHost:$targetPort")

            // --- STEP 3: Connect to the Real Internet ---
            // We create a socket to the target (e.g., Google.com)
            val targetSocket = Socket(targetHost, targetPort)

            // Respond to Client: "OK, Connected"
            // [VER, REP, RSV, ATYP, BND.ADDR, BND.PORT]
            val response = ByteArray(10)
            response[0] = 0x05
            response[1] = 0x00 // Succeeded
            response[3] = 0x01 // IPv4
            output.write(response)
            output.flush()

            // --- STEP 4: Data Pipe (Relay) ---
            // We need two threads:
            // 1. Client -> Target
            // 2. Target -> Client
            thread { pipe(client.getInputStream(), targetSocket.getOutputStream()) }
            thread { pipe(targetSocket.getInputStream(), client.getOutputStream()) }

        } catch (e: Exception) {
            // Log.e("ProxyServer", "Connection Error", e)
            try { client.close() } catch (ex: Exception) {}
        }
    }

    private fun pipe(ins: InputStream, out: OutputStream) {
        val buffer = ByteArray(4096)
        var len: Int
        try {
            while (ins.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
                out.flush()
            }
        } catch (e: Exception) {
            // Connection closed
        }
    }

    private fun readByte(ins: InputStream): Int {
        return ins.read()
    }
}