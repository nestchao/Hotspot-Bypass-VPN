# Frequently Asked Questions

## General

### What makes this different from a normal mobile hotspot?

Normal mobile hotspots use Android's built-in tethering API, which carriers can detect and throttle/block. This app uses **Wi-Fi Direct** (a peer-to-peer protocol) to create a direct connection between devices, which carriers see as regular phone-to-phone communication — not tethering.

### Do I need root access?

No. The app uses only standard Android APIs — **VpnService** and **Wi-Fi Direct (WifiP2pManager)**. No root access is required on either the host or client phone.

### Does the app work on any Android phone?

Any phone running **Android 7.0 (API 24)** or higher with **Wi-Fi Direct** support should work. Most modern Android phones support Wi-Fi Direct.

### Will this work with my carrier?

The app is designed to bypass carrier hotspot detection, but we cannot guarantee it works with every carrier in every country. Results may vary depending on your carrier's detection methods.

### Is this legal?

In most countries, using a VPN and Wi-Fi Direct to share your connection is legal. However, tethering restrictions in your carrier's terms of service may prohibit this. Check your carrier's terms before using.

---

## Setup & Usage

### Why does the app need location permission?

Android requires location permission for Wi-Fi Direct discovery and connection. The app does not use your actual GPS location — it only uses the permission because the Android API demands it.

### What is "Private DNS" and why should I disable it?

Private DNS (DNS over TLS) can interfere with the SOCKS5 proxy. If you have a custom Private DNS provider set (like `dns.google`), the app will show a warning. Set it to **Off** or **Automatic** in your phone's network settings.

### Can I use both Host and Client mode at the same time?

No. The app prevents running both modes simultaneously to avoid conflicts. You'll see a warning if you try to switch modes while one is active.

### Why does the service stop when I close the app?

The app uses a **Foreground Service** to keep running, but some phones (especially Xiaomi, Huawei, OnePlus) have aggressive battery management that can kill foreground services. Make sure to:
- Grant **Ignore battery optimization** permission
- Add the app to your phone's **protected apps** or **auto-start** list
- Lock the app in your recent apps list

### How far apart can the devices be?

Wi-Fi Direct typically works within **30-50 meters** (100-165 feet) with line of sight. Walls and obstacles reduce the range.

---

## Performance

### What internet speeds should I expect?

Speeds depend on:
- Your phone's cellular connection speed
- Wi-Fi Direct bandwidth (typically 30-50 Mbps real-world)
- Distance and obstacles between devices
- Band selection (5GHz is faster, 2.4GHz has better range)

Real-world: **10-40 Mbps** is typical for most setups.

### Is this good for gaming?

Yes. The app is specifically designed with gaming in mind:
- **5GHz band** option for lower latency
- **UDP support** in the SOCKS5 proxy for real-time game traffic
- **Windows TUN adapter** for full game compatibility
- **ICS bridge** for consoles (PS5, Xbox, Switch)

Typical ping: **30-60ms** added on top of the phone's cellular latency.

### Will streaming work (Netflix, YouTube, etc.)?

Yes. The SOCKS5 proxy handles TCP traffic (streaming) and UDP traffic (gaming/chat). Streaming services work normally.

---

## Windows Client

### Why does the Windows client need Administrator privileges?

The app needs admin rights to create and configure the TUN virtual adapter and modify system routing tables. This is normal for VPN software on Windows.

### Can I use the Windows client on a desktop PC?

Yes, as long as it has Wi-Fi (to connect to the phone's Wi-Fi Direct network). Desktop PCs without Wi-Fi can use a USB Wi-Fi adapter.

### Does the Windows client work on Mac?

No, the desktop client is Windows-only. However, Macs can connect through the **ICS bridge method** — connect a Windows laptop via VPN, then share the connection to the Mac via hotspot.

---

## Consoles & TVs

### Can I connect my PS5 directly to the phone?

Not directly. The PS5 cannot run the Android app or connect to SOCKS5 proxies natively. You need a **Windows laptop as a bridge** (see the [Non-App Device Guide](/guide/non-app-devices)).

### Does bridge mode add latency?

Yes, but usually only **5-15ms** extra. For most games, this is perfectly acceptable.

### Do I need a separate Wi-Fi adapter for the laptop?

Some laptops can run both VPN and mobile hotspot on the same Wi-Fi adapter, but not all. If you have issues, a **USB Wi-Fi adapter** (as low as $10-15) dedicated to the hotspot solves the problem.
