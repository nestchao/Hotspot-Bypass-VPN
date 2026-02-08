package com.example.hotspot_bypass_vpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.concurrent.thread
import android.provider.Settings
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), WifiP2pManager.ConnectionInfoListener {

    private val proxyServer = ProxyServer()

    // Wifi P2P Variables
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private val intentFilter = IntentFilter()

    // UI Elements
    private lateinit var tvHostInfo: TextView
    private lateinit var tvStatusLog: TextView
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val btnDebug = findViewById<Button>(R.id.btn_debug)

        // 1. Initialize UI Elements
        val btnStartHost = findViewById<Button>(R.id.btn_start_host)
        val btnConnect = findViewById<Button>(R.id.btn_connect)
        tvHostInfo = findViewById(R.id.tv_host_info)
        tvStatusLog = findViewById(R.id.tv_status_log)
        etIp = findViewById(R.id.et_host_ip)
        etPort = findViewById(R.id.et_host_port)

        // 2. Initialize Wi-Fi Direct
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        checkPermissions()

        // 3. Logic for MODE A: HOST (Sharing)
        btnStartHost.setOnClickListener {
            startHost()
        }

        // Add this listener
        btnDebug.setOnClickListener {
            runInternetPingTest()
        }

        // 4. Logic for MODE B: CLIENT (Connecting)
        btnConnect.setOnClickListener {
            val ip = etIp.text.toString()
            val portStr = etPort.text.toString()

            if (ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Please enter IP and Port", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val port = portStr.toIntOrNull() ?: 8080

            // Test connectivity first
            testConnectivity(ip, port)

            // Then start VPN
            prepareVpn(ip, port)
        }
    }

    // --- VPN CLIENT LOGIC ---

    private fun prepareVpn(ip: String, port: Int) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Asks user: "Do you trust this app to start a VPN?"
            startActivityForResult(intent, 102)
        } else {
            // Already have permission
            startVpnService(ip, port)
        }
    }

    private fun startVpnService(ip: String, port: Int) {
        val intent = Intent(this, MyVpnService::class.java).apply {
            putExtra("PROXY_IP", ip)
            putExtra("PROXY_PORT", port)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        log("VPN Client Starting... Connecting to $ip:$port")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            val ip = etIp.text.toString()
            val port = etPort.text.toString().toInt()
            startVpnService(ip, port)
        }
    }

    // --- WIFI DIRECT HOST LOGIC ---

    private fun startHost() {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { createNewGroup() }
            override fun onFailure(reason: Int) { createNewGroup() }
        })
    }

    private fun createNewGroup() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            log("Permission Error")
            return
        }
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { log("Creating Group...") }
            override fun onFailure(reason: Int) { log("Error: $reason") }
        })
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info != null && info.groupFormed) {
            val hostIp = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
            log("NETWORK ACTIVE. Host IP: $hostIp")
            // Use this IP in Phone B
        }
    }

    fun updateGroupInfo(group: WifiP2pGroup?) {
        if (group != null && group.isGroupOwner) {
            proxyServer.start() // Start the SOCKS5 server on Phone A
            tvHostInfo.text = "SSID: ${group.networkName}\nPASS: ${group.passphrase}\nIP: 192.168.49.1\nPORT: 8080"
            log("Proxy Running.")
        }
    }

    // --- LIFECYCLE ---

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message") ?: ""
            log(message)
        }
    }

    override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)

        // FIX: Use ContextCompat to support API 24 while using modern security flags
        ContextCompat.registerReceiver(
            this,
            logReceiver,
            IntentFilter("VPN_LOG"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }

        try {
            unregisterReceiver(logReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        proxyServer.stop()
        manager.removeGroup(channel, null)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }

    private fun log(message: String) {
        runOnUiThread {
            val currentText = tvStatusLog.text.toString()
            tvStatusLog.text = "$message\n$currentText"
        }
    }

    private fun testConnectivity(ip: String, port: Int) {
        thread {
            try {
                DebugUtils.log("Starting connectivity test...")

                // Test 1: Basic socket connection
                val canConnect = DebugUtils.testProxyConnection(ip, port)

                runOnUiThread {
                    if (canConnect) {
                        Toast.makeText(this, "✓ Can connect to proxy", Toast.LENGTH_SHORT).show()
                        log("Connectivity test PASSED")
                    } else {
                        Toast.makeText(this, "✗ Cannot connect to proxy", Toast.LENGTH_SHORT).show()
                        log("Connectivity test FAILED")
                    }
                }

                // Test 2: Test internet via proxy (optional)
                if (canConnect) {
                    testInternetViaProxy(ip, port)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    log("Test error: ${e.message}")
                }
            }
        }
    }

    private fun testInternetViaProxy(ip: String, port: Int) {
        thread {
            try {
                DebugUtils.log("Testing internet via proxy...")

                // This is a simple test - you'd need to implement proper SOCKS5 client
                // For now, just log that we're trying
                log("Attempting to route traffic through proxy $ip:$port")

            } catch (e: Exception) {
                log("Proxy routing test error: ${e.message}")
            }
        }
    }

    private fun checkVpnPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                true
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
                false
            }
        } else {
            true
        }
    }

    private fun runInternetPingTest() {
        log("--- Starting Connectivity Test ---")

        thread {
            // Test 1: TCP "Ping" to Google DNS (8.8.8.8)
            // We use a Socket instead of ICMP Ping because SOCKS5 proxies
            // usually don't support ICMP.
            try {
                val host = "8.8.8.8"
                val port = 53
                val timeout = 5000
                val startTime = System.currentTimeMillis()

                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), timeout)
                val endTime = System.currentTimeMillis()
                socket.close()

                log("✓ Internet Reachable: Connected to $host in ${endTime - startTime}ms")
            } catch (e: Exception) {
                log("✗ Internet Unreachable: ${e.message}")
            }

            // Test 2: DNS Resolution
            // This tests if your handleDnsOverTcp function in MyVpnService is working
            try {
                log("Testing DNS resolution (google.com)...")
                val dnsStart = System.currentTimeMillis()
                val address = java.net.InetAddress.getByName("google.com")
                val dnsEnd = System.currentTimeMillis()

                log("✓ DNS Success: google.com -> ${address.hostAddress} (${dnsEnd - dnsStart}ms)")
            } catch (e: Exception) {
                log("✗ DNS Failed: ${e.message}")
                log("Tip: Check if Phone A has Mobile Data enabled.")
            }
        }
    }
}