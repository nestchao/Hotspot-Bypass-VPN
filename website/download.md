# Download

## Android App

Get the latest version of the Hotspot Bypass VPN Android app.

| Item | Link |
|------|------|
| APK Download | [Hotspot_Bypass_VPN.apk](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v3.0.0/Hotspot_Bypass_VPN.apk) |
| Google Play | *Coming soon* |
| Source Code | [GitHub Repository](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot) |

### Requirements

- Android 7.0 (API 24) or higher
- No root access required
- Wi-Fi Direct capable device (most modern Android phones)

## Windows Client

For Windows laptops, use the dedicated desktop client to connect to the phone's proxy.

| Item | Link |
|------|------|
| Standalone EXE | [Hotspot_Bypass_VPN_Windows.exe](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v3.0.0/Hotspot_Bypass_VPN_Windows.exe) |
| Source Code | [laptop_proxy/](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/tree/master/laptop_proxy) |

### Requirements

- Windows 10 or 11 (64-bit)
- Administrator privileges (required for TUN virtual adapter)

## Building from Source

### Android App

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot
./gradlew assembleDebug
```

### Windows Client

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot/laptop_proxy
pip install -r requirements.txt
python main.py
```

To build a standalone executable:

```bash
.\build_exe.bat
```

---

## Changelog

### Version 3.0.0 (June 2026)

- Enhanced service reliability and persistence
- Optimized networking performance for gaming
- Improved Windows client stability
- New Phone Proxy Manager for Windows
- Application logo and branding updates

### Version 2.0.0 (May 2026)

- Optimized networking performance for low-latency gaming
- Improved service persistence and background reliability
- Enhanced Windows client stability and features
- Added application logo and branding
