package com.example.hotspot_bypass_vpn

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
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
import kotlin.concurrent.thread
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

// Dark Theme (Cyber)
val DarkPurpleBg = Color(0xFF120024)
val DarkCardPurple = Color(0xFF1A0033)
val CyberTeal = Color(0xFF03DAC5)
val CyberPurple = Color(0xFF6200EE)

// Light Theme (Clean)
val LightBg = Color(0xFFF5F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightPrimaryTeal = Color(0xFF00796B)
val LightSecondary = Color(0xFF3F51B5)

private val DarkColorScheme = darkColorScheme(
    primary = CyberTeal,
    secondary = CyberPurple,
    background = DarkPurpleBg,
    surface = DarkCardPurple,
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = CyberTeal.copy(alpha = 0.1f),
    onPrimaryContainer = CyberTeal
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryTeal,
    secondary = LightSecondary,
    background = LightBg,
    surface = LightSurface,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    primaryContainer = LightPrimaryTeal.copy(alpha = 0.1f),
    onPrimaryContainer = LightPrimaryTeal
)

@Composable
fun BypassVPNTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        // Add typography here if needed
        content = content
    )
}

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

    private var hostInfoState = mutableStateOf<HostInfo?>(null)
    private var logState = mutableStateListOf<String>()
    private var clientIp = mutableStateOf("192.168.49.1")
    private var clientPort = mutableStateOf("8080")
    private var selectedBand = mutableIntStateOf(1)
    private var selectedTab = mutableIntStateOf(0)

    // --- CRITICAL STATES ---
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

        // Load saved band preference
        selectedBand.intValue = getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE).getInt("selected_band", 1)

        checkPermissions()

        setContent {
            val isDarkMode = isSystemInDarkTheme()  // Call directly, not in wrapper
            val colorScheme = if (isDarkMode) DarkColorScheme else LightColorScheme

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("BYPASS VPN", fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Text("CONNECTED", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                TabRow(
                    selectedTabIndex = selectedTab.intValue,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},                  // Removes the thin gray line for a cleaner look
                    indicator = { tabPositions ->
                        if (selectedTab.intValue < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.intValue]),
                                color = CyberTeal // The moving line under the tab
                            )
                        }
                    }
                ) {
                    // Tab 1: Share (Host)
                    Tab(
                        selected = selectedTab.intValue == 0,
                        onClick = { selectedTab.intValue = 0 },
                        text = {
                            Text(
                                "Share (Host)",
                                // Use Theme primary color instead of hardcoded CyberTeal
                                color = if (selectedTab.intValue == 0) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontWeight = if (selectedTab.intValue == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = if (selectedTab.intValue == 0) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    )

                    // Tab 2: Connect (Client)
                    Tab(
                        selected = selectedTab.intValue == 1,
                        onClick = { selectedTab.intValue = 1 },
                        text = {
                            Text(
                                "Connect (Client)",
                                color = if (selectedTab.intValue == 1) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontWeight = if (selectedTab.intValue == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.VpnLock,
                                contentDescription = null,
                                tint = if (selectedTab.intValue == 1) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
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

                    DebugLogSection()
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
            icon = Icons.Default.CellTower
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isClientRunning.value) {
            ConflictCard(message = "VPN Client is currently active. You cannot share while connected to another proxy.")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            // FIXED: Uses the theme surface color instead of hardcoded CardPurple
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi Band:", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    FilterChip(
                        selected = selectedBand.intValue == 1,
                        onClick = { 
                            selectedBand.intValue = 1 
                            getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE).edit().putInt("selected_band", 1).apply()
                        },
                        label = { Text("2.4 GHz") },
                        enabled = !isHostRunning.value && !isClientRunning.value,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.background, // Adapts to Light or Dark
                            labelColor = MaterialTheme.colorScheme.onSurface      // Adapts to Black or White text
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedBand.intValue == 2,
                        onClick = { 
                            selectedBand.intValue = 2 
                            getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE).edit().putInt("selected_band", 2).apply()
                        },
                        label = { Text("5 GHz") },
                        enabled = !isHostRunning.value && !isClientRunning.value,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.background, // Adapts to Light or Dark
                            labelColor = MaterialTheme.colorScheme.onSurface      // Adapts to Black or White text
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isHostRunning.value) {
                    Button(
                        onClick = { handleStartHost() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,    // Dynamic
                            contentColor = MaterialTheme.colorScheme.background    // Dynamic
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isClientRunning.value
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("START SERVICE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                } else {
                    Button(
                        onClick = { handleStopHost() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB00020), // Standard Red
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("STOP SERVICE", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // Host Info Card (Update for contrast)
        AnimatedVisibility(visible = hostInfoState.value != null) {
            hostInfoState.value?.let { info ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemInDarkTheme()) Color(0xFF1B2E1C) else Color(0xFFE8F5E9)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connection Details (Phone B)", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF4CAF50).copy(alpha = 0.3f))
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
        StatusCard(
            title = "VPN Tunnel",
            isActive = isClientRunning.value,
            activeColor = Color(0xFF2196F3),
            icon = Icons.Default.Security
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isHostRunning.value) {
            ConflictCard(message = "Hotspot Sharing is currently active. You cannot start a VPN while sharing your own connection.")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            // FIXED: Uses the theme surface color
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = clientIp.value,
                    onValueChange = { clientIp.value = it },
                    label = { Text("Host IP Address") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lan, null) },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isClientRunning.value && !isHostRunning.value
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = clientPort.value,
                    onValueChange = { clientPort.value = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Numbers, null) },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isClientRunning.value && !isHostRunning.value
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!isClientRunning.value) {
                    Button(
                        onClick = { handleConnectClient() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isHostRunning.value
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("START VPN", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                } else {
                    Button(
                        onClick = { handleStopClient() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB00020),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("STOP VPN", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        if (isClientRunning.value) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { handleReconnectVPN() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("RECONNECT VPN", fontWeight = FontWeight.Bold)
            }
        }
    }

    private fun handleReconnectVPN() {
        logState.add("Reconnecting VPN...")
        val stopIntent = Intent(this, MyVpnServiceTun2Socks::class.java).apply {
            action = MyVpnServiceTun2Socks.ACTION_STOP
        }
        startService(stopIntent)
        thread {
            Thread.sleep(2000)
            runOnUiThread {
                startVpnService(clientIp.value, clientPort.value.toIntOrNull() ?: 8080)
                logState.add("VPN reconnected")
            }
        }
    }

    @Composable
    fun ConflictCard(message: String) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) Color(0xFF3D0000) else Color(0xFFFFEBEE)
            ),
            border = BorderStroke(1.dp, Color.Red)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                Spacer(modifier = Modifier.width(12.dp))
                Text(message, fontSize = 13.sp, color = Color(0xFFB71C1C), fontWeight = FontWeight.Medium)
            }
        }
    }

    @Composable
    fun StatusCard(title: String, isActive: Boolean, activeColor: Color, icon: ImageVector) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
            label = "alpha"
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                // Uses theme surface color
                containerColor = if (isActive) activeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (isActive) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) Color.White else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface // Adapts to Black/White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isActive) activeColor.copy(alpha = alpha) else Color.Gray))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isActive) "SERVICE ACTIVE" else "SERVICE READY",
                            color = if (isActive) activeColor else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun DebugLogSection() {
        var showLogs by remember { mutableStateOf(false) }
        val logBgColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFE0E0E0)
        val logTextColor = if (isSystemInDarkTheme()) Color(0xFF00E676) else Color(0xFF1B5E20)

        Column(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { showLogs = !showLogs }) {
                Icon(if (showLogs) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                Text(if (showLogs) "Hide Debug Logs" else "Show Debug Logs")
            }
            AnimatedVisibility(visible = showLogs) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(logBgColor, RoundedCornerShape(12.dp)) // Adaptive background
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        logState.asReversed().forEach { logLine ->
                            Text(text = "➜ $logLine", fontSize = 11.sp, color = logTextColor) // Adaptive text
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        val clipboardManager = LocalClipboardManager.current
        Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label.uppercase(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(
                    value,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface // Dynamic color
                )
            }
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(value))
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape) // Subtle contrast
                    .size(36.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    private fun handleStartHost() {
        if (checkHardwareStatus()) {
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations()
                return
            }
            val intent = Intent(this, HostService::class.java).apply { putExtra("WIFI_BAND", selectedBand.intValue) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isHostRunning.value = true
            logState.add("Starting Host Service...")
        }
        saveServiceState(hostRunning = true)
    }

    private fun handleStopHost() {
        val intent = Intent(this, HostService::class.java).apply { action = "STOP" }
        startService(intent)
        hostInfoState.value = null
        isHostRunning.value = false
        logState.add("Host Service Stopped.")
        saveServiceState(hostRunning = false)
    }

    private fun handleConnectClient() {
        if (checkHardwareStatus()) {
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations()
                return
            }
            if (getPrivateDnsMode() == "hostname") {
                Toast.makeText(this, "Disable Private DNS first!", Toast.LENGTH_LONG).show()
                navigateToPrivateDnsSettings()
                return
            }
            prepareVpn(clientIp.value, clientPort.value.toIntOrNull() ?: 8080)
        }
    }

    private fun handleStopClient() {
        val intent = Intent(this, MyVpnServiceTun2Socks::class.java).apply { action = MyVpnServiceTun2Socks.ACTION_STOP }
        startService(intent)
        isClientRunning.value = false
        logState.add("VPN Client Stopped.")

        // FIX: Use vpnRunning = false
        saveServiceState(vpnRunning = false)
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
        logState.add("VPN Client Starting...")

        // FIX: Use vpnRunning instead of hostRunning
        saveServiceState(vpnRunning = true)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            startVpnService(clientIp.value, clientPort.value.toIntOrNull() ?: 8080)
        }
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

        // SYNC STATES ON RESUME
        syncServiceStates()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        try { unregisterReceiver(logReceiver) } catch (e: Exception) {}
    }

    private fun getPrivateDnsMode(): String {
        return try {
            Settings.Global.getString(contentResolver, "private_dns_mode") ?: "off"
        } catch (e: Exception) { "unknown" }
    }

    private fun navigateToPrivateDnsSettings() {
        try {
            startActivity(Intent("android.settings.DNS_SETTINGS"))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun syncServiceStates() {
        // 1. Sync VPN Client state (Static variable check)
        isClientRunning.value = MyVpnServiceTun2Socks.isServiceRunning

        // 2. Sync Host state with Permission Check
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            PackageManager.PERMISSION_GRANTED
        }

        if (fineLocation == PackageManager.PERMISSION_GRANTED && nearbyDevices == PackageManager.PERMISSION_GRANTED) {
            try {
                manager.requestGroupInfo(channel) { group ->
                    if (group != null) {
                        isHostRunning.value = true
                        updateGroupInfo(group)
                    } else {
                        // Only set false if we're sure no group exists
                        if (isHostRunning.value) isHostRunning.value = false
                    }
                }
                manager.requestConnectionInfo(channel, this)
            } catch (e: SecurityException) {
                logState.add("Sync error: Permission denied")
            }
        }
    }

    private fun saveServiceState(hostRunning: Boolean? = null, vpnRunning: Boolean? = null) {
        val prefs = getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            hostRunning?.let { putBoolean("host_was_running", it) }
            vpnRunning?.let { putBoolean("vpn_was_running", it) }
            apply()
        }
    }
}