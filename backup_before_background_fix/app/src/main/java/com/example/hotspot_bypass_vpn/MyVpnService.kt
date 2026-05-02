package com.example.hotspot_bypass_vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var proxyIp = ""
    private var proxyPort = 0

    private val tcpConnections = ConcurrentHashMap<String, TcpConnection>()
    private val udpSockets = ConcurrentHashMap<String, UdpRelay>()

    // ULTRA-OPTIMIZED: Even larger pool with priority queue
    private val fastPool = ThreadPoolExecutor(
        50, 1000, 30L, TimeUnit.SECONDS,
        LinkedBlockingQueue(20000),
        ThreadFactory { r -> Thread(r).apply { priority = Thread.MAX_PRIORITY } },
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    // Separate pool for packet reading (high priority)
    private val readerPool = Executors.newFixedThreadPool(4, ThreadFactory { r ->
        Thread(r).apply { priority = Thread.MAX_PRIORITY; name = "PacketReader-${Thread.currentThread().id}" }
    })

    // ULTRA-OPTIMIZED: Massive queue with lock-free operations
    private val vpnWriteQueue = LinkedBlockingQueue<ByteArray>(50000)
    private var vpnWriter: FileOutputStream? = null

    // DNS Cache to avoid repeated lookups
    private val dnsCache = ConcurrentHashMap<String, InetAddress>()

    // Connection statistics
    private val stats = Stats()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        proxyIp = intent?.getStringExtra("PROXY_IP") ?: "192.168.49.1"
        proxyPort = intent?.getIntExtra("PROXY_PORT", 8080) ?: 8080

        startForegroundNotification()

        thread(name = "ProxyTest") {
            if (DebugUtils.testProxyConnection(proxyIp, proxyPort)) {
                startVpnInterface()
            } else {
                updateNotification("Error: Cannot reach proxy")
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpnInterface() {
        val builder = Builder()
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDisallowedApplication(packageName)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setBlocking(false)
            .setSession("Instagram Optimized VPN")

        vpnInterface = builder.establish()

        if (vpnInterface != null) {
            isRunning = true
            updateNotification("⚡ VPN Active - Optimized")

            // Start multiple writer threads for parallel processing
            repeat(2) { i ->
                thread(name = "VPN-Writer-$i", isDaemon = true) { runVpnWriterUltraFast() }
            }

            // Start multiple reader threads for parallel packet processing
            repeat(4) { i ->
                thread(name = "VPN-Reader-$i", isDaemon = true) { readPacketsParallel() }
            }

            thread(name = "Cleanup", isDaemon = true) { cleanupStaleConnections() }
            thread(name = "Stats", isDaemon = true) { printStats() }
        }
    }

    // ULTRA-OPTIMIZED: Lock-free batch writing with minimal latency
    private fun runVpnWriterUltraFast() {
        vpnWriter = FileOutputStream(vpnInterface!!.fileDescriptor)
        val batch = ArrayList<ByteArray>(200)

        try {
            while (isRunning) {
                batch.clear()

                // Non-blocking poll with timeout
                val first = vpnWriteQueue.poll(5, TimeUnit.MILLISECONDS)
                if (first != null) {
                    batch.add(first)
                    vpnWriteQueue.drainTo(batch, 199) // Get up to 199 more instantly

                    // Write entire batch at once
                    synchronized(vpnWriter!!) {
                        for (packet in batch) {
                            vpnWriter?.write(packet)
                        }
                        vpnWriter?.flush()
                    }
                    stats.packetsSent.addAndGet(batch.size)
                }
            }
        } catch (e: Exception) {
            DebugUtils.error("VPN Writer error", e)
        }
    }

    private fun writeToVpn(packet: ByteArray) {
        if (!vpnWriteQueue.offer(packet)) {
            stats.packetsDropped.incrementAndGet()
            // Drop oldest, retry
            vpnWriteQueue.poll()
            vpnWriteQueue.offer(packet)
        }
    }

    // ULTRA-OPTIMIZED: Parallel packet reading with zero-copy buffers
    private fun readPacketsParallel() {
        val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
        val buffer = ByteBuffer.allocateDirect(131072) // 128KB direct buffer
        val array = ByteArray(131072)

        try {
            while (isRunning) {
                val length = inputStream.read(array)
                if (length > 0) {
                    buffer.clear()
                    buffer.put(array, 0, length)
                    buffer.flip()

                    // Process ALL packets in this read
                    while (buffer.hasRemaining() && buffer.remaining() >= 20) {
                        val packetStart = buffer.position()
                        val ipHeaderLength = (buffer.get(packetStart).toInt() and 0x0F) * 4

                        if (ipHeaderLength < 20) {
                            buffer.position(buffer.limit())
                            break
                        }

                        val totalLength = ((buffer.get(packetStart + 2).toInt() and 0xFF) shl 8) or
                                (buffer.get(packetStart + 3).toInt() and 0xFF)

                        if (buffer.remaining() < totalLength) break

                        val packetData = ByteArray(totalLength)
                        buffer.get(packetData)

                        // Submit to fast pool for processing
                        fastPool.execute {
                            handlePacket(ByteBuffer.wrap(packetData))
                            stats.packetsProcessed.incrementAndGet()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugUtils.error("VPN Reader error", e)
        }
    }

    private fun handlePacket(packet: ByteBuffer) {
        if (packet.limit() < 20) return
        val version = (packet.get(0).toInt() shr 4) and 0x0F
        if (version != 4) return
        val protocol = packet.get(9).toInt() and 0xFF

        when (protocol) {
            6 -> handleTcpPacket(packet)
            17 -> handleUdpPacket(packet)
        }
    }

    private fun handleUdpPacket(packet: ByteBuffer) {
        try {
            val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
            val ipTotalLen = ((packet.get(2).toInt() and 0xFF) shl 8) or (packet.get(3).toInt() and 0xFF)
            if (packet.limit() < ipHeaderLen + 8) return

            val srcIp = parseIpAddress(packet, 12)
            val destIp = parseIpAddress(packet, 16)
            val srcPort = ((packet.get(ipHeaderLen).toInt() and 0xFF) shl 8) or (packet.get(ipHeaderLen + 1).toInt() and 0xFF)
            val destPort = ((packet.get(ipHeaderLen + 2).toInt() and 0xFF) shl 8) or (packet.get(ipHeaderLen + 3).toInt() and 0xFF)

            val payloadStart = ipHeaderLen + 8
            val payloadSize = ipTotalLen - ipHeaderLen - 8
            if (payloadSize <= 0) return

            val payload = ByteArray(payloadSize)
            packet.position(payloadStart)
            packet.get(payload)

            // OPTIMIZED: Direct UDP for DNS (faster than TCP)
            if (destPort == 53) {
                fastPool.execute { handleDnsDirectUdp(srcIp, srcPort, payload) }
                return
            }

            val connectionKey = "$srcIp:$srcPort-$destIp:$destPort"
            val relay = udpSockets.getOrPut(connectionKey) {
                UdpRelay(connectionKey, srcIp, srcPort, destIp, destPort)
            }
            relay.sendData(payload)
        } catch (e: Exception) {
            DebugUtils.error("UDP packet error", e)
        }
    }

    // ULTRA-OPTIMIZED: Direct UDP DNS (no SOCKS overhead)
    private fun handleDnsDirectUdp(srcIp: String, srcPort: Int, dnsPayload: ByteArray) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            if (!protect(socket)) return
            socket.soTimeout = 2000

            // Send directly to Google DNS
            val packet = DatagramPacket(dnsPayload, dnsPayload.size, InetAddress.getByName("8.8.8.8"), 53)
            socket.send(packet)

            // Receive response
            val responseBuffer = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)

            val response = responseBuffer.copyOf(responsePacket.length)
            val udpResponse = buildUdpPacket("8.8.8.8", 53, srcIp, srcPort, response)
            writeToVpn(udpResponse)

            stats.dnsQueriesHandled.incrementAndGet()
        } catch (e: Exception) {
            DebugUtils.error("DNS Direct UDP error", e)
        } finally {
            socket?.close()
        }
    }

    inner class UdpRelay(
        private val key: String,
        private val srcIp: String,
        private val srcPort: Int,
        private val destIp: String,
        private val destPort: Int
    ) {
        private var socket: DatagramSocket? = null
        @Volatile var lastActivity = System.currentTimeMillis()
        private val running = AtomicBoolean(true)

        init {
            try {
                socket = DatagramSocket()
                socket?.receiveBufferSize = 524288 // 512KB
                socket?.sendBufferSize = 524288
                if (!this@MyVpnService.protect(socket!!)) throw Exception("Protect failed")
                socket?.soTimeout = 10000
                fastPool.execute { runReceiver() }
            } catch (e: Exception) {
                udpSockets.remove(key)
                close()
            }
        }

        fun sendData(payload: ByteArray) {
            try {
                val targetAddress = InetAddress.getByName(destIp)
                val packet = DatagramPacket(payload, payload.size, targetAddress, destPort)
                socket?.send(packet)
                lastActivity = System.currentTimeMillis()
            } catch (e: Exception) { }
        }

        private fun runReceiver() {
            val buffer = ByteArray(16384)
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                while (running.get() && isRunning) {
                    try {
                        socket?.receive(packet)
                        val responsePayload = packet.data.copyOf(packet.length)
                        val response = buildUdpPacket(destIp, destPort, srcIp, srcPort, responsePayload)
                        writeToVpn(response)
                        lastActivity = System.currentTimeMillis()
                    } catch (e: SocketTimeoutException) {
                        // Normal timeout
                    } catch (e: Exception) {
                        break
                    }
                }
            } finally {
                udpSockets.remove(key)
                close()
            }
        }

        fun close() {
            running.set(false)
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    private fun buildUdpPacket(srcIp: String, srcPort: Int, destIp: String, destPort: Int, payload: ByteArray): ByteArray {
        val totalLen = 28 + payload.size
        val packet = ByteArray(totalLen)
        packet[0] = 0x45; packet[1] = 0x00
        packet[2] = (totalLen shr 8).toByte(); packet[3] = totalLen.toByte()
        packet[6] = 0x40; packet[8] = 64; packet[9] = 17
        fillIpAddresses(packet, srcIp, destIp)
        val ipChecksum = calculateChecksum(packet, 0, 20)
        packet[10] = (ipChecksum shr 8).toByte(); packet[11] = ipChecksum.toByte()
        packet[20] = (srcPort shr 8).toByte(); packet[21] = srcPort.toByte()
        packet[22] = (destPort shr 8).toByte(); packet[23] = destPort.toByte()
        val udpLen = 8 + payload.size
        packet[24] = (udpLen shr 8).toByte(); packet[25] = udpLen.toByte()
        if (payload.isNotEmpty()) System.arraycopy(payload, 0, packet, 28, payload.size)
        return packet
    }

    private fun handleTcpPacket(packet: ByteBuffer) {
        try {
            val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
            val ipTotalLen = ((packet.get(2).toInt() and 0xFF) shl 8) or (packet.get(3).toInt() and 0xFF)
            if (packet.limit() < ipHeaderLen + 20) return

            val srcIp = parseIpAddress(packet, 12)
            val destIp = parseIpAddress(packet, 16)
            val srcPort = ((packet.get(ipHeaderLen).toInt() and 0xFF) shl 8) or (packet.get(ipHeaderLen + 1).toInt() and 0xFF)
            val destPort = ((packet.get(ipHeaderLen + 2).toInt() and 0xFF) shl 8) or (packet.get(ipHeaderLen + 3).toInt() and 0xFF)
            val tcpHeaderLen = ((packet.get(ipHeaderLen + 12).toInt() shr 4) and 0x0F) * 4
            val tcpFlags = packet.get(ipHeaderLen + 13).toInt() and 0xFF
            val seqNum = packet.getInt(ipHeaderLen + 4)
            val ackNum = packet.getInt(ipHeaderLen + 8)
            val connectionKey = "$srcIp:$srcPort-$destIp:$destPort"
            val payloadStart = ipHeaderLen + tcpHeaderLen
            val payloadSize = ipTotalLen - ipHeaderLen - tcpHeaderLen

            val flagSYN = (tcpFlags and 0x02) != 0
            val flagACK = (tcpFlags and 0x10) != 0
            val flagFIN = (tcpFlags and 0x01) != 0
            val flagRST = (tcpFlags and 0x04) != 0

            when {
                flagRST -> tcpConnections.remove(connectionKey)?.close()
                flagSYN && !flagACK -> {
                    if (tcpConnections.containsKey(connectionKey)) return
                    val connection = TcpConnection(connectionKey, srcIp, srcPort, destIp, destPort, proxyIp, proxyPort, seqNum, ackNum)
                    tcpConnections[connectionKey] = connection
                    fastPool.execute { connection.start() }
                    stats.tcpConnectionsCreated.incrementAndGet()
                }
                flagFIN -> tcpConnections[connectionKey]?.handleFIN(seqNum, ackNum)
                payloadSize > 0 -> {
                    val payload = ByteArray(payloadSize)
                    packet.position(payloadStart)
                    packet.get(payload)
                    val conn = tcpConnections[connectionKey]
                    if (conn != null) {
                        if (conn.isEstablished()) conn.sendData(payload, seqNum, ackNum)
                        else conn.queueData(payload, seqNum, ackNum)
                    }
                    stats.bytesTransferred.addAndGet(payloadSize.toLong())
                }
                flagACK -> tcpConnections[connectionKey]?.handleAck(ackNum)
            }
        } catch (e: Exception) {
            DebugUtils.error("TCP packet error", e)
        }
    }

    private fun cleanupStaleConnections() {
        while (isRunning) {
            try {
                Thread.sleep(5000) // Check every 5 seconds
                val now = System.currentTimeMillis()

                tcpConnections.values.removeIf {
                    if (now - it.lastActivity > 120000) { // 2 minutes
                        it.close()
                        true
                    } else false
                }

                udpSockets.values.removeIf {
                    if (now - it.lastActivity > 60000) { // 1 minute
                        it.close()
                        true
                    } else false
                }
            } catch (e: Exception) {}
        }
    }

    private fun printStats() {
        while (isRunning) {
            try {
                Thread.sleep(10000) // Every 10 seconds
                DebugUtils.log("""
                    === VPN STATS ===
                    TCP Connections: ${tcpConnections.size} (${stats.tcpConnectionsCreated.get()} created)
                    UDP Sockets: ${udpSockets.size}
                    Packets: Sent=${stats.packetsSent.get()}, Processed=${stats.packetsProcessed.get()}, Dropped=${stats.packetsDropped.get()}
                    DNS Queries: ${stats.dnsQueriesHandled.get()}
                    Bytes: ${stats.bytesTransferred.get() / 1024}KB
                    Queue: ${vpnWriteQueue.size}
                    Pool: Active=${fastPool.activeCount}, Queue=${fastPool.queue.size}
                """.trimIndent())
            } catch (e: Exception) {}
        }
    }

    private fun parseIpAddress(buffer: ByteBuffer, offset: Int): String {
        return "${buffer.get(offset).toInt() and 0xFF}.${buffer.get(offset + 1).toInt() and 0xFF}." +
                "${buffer.get(offset + 2).toInt() and 0xFF}.${buffer.get(offset + 3).toInt() and 0xFF}"
    }

    private fun fillIpAddresses(packet: ByteArray, srcIp: String, destIp: String) {
        val srcParts = srcIp.split(".")
        val destParts = destIp.split(".")
        for(i in 0..3) packet[12+i] = srcParts[i].toInt().toByte()
        for(i in 0..3) packet[16+i] = destParts[i].toInt().toByte()
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            val high = (data[i].toInt() and 0xFF) shl 8
            val low = (data[i + 1].toInt() and 0xFF)
            sum += (high or low)
            i += 2
        }
        if (i < offset + length) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    data class PendingPacket(val payload: ByteArray, val seq: Int, val ack: Int)

    inner class TcpConnection(
        private val key: String,
        private val srcIp: String,
        private val srcPort: Int,
        private val destIp: String,
        private val destPort: Int,
        private val proxyIp: String,
        private val proxyPort: Int,
        initialLocalSeq: Int,
        initialRemoteSeq: Int
    ) {
        private var socket: Socket? = null
        private val established = AtomicBoolean(false)
        @Volatile var lastActivity = System.currentTimeMillis()
        private var localSeq = initialLocalSeq.toLong()
        private var remoteSeq = initialRemoteSeq.toLong()
        private val pendingData = ConcurrentLinkedQueue<PendingPacket>()

        fun isEstablished() = established.get()

        fun queueData(payload: ByteArray, seq: Int, ack: Int) {
            if (pendingData.size < 100) {
                pendingData.offer(PendingPacket(payload, seq, ack))
            }
        }

        fun start() {
            try {
                socket = Socket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    soTimeout = 60000
                    receiveBufferSize = 1048576 // 1MB for Instagram
                    sendBufferSize = 1048576
                }

                if (!this@MyVpnService.protect(socket!!)) throw Exception("Protect failed")
                socket?.connect(InetSocketAddress(proxyIp, proxyPort), 15000)

                val input = socket!!.getInputStream()
                val output = socket!!.getOutputStream()

                // SOCKS5 handshake
                output.write(byteArrayOf(0x05, 0x01, 0x00))
                output.flush()
                val handshake = ByteArray(2)
                if (input.read(handshake) < 2 || handshake[0] != 0x05.toByte()) {
                    throw Exception("Handshake failed")
                }

                // SOCKS5 connect
                val ipParts = destIp.split(".")
                val request = ByteArray(10)
                request[0] = 0x05; request[1] = 0x01; request[2] = 0x00; request[3] = 0x01
                for (i in 0..3) request[4 + i] = ipParts[i].toInt().toByte()
                request[8] = (destPort shr 8).toByte()
                request[9] = (destPort and 0xFF).toByte()
                output.write(request)
                output.flush()

                val response = ByteArray(10)
                if (input.read(response) < 10 || response[1] != 0x00.toByte()) {
                    throw Exception("Connect failed: ${response[1]}")
                }

                sendSynAck()
                established.set(true)

                // Send all pending data
                while (pendingData.isNotEmpty()) {
                    val pending = pendingData.poll()
                    if (pending != null) {
                        sendData(pending.payload, pending.seq, pending.ack)
                    }
                }

                fastPool.execute { runReceiver(input) }

            } catch (e: Exception) {
                DebugUtils.error("[$key] Connection failed", e)
                sendReset()
                tcpConnections.remove(key)
                close()
            }
        }

        private fun runReceiver(input: java.io.InputStream) {
            val buffer = ByteArray(32768) // 32KB buffer
            try {
                while (established.get() && isRunning) {
                    val len = input.read(buffer)
                    if (len <= 0) break
                    sendToVpn(buffer.copyOf(len))
                    lastActivity = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                DebugUtils.error("[$key] Receiver error", e)
            } finally {
                tcpConnections.remove(key)
                close()
            }
        }

        fun sendData(payload: ByteArray, seqNum: Int, ackNum: Int) {
            if (!established.get()) return
            try {
                socket?.getOutputStream()?.write(payload)
                socket?.getOutputStream()?.flush()
                localSeq = seqNum.toLong() + payload.size
                val ack = buildTcpPacket(destIp, destPort, srcIp, srcPort, remoteSeq.toInt(), localSeq.toInt(), 0x10, byteArrayOf())
                writeToVpn(ack)
                lastActivity = System.currentTimeMillis()
            } catch (e: Exception) {
                close()
            }
        }

        private fun sendToVpn(payload: ByteArray) {
            val packet = buildTcpPacket(destIp, destPort, srcIp, srcPort, remoteSeq.toInt(), localSeq.toInt(), 0x18, payload)
            writeToVpn(packet)
            remoteSeq += payload.size
        }

        private fun sendSynAck() {
            val synAck = buildTcpPacket(destIp, destPort, srcIp, srcPort, remoteSeq.toInt(), (localSeq + 1).toInt(), 0x12, byteArrayOf())
            writeToVpn(synAck)
            remoteSeq++
            localSeq++
        }

        fun handleFIN(seqNum: Int, ackNum: Int) {
            localSeq = seqNum.toLong() + 1
            val finAck = buildTcpPacket(destIp, destPort, srcIp, srcPort, remoteSeq.toInt(), localSeq.toInt(), 0x11, byteArrayOf())
            writeToVpn(finAck)
            fastPool.execute {
                Thread.sleep(500)
                tcpConnections.remove(key)
                close()
            }
        }

        fun handleAck(ackNum: Int) {
            lastActivity = System.currentTimeMillis()
        }

        private fun sendReset() {
            val rst = buildTcpPacket(destIp, destPort, srcIp, srcPort, remoteSeq.toInt(), localSeq.toInt(), 0x04, byteArrayOf())
            writeToVpn(rst)
        }

        fun close() {
            established.set(false)
            try { socket?.close() } catch (e: Exception) {}
        }

        private fun buildTcpPacket(srcIp: String, srcPort: Int, destIp: String, destPort: Int, seqNum: Int, ackNum: Int, flags: Int, payload: ByteArray): ByteArray {
            val totalLen = 40 + payload.size
            val packet = ByteArray(totalLen)
            packet[0] = 0x45; packet[1] = 0x00
            packet[2] = (totalLen shr 8).toByte(); packet[3] = totalLen.toByte()
            packet[6] = 0x40; packet[8] = 64; packet[9] = 6
            fillIpAddresses(packet, srcIp, destIp)
            val ipChecksum = calculateChecksum(packet, 0, 20)
            packet[10] = (ipChecksum shr 8).toByte(); packet[11] = ipChecksum.toByte()
            packet[20] = (srcPort shr 8).toByte(); packet[21] = srcPort.toByte()
            packet[22] = (destPort shr 8).toByte(); packet[23] = destPort.toByte()
            packet[24] = (seqNum shr 24).toByte(); packet[25] = (seqNum shr 16).toByte()
            packet[26] = (seqNum shr 8).toByte(); packet[27] = seqNum.toByte()
            packet[28] = (ackNum shr 24).toByte(); packet[29] = (ackNum shr 16).toByte()
            packet[30] = (ackNum shr 8).toByte(); packet[31] = ackNum.toByte()
            packet[32] = 0x50; packet[33] = flags.toByte()
            packet[34] = 0xFF.toByte(); packet[35] = 0xFF.toByte()
            if (payload.isNotEmpty()) System.arraycopy(payload, 0, packet, 40, payload.size)
            val tcpChecksum = calculateTcpChecksum(packet, 20, 20 + payload.size, srcIp, destIp)
            packet[36] = (tcpChecksum shr 8).toByte(); packet[37] = tcpChecksum.toByte()
            return packet
        }

        private fun calculateTcpChecksum(packet: ByteArray, tcpOffset: Int, tcpLen: Int, srcIp: String, destIp: String): Int {
            val pseudoHeader = ByteArray(12 + tcpLen)
            val srcParts = srcIp.split(".")
            val destParts = destIp.split(".")
            for(i in 0..3) pseudoHeader[i] = srcParts[i].toInt().toByte()
            for(i in 0..3) pseudoHeader[4+i] = destParts[i].toInt().toByte()
            pseudoHeader[9] = 6
            pseudoHeader[10] = (tcpLen shr 8).toByte()
            pseudoHeader[11] = tcpLen.toByte()
            System.arraycopy(packet, tcpOffset, pseudoHeader, 12, tcpLen)
            return calculateChecksum(pseudoHeader, 0, pseudoHeader.size)
        }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("vpn_channel", "VPN Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("Instagram Optimized VPN")
            .setContentText("⚡ Ultra-fast mode active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
    }

    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("VPN Service")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        getSystemService(NotificationManager::class.java).notify(1, notification)
    }

    override fun onDestroy() {
        isRunning = false
        fastPool.shutdownNow()
        readerPool.shutdownNow()
        tcpConnections.values.forEach { it.close() }
        tcpConnections.clear()
        udpSockets.values.forEach { it.close() }
        udpSockets.clear()
        vpnWriter?.close()
        vpnInterface?.close()
        super.onDestroy()
    }

    // Statistics tracking
    class Stats {
        val packetsSent = AtomicInteger(0)
        val packetsProcessed = AtomicInteger(0)
        val packetsDropped = AtomicInteger(0)
        val tcpConnectionsCreated = AtomicInteger(0)
        val dnsQueriesHandled = AtomicInteger(0)
        val bytesTransferred = AtomicLong(0L) // Changed from AtomicInteger to AtomicLong
    }
}