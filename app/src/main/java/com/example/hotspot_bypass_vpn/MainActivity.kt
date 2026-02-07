package com.example.hotspot_bypass_vpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize UI
        val btnStartHost = findViewById<Button>(R.id.btn_start_host)
        tvHostInfo = findViewById(R.id.tv_host_info)
        tvStatusLog = findViewById(R.id.tv_status_log)

        // 2. Initialize Wi-Fi Direct
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        // 3. Set up Intent Filter (What events we want to listen to)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        // 4. Check Permissions immediately
        checkPermissions()

        // 5. Button Logic
        btnStartHost.setOnClickListener {
            startHost()
        }
    }

    /** Register the BroadcastReceiver when the app is open */
    override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)
    }

    /** Unregister when the app is minimized */
    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    /** Logic to Create the Group (Hotspot) */
    private fun startHost() {
        // 1. Always try to remove any existing group first to clear the "Busy" state
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Group removed, now create a new one
                createNewGroup()
            }

            override fun onFailure(reason: Int) {
                // Usually fails if no group existed, which is fine, proceed anyway
                createNewGroup()
            }
        })
    }

    private fun createNewGroup() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            log("Permission denied.")
            return
        }

        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log("Group creation started...")
            }

            override fun onFailure(reason: Int) {
                log("Failed. Reason: $reason")
                if (reason == 2) {
                    log("Error 2 (BUSY): Ensure Wi-Fi & Location are ON and System Hotspot is OFF.")
                }
            }
        })
    }

    /** Called by BroadcastReceiver when connection info is available */
    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        // This usually tells us if we are the Group Owner and what the IP is
        if (info != null && info.groupFormed && info.isGroupOwner) {
            log("HOST ACTIVE: IP is ${info.groupOwnerAddress.hostAddress}")
            // We don't display password here; updateGroupInfo handles that
        }
    }

    /** Called by BroadcastReceiver when Group info (SSID/Pass) is available */
    fun updateGroupInfo(group: WifiP2pGroup?) {
        if (group != null && group.isGroupOwner) {
            val ssid = group.networkName
            val password = group.passphrase

            // Start the Proxy Server if it's not running
            proxyServer.start()

            val text = "SSID: $ssid\nPASS: $password\nIP: 192.168.49.1\nPORT: 8080"

            tvHostInfo.text = text
            log("Group Created. Proxy running on Port 8080.")
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
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }

    private fun log(message: String) {
        val currentText = tvStatusLog.text.toString()
        tvStatusLog.text = "$message\n$currentText"
    }
}
