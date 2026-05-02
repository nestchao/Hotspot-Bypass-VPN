# Sharing with Non-App Devices (PS4, Xbox, Mac, etc.) 🎮💻🍎

This guide explains how to share your unlimited hotspot connection with devices that **cannot** install the Hotspot Bypass app, such as game consoles, Apple computers, or smart TVs.

---

## The "Middleman" Strategy

Since consoles and MacBooks cannot run the custom bypass logic directly, the most reliable method is to use a **Windows Laptop** as a bridge.

### How it Works:
`Phone (Bypass Host)` ➜ `Windows Laptop (Global VPN)` ➜ `Non-App Device`

### Step-by-Step Instructions:

#### 1. Prepare the Laptop Bridge
1.  Connect your **Windows Laptop** to your phone's Wi-Fi Direct hotspot.
2.  Launch the **LaptopProxy.exe** (Global VPN mode) and click **START**.
3.  Verify the laptop has full internet access.

#### 2. Enable Windows Mobile Hotspot
1.  On your Windows Laptop, go to **Settings > Network & internet > Mobile hotspot**.
2.  Turn **Mobile hotspot** to **ON**.
3.  Click **Edit** to set a Network name (SSID) and Password that your console/Mac will connect to.
4.  In the "Share my internet connection from" dropdown, ensure it is set to **Wi-Fi**.

#### 3. Share the VPN Tunnel (CRITICAL)
Windows does not always share the VPN connection automatically. You must tell Windows to route the hotspot through the `LaptopProxy` tunnel:
1.  Go to **Control Panel > Network and Internet > Network and Sharing Center**.
2.  Click **Change adapter settings** on the left.
3.  Find the adapter named **LaptopProxyVPN** (or the one created by `wintun`).
4.  Right-click it and select **Properties**.
5.  Go to the **Sharing** tab.
6.  Check **"Allow other network users to connect through this computer's Internet connection"**.
7.  In the dropdown, select the connection name that corresponds to your **Windows Mobile Hotspot** (usually named something like `Local Area Connection* X`).
8.  Click **OK**.

#### 4. Connect your Device
1.  On your **PS4, Xbox, or Mac**, search for Wi-Fi networks.
2.  Connect to the **Windows Laptop's** hotspot.
3.  Run a network test. Your device is now using the bypassed unlimited connection!

---

## Alternative Method: Manual Proxy (Mac/PC Only)
If you only need internet for a **MacBook** and don't want to use a bridge:
1.  Connect the Mac to the phone's Wi-Fi.
2.  Go to **System Settings > Network > Wi-Fi > Details > Proxies**.
3.  Enable **SOCKS Proxy**.
4.  Enter the **Proxy IP** and **Port** shown on your phone's app (e.g., `192.168.49.1` and `8080`).
5.  Click **OK**.
    *   *Note: This method only works for web traffic and will NOT work for most games on Mac.*

---

## Summary of Device Support
| Device | Method | Gaming Support? |
| :--- | :--- | :--- |
| **MacBook** | Windows Bridge / Manual Proxy | Yes (Bridge) / No (Proxy) |
| **PS4 / PS5** | Windows Bridge | Yes |
| **Xbox One / Series** | Windows Bridge | Yes |
| **Nintendo Switch** | Windows Bridge | Yes |
| **Smart TV** | Windows Bridge | Yes |
