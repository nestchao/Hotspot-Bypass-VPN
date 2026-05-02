package com.example.hotspot_bypass_vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only restart if the services were previously running
            // You could persist this preference in SharedPreferences
            val prefs = context.getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE)
            val wasHostRunning = prefs.getBoolean("host_was_running", false)
            val wasVpnRunning = prefs.getBoolean("vpn_was_running", false)

            if (wasHostRunning) {
                val hostIntent = Intent(context, HostService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(hostIntent)
                } else {
                    context.startService(hostIntent)
                }
            }
        }
    }
}