# Hotspot Bypass VPN

**A complete solution to bypass carrier hotspot data restrictions** and share your internet connection (including VPN) from an Android device to other phones or laptops. Designed for 100% compatibility with standard web traffic and high-performance gaming (Roblox, Discord, UDP traffic).

---

## 🔗 Official Website

> **[https://nestchao.github.io/Hotspot-Bypass-VPN-Unlimited-Hotspot/](https://nestchao.github.io/Hotspot-Bypass-VPN-Unlimited-Hotspot/)**

Visit our website for:
- 📥 Latest APK downloads
- 💻 Laptop client releases
- 📖 Full documentation and guides
- 🐛 Issue tracking and support

---

## Key Features

### Android Application
- **Share Mode (Host)**: Creates a high-performance Wi-Fi Direct hotspot that bypasses carrier tethering limits by routing traffic through a local SOCKS5 proxy. Persistent background operation even when screen is locked or app is swiped away.
- **Connect Mode (Client)**: Connects to a host phone and tunnels all system traffic through a VPN using tun2socks for robust, gaming-optimized packet handling.

### Laptop Client (Python)
- **Global VPN (TUN) mode**: Captures 100% of Windows traffic (required for games like Roblox)
- **Automatic setup**: Downloads wintun and tun2socks binaries on first run
- **Health monitoring**: Automatic proxy health checks with 2-fail restart logic
- **Console Bridge (ICS)**: Share the VPN connection with consoles/Macs via Windows Mobile Hotspot
- **Lightweight GUI**: Clean CustomTkinter interface

### Debug Tools (laptop_proxy/tools/)
- **diagnostic.py**: Standalone TCP/UDP/DNS probe tool through the SOCKS5 proxy
- **traffic_monitor.py**: Real-time connection logging via ADB logcat + TUN adapter stats + CSV output
- **tests/**: Unit tests for proxy bridge, tun manager health, and routing

---

## How to Use

### 1. Setup the Host (Phone with internet)
1. Open the Android app and go to the **Share (Host)** tab.
2. Choose Wi-Fi Band (5GHz recommended for gaming).
3. Click **START SERVICE**.
4. Grant battery optimization exemption when prompted (keeps service alive).
5. Note the SSID, Password, Proxy IP, and Port from the info card.

### 2. Connect another Phone (Client)
1. Connect the second phone to the host's Wi-Fi network.
2. Open the app on the second phone and go to **Connect (Client)** tab.
3. Enter the Host IP and Port from the host phone.
4. Click **START VPN**.

### 3. Connect a Laptop
1. Connect the laptop to the host phone's Wi-Fi network.
2. Navigate to `laptop_proxy/` on the laptop.
3. Run `setup_venv.bat` (first time only).
4. Run: `venv\Scripts\python.exe main.py`
5. Select **Global VPN**, enter the Phone IP, and click **START**.

---

## Technical Details

| Aspect | Implementation |
|--------|----------------|
| **Bypass method** | SOCKS5 proxy over Wi-Fi Direct hides tethering traffic from carriers |
| **MTU tuning** | Optimized for gaming (MTU 1350) to reduce packet fragmentation |
| **Persistence** | Android Foreground Service + WakeLock + AlarmManager restart logic |
| **Connection stability** | Client socket timeout set to infinite (0); idle connection cleaner at 10 minutes; server socket timeout at 5 minutes; pipe buffer at 64KB |

---

## Building

### Android App
Open in Android Studio and build the APK.

### Laptop App
Navigate to `laptop_proxy/` and run `build_exe.bat`. The standalone `LaptopProxy.exe` will be generated in `dist/`. No Python installation required — the exe auto-requests Administrator rights for VPN tunnel setup.

---

## 📱 Download

**[Get the latest release →](https://nestchao.github.io/Hotspot-Bypass-VPN-Unlimited-Hotspot/)**

---

*For support, issues, or contributions, please visit our [GitHub repository](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot).*
