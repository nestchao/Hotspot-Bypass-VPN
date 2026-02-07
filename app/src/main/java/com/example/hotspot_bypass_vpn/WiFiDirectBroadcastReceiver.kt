package com.example.hotspot_bypass_vpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.app.ActivityCompat

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                // Optional: Notify activity if Wifi P2P is disabled
                if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    // Wi-Fi Direct is OFF
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                // We don't need this for the Host, but if you scan for peers,
                // you would check permissions here too.
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                if (networkInfo?.isConnected == true) {
                    // --- PERMISSION CHECK START ---
                    // We must check if we have permission before asking for IP/Password

                    // 1. Check for Fine Location (Required for all versions)
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                        return // Exit if permission is missing
                    }

                    // 2. Check for Nearby Devices (Required for Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                            != PackageManager.PERMISSION_GRANTED) {
                            return // Exit if permission is missing
                        }
                    }
                    // --- PERMISSION CHECK END ---

                    // If we passed the checks, it is safe to call these:
                    manager.requestConnectionInfo(channel, activity)

                    manager.requestGroupInfo(channel) { group ->
                        // Send the group info (Password/SSID) back to MainActivity
                        activity.updateGroupInfo(group)
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Respond to this device's wifi state changing
            }
        }
    }
}