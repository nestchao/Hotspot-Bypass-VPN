package com.example.hotspot_bypass_vpn

import android.Manifest
import android.content.*
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.Uri
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext

// --- DATA CLASS MUST BE DEFINED ---
data class HostInfo(
    val ssid: String,
    val pass: String,
    val ip: String,
    val port: String
)

class MainActivity : ComponentActivity(), WifiP2pManager.ConnectionInfoListener {

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private val intentFilter = IntentFilter()

    // --- STATES ---
    private var hostInfoState = mutableStateOf<HostInfo?>(null)
    private var logState = mutableStateListOf<String>()
    private var clientIp = mutableStateOf("192.168.49.1")
    private var clientPort = mutableStateOf("8080")
    private var selectedBand = mutableIntStateOf(1)
    private var selectedTab = mutableIntStateOf(0)
    private var isHostRunning = mutableStateOf(false)
    private var isClientRunning = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        intentFilter.apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        checkPermissions()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6200EE),
                    secondary = Color(0xFF03DAC5)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F9FA)) {
                    MainScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Bypass Hotspot VPN", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                TabRow(selectedTabIndex = selectedTab.intValue) {
                    Tab(
                        selected = selectedTab.intValue == 0,
                        onClick = { selectedTab.intValue = 0 },
                        text = { Text("Share (Host)") },
                        icon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab.intValue == 1,
                        onClick = { selectedTab.intValue = 1 },
                        text = { Text("Connect (Client)") },
                        icon = { Icon(Icons.Default.SettingsInputAntenna, contentDescription = null) }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedTab.intValue == 0) {
                        HostModeView()
                    } else {
                        ClientModeView()
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    var showLogs by remember { mutableStateOf(false) }
                    TextButton(onClick = { showLogs = !showLogs }) {
                        Text(if (showLogs) "Hide Debug Logs" else "Show Debug Logs")
                    }

                    AnimatedVisibility(visible = showLogs) {
                        LogView()
                    }
                }
            }
        }
    }

    @Composable
    fun HostModeView() {
        StatusCard(
            title = "Hotspot Sharing",
            isActive = isHostRunning.value,
            activeColor = Color(0xFF4CAF50),
            icon = Icons.Default.CellTower // Changed from CloudUpload
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sharing Preferences", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi Band:", modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = selectedBand.intValue == 1,
                        onClick = { selectedBand.intValue = 1 },
                        label = { Text("2.4 GHz") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedBand.intValue == 2,
                        onClick = { selectedBand.intValue = 2 },
                        label = { Text("5 GHz") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isHostRunning.value) {
                    Button(
                        onClick = { handleStartHost() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("START SHARING", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { handleStopHost() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("STOP SHARING", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hostInfoState.value != null,
            enter = expandVertically() + fadeIn()
        ) {
            hostInfoState.value?.let { info ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Client Setup Info", fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                        Text("Enter these on Phone B:", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        InfoRow(label = "SSID", value = info.ssid)
                        InfoRow(label = "Password", value = info.pass)
                        InfoRow(label = "Proxy IP", value = info.ip)
                        InfoRow(label = "Proxy Port", value = info.port)
                    }
                }
            }
        }
    }

    @Composable
    fun ClientModeView() {
//        PrivateDnsWarning()

        StatusCard(
            title = "VPN Tunnel",
            isActive = isClientRunning.value,
            activeColor = Color(0xFF2196F3),
            icon = Icons.Default.VpnLock // Changed from Security
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connection Details", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = clientIp.value,
                    onValueChange = { clientIp.value = it },
                    label = { Text("Host IP Address") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = clientPort.value,
                    onValueChange = { clientPort.value = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!isClientRunning.value) {
                    Button(
                        onClick = { handleConnectClient() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("START VPN", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { handleStopClient() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("STOP VPN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // In MainActivity.kt

    @Composable
    fun PrivateDnsWarning() {
        val context = LocalContext.current
        val dnsMode = remember { mutableStateOf(getPrivateDnsMode()) }

        // If the mode is "hostname", it means the user set a specific DNS (like dns.google)
        if (dnsMode.value == "hostname") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                border = BorderStroke(1.dp, Color.Red)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Private DNS Conflict",
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Manual Private DNS detected. This usually blocks hotspot bypass. " +
                                "Please set it to 'Automatic' or 'Off'.",
                        fontSize = 13.sp,
                        color = Color.Black
                    )

                    // ADDED: Small helper text for manual path
                    Text(
                        "Path: Settings > Network > Private DNS",
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        fontFamily = FontFamily.Monospace
                    )

                    TextButton(
                        onClick = {
                            // Intent Ladder: Try most specific first, then fall back
                            val intentSpecific = Intent("android.settings.DNS_SETTINGS")
                            val intentNetwork = Intent(Settings.ACTION_WIRELESS_SETTINGS) // "Network & Internet"
                            val intentSettings = Intent(Settings.ACTION_SETTINGS) // General Settings

                            try {
                                context.startActivity(intentSpecific)
                            } catch (e: Exception) {
                                try {
                                    // Most devices land Private DNS inside Wireless/Network settings
                                    context.startActivity(intentNetwork)
                                    Toast.makeText(context, "Look for 'Private DNS' in Advanced settings", Toast.LENGTH_LONG).show()
                                } catch (e2: Exception) {
                                    context.startActivity(intentSettings)
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("FIX IN SETTINGS")
                    }
                }
            }
        }
    }

    @Composable
    fun StatusCard(title: String, isActive: Boolean, activeColor: Color, icon: ImageVector) {
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = ""
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isActive) activeColor.copy(alpha = 0.1f) else Color.White),
            border = if (isActive) BorderStroke(2.dp, activeColor) else null
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (isActive) activeColor else Color.LightGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isActive) activeColor.copy(alpha = alpha) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isActive) "ACTIVE" else "IDLE",
                            color = if (isActive) activeColor else Color.Gray,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        val clipboardManager = LocalClipboardManager.current
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = Color.Gray)
                Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(value))
                Toast.makeText(this@MainActivity, "$label copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    @Composable
    fun LogView() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF212121), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                logState.asReversed().forEach { logLine ->
                    Text(
                        text = "> $logLine",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF00)
                    )
                }
            }
        }
    }

    private fun handleStartHost() {
        if (checkHardwareStatus()) {
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations()
                return
            }
            val intent = Intent(this, HostService::class.java).apply {
                putExtra("WIFI_BAND", selectedBand.intValue)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isHostRunning.value = true
        }
    }

    private fun handleStopHost() {
        val intent = Intent(this, HostService::class.java).apply { action = "STOP" }
        startService(intent)
        hostInfoState.value = null
        isHostRunning.value = false
    }

    private fun handleConnectClient() {
        if (checkHardwareStatus()) {
            // 1. Check Battery
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations()
                return
            }

            // 2. NEW: Check Private DNS
            if (getPrivateDnsMode() == "hostname") {
                // Log it so you can see it in debug
                logState.add("Error: Manual Private DNS detected. Redirecting...")

                // Show a Toast so the user knows WHY they are being moved to settings
                Toast.makeText(
                    this,
                    "Private DNS must be 'Automatic' or 'Off' for VPN to work.",
                    Toast.LENGTH_LONG
                ).show()

                // Navigate
                navigateToPrivateDnsSettings()
                return // <--- CRITICAL: Exit here so the VPN doesn't try to start
            }

            // 3. Proceed with VPN if everything is okay
            val ip = clientIp.value
            val port = clientPort.value.toIntOrNull() ?: 8080
            prepareVpn(ip, port)
        }
    }

    private fun handleStopClient() {
        val intent = Intent(this, MyVpnServiceTun2Socks::class.java).apply {
            action = MyVpnServiceTun2Socks.ACTION_STOP
        }
        startService(intent)
        isClientRunning.value = false
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info != null && info.groupFormed) {
            isHostRunning.value = true
        }
    }

    fun updateGroupInfo(group: WifiP2pGroup?) {
        if (group != null && group.isGroupOwner) {
            hostInfoState.value = HostInfo(
                ssid = group.networkName ?: "Unknown",
                pass = group.passphrase ?: "N/A",
                ip = "192.168.49.1",
                port = "8080"
            )
            isHostRunning.value = true
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }

    private fun checkHardwareStatus(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        if (!wifiManager.isWifiEnabled) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            return false
        }
        return true
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

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
        isClientRunning.value = true
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("message")?.let { logState.add(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)
        ContextCompat.registerReceiver(this, logReceiver, IntentFilter("VPN_LOG"), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        try { unregisterReceiver(logReceiver) } catch (e: Exception) {}
    }

    private fun getPrivateDnsMode(): String {
        return try {
            Settings.Global.getString(contentResolver, "private_dns_mode") ?: "off"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun navigateToPrivateDnsSettings() {
        val intentSpecific = Intent("android.settings.DNS_SETTINGS")
        val intentNetwork = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        val intentSettings = Intent(Settings.ACTION_SETTINGS)

        try {
            startActivity(intentSpecific)
        } catch (e: Exception) {
            try {
                startActivity(intentNetwork)
                Toast.makeText(this, "Go to 'Advanced' -> 'Private DNS'", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                startActivity(intentSettings)
            }
        }
    }
}