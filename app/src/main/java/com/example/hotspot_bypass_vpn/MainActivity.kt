package com.example.hotspot_bypass_vpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.VpnService
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), WifiP2pManager.ConnectionInfoListener {

    private val proxyServer = ProxyServer()
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private val intentFilter = IntentFilter()

    private lateinit var tvHostInfo: TextView
    private lateinit var tvStatusLog: TextView
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Initialization
        val btnStartHost = findViewById<Button>(R.id.btn_start_host)
        val btnStopHost = findViewById<Button>(R.id.btn_stop_host)
        val btnConnect = findViewById<Button>(R.id.btn_connect)
        val btnStopClient = findViewById<Button>(R.id.btn_stop_client)
        val btnDebug = findViewById<Button>(R.id.btn_debug)
        tvHostInfo = findViewById(R.id.tv_host_info)
        tvStatusLog = findViewById(R.id.tv_status_log)
        etIp = findViewById(R.id.et_host_ip)
        etPort = findViewById(R.id.et_host_port)

        // Wi-Fi Direct Initialization
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        checkPermissions()

        // --- BUTTON LOGIC ---

        btnStartHost.setOnClickListener {
            if (checkHardwareStatus()) {
                startHost()
            }
        }

        btnStopHost.setOnClickListener {
            stopHost()
        }

        btnConnect.setOnClickListener {
            if (checkHardwareStatus()) {
                val ip = etIp.text.toString()
                val portStr = etPort.text.toString()

                if (ip.isEmpty() || portStr.isEmpty()) {
                    Toast.makeText(this, "Please enter IP and Port", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val port = portStr.toIntOrNull() ?: 8080
                testConnectivity(ip, port)
                prepareVpn(ip, port)
            }
        }

        btnStopClient.setOnClickListener {
            log("Stopping VPN Service...")
            val intent = Intent(this, MyVpnServiceTun2Socks::class.java)
            stopService(intent)
        }

        btnDebug.setOnClickListener {
            runInternetPingTest()
        }
    }

    // --- HARDWARE CHECKS & DIALOGS ---

    private fun checkHardwareStatus(): Boolean {
        if (!isWifiEnabled()) {
            showEnableServiceDialog(
                "Wi-Fi Required",
                "Wi-Fi must be ON for this app to function.",
                Settings.ACTION_WIFI_SETTINGS
            )
            return false
        }
        if (!isLocationEnabled()) {
            showEnableServiceDialog(
                "Location Required",
                "System Location (GPS) must be ON for Android to allow Wi-Fi Direct/Network discovery.",
                Settings.ACTION_LOCATION_SOURCE_SETTINGS
            )
            return false
        }
        return true
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun showEnableServiceDialog(title: String, message: String, settingsAction: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                startActivity(Intent(settingsAction))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- HOST LOGIC ---

    private fun startHost() {
        val intent = Intent(this, HostService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        log("Host Service starting in dedicated process...")
    }

    private fun createNewGroup() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            log("Permission Error: Missing Location")
            return
        }
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { log("Creating Wi-Fi Group...") }
            override fun onFailure(reason: Int) { log("Error creating group: $reason") }
        })
    }

    private fun stopHost() {
        val intent = Intent(this, HostService::class.java).apply { action = "STOP" }
        startService(intent)
        log("Host Service stopping...")
    }
    // --- CLIENT LOGIC ---

    private fun prepareVpn(ip: String, port: Int) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 102)
        } else {
            startVpnService(ip, port)
        }
    }

    private fun startVpnService(ip: String, port: Int) {
        val intent = Intent(this, MyVpnServiceTun2Socks::class.java).apply {
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

    private fun stopVpnService() {
        log("Stopping VPN Service...")
        val intent = Intent(this, MyVpnServiceTun2Socks::class.java)
        stopService(intent)
        log("✓ VPN stop signal sent")
    }

    // --- INTERFACE & LIFECYCLE ---

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info != null && info.groupFormed) {
            val hostIp = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
            log("NETWORK ACTIVE. Host IP: $hostIp")
        }
    }

    fun updateGroupInfo(group: WifiP2pGroup?) {
        if (group != null && group.isGroupOwner) {
            // We don't call proxyServer.start() here anymore,
            // the HostService handles it.
            tvHostInfo.text = "SSID: ${group.networkName}\nPASS: ${group.passphrase}\nIP: 192.168.49.1\nPORT: 8080"
            log("Proxy Service running in background")
        }
    }

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
        ContextCompat.registerReceiver(
            this,
            logReceiver,
            IntentFilter("VPN_LOG"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        try { unregisterReceiver(logReceiver) } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        proxyServer.stop()
        manager.removeGroup(channel, null)
    }

    // --- HELPERS ---

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
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
                val canConnect = DebugUtils.testProxyConnection(ip, port)
                runOnUiThread {
                    log(if (canConnect) "✓ Connectivity test PASSED" else "✗ Connectivity test FAILED")
                }
            } catch (e: Exception) {
                runOnUiThread { log("Test error: ${e.message}") }
            }
        }
    }

    private fun runInternetPingTest() {
        log("--- Starting Connectivity Test ---")
        thread {
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 5000)
                socket.close()
                log("✓ Internet Reachable (8.8.8.8)")
            } catch (e: Exception) {
                log("✗ Internet Unreachable: ${e.message}")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            val ip = etIp.text.toString()
            val port = etPort.text.toString().toIntOrNull() ?: 8080
            startVpnService(ip, port)
        }
    }
}