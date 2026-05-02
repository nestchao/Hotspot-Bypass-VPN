# Hotspot Bypass VPN - User Guide

This guide explains how to use the **Hotspot Bypass VPN** system to share your phone's internet with your PC without hitting "Hotspot Data" limits.

---

## 🚀 Quick Start (Personal Browsing)
Use this mode for web browsing, Netflix, and basic apps.

1.  **On your Android Phone:**
    *   Open the **Hotspot Bypass VPN** app.
    *   Ensure your phone is connected to cellular data.
    *   Turn on your phone's **Mobile Hotspot**.
    *   Click **"Start Proxy"** in the app. Note the **IP** (usually `192.168.49.1`) and **Port** (usually `8080`).

2.  **On your Windows Laptop:**
    *   Connect to your phone's Hotspot Wi-Fi.
    *   Open `LaptopProxy.exe` (or run `main.py`).
    *   Enter the **Phone IP** and **Port** from Step 1.
    *   Click **START**.
    *   *The app will automatically set your Windows System Proxy.*

---

## 🎮 Global VPN Mode (Gaming & Full System)
Use this mode for **Steam, Discord, Call of Duty, or any UDP/Game traffic**.

1.  **Perform the Android steps** from the Quick Start above.
2.  **On your Windows Laptop:**
    *   Right-click `LaptopProxy.exe` and select **Run as Administrator** (Required for network routing).
    *   Ensure **Routing Mode** is set to **GLOBAL VPN**.
    *   Click **START**.
    *   The app will download necessary drivers (first time only) and create a virtual network adapter called `LaptopProxyVPN`.
    *   Once the status says **Connected**, 100% of your laptop's traffic is now routed through your phone's cellular data.

---

## 🛠 Troubleshooting

### "No Internet" on Laptop after starting
*   **Check Admin Rights:** Global VPN mode **must** be run as Administrator.
*   **Check Firewall:** If prompted by Windows Firewall, click "Allow Access".
*   **Reconnect Wi-Fi:** Sometimes Windows gets confused; toggle your Wi-Fi off and on.

### "Unreachable" error in log
*   Ensure the Android app is actually running and you clicked "Start Proxy".
*   Check that your Laptop is connected to the **Phone's Hotspot**, not your home Wi-Fi.

### Closing the App
*   You can close the app at any time. It will automatically clean up your routes and restore your normal internet connection.

---

## 💡 Pro-Tips
*   **Battery:** Using Global VPN mode on the phone uses significant battery. Keep your phone plugged in!
*   **Data Usage:** While this bypasses hotspot limits, it still uses your phone's **Primary Data**. Keep an eye on your monthly allowance.
