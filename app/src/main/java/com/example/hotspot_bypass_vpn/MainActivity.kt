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

        // 4. Logic for MODE B: CLIENT (Connecting)
        btnConnect.setOnClickListener {
            val ip = etIp.text.toString()
            val portStr = etPort.text.toString()

            if (ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Please enter IP and Port", Toast.LENGTH_SHORT).show()
            } else {
                prepareVpn(ip, portStr.toInt())
            }
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

    override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
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
}