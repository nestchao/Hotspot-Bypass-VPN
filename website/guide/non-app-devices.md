# Non-App Device Guide

## Overview

Devices that cannot run the Android app (game consoles, Macs, Smart TVs) can still use the bypassed connection through **Windows Internet Connection Sharing (ICS)**.

The setup requires:
1. A phone running **Host mode** (the hotspot)
2. A Windows laptop running the **Windows Client** in VPN mode
3. The target device connects to the laptop's mobile hotspot

---

## Setup Diagram

```
[Phone (Host Mode)]
       |
   Wi-Fi Direct
       |
[Windows Laptop (VPN + Mobile Hotspot)]
       |
   Wi-Fi Hotspot
       |
[PS5 / Xbox / Switch / Mac / TV]
```

---

## Prerequisites

- A phone with the app running in **Host mode**
- A **Windows laptop** (10 or 11, 64-bit) with Wi-Fi
- The **target device** you want to connect

---

## Step-by-Step Setup

### Step 1: Phone Setup

1. Open the app on your phone.
2. Switch to **HOST** tab.
3. Tap **Start Host**.
4. Note the connection details (SSID, password, proxy IP, port).

### Step 2: Laptop Setup

1. Connect your laptop to the phone's Wi-Fi Direct: **DIRECT-HotspotBypass** (password: `87654321`).
2. Launch the **Windows Client** and start the VPN (see [Windows Guide](/guide/windows)).
3. Once connected, go to **Settings → Network & Internet → Mobile hotspot**.
4. Turn on **Mobile hotspot**.

### Step 3: Configure ICS

1. Open **Control Panel → Network and Sharing Center → Change adapter settings**.
2. Right-click the **LaptopProxyVPN** TUN adapter → **Properties**.
3. Go to the **Sharing** tab.
4. Check **Allow other network users to connect through this computer's Internet connection**.
5. In the dropdown, select your mobile hotspot adapter (usually "Local Area Connection* ##" or similar).
6. Click **OK**.

### Step 4: Connect Your Device

1. On your game console / Mac / TV, open Wi-Fi settings.
2. Find and connect to the laptop's mobile hotspot network.
3. The device should now have internet access through the bypassed connection.

---

## Per-Device Notes

### PlayStation 5 / PlayStation 4

- Go to **Settings → Network → Settings → Set Up Internet Connection**
- Select **Use Wi-Fi** → choose the laptop's hotspot
- No proxy configuration needed — ICS handles everything
- Test the connection in **Settings → Network → Connection Status**

### Xbox Series X|S / Xbox One

- Go to **Settings → General → Network settings → Set up wireless network**
- Select the laptop's hotspot
- Go to **Test network connection** to verify

### Nintendo Switch

- Go to **System Settings → Internet → Internet Settings**
- Select the laptop's hotspot
- Test the connection

### MacBook

- Connect to the laptop's hotspot via Wi-Fi
- No additional configuration needed
- Ideal for gaming, browsing, and streaming

### Smart TV

- Open **Network Settings** → **Wi-Fi**
- Select the laptop's hotspot
- Works for streaming apps (Netflix, YouTube, etc.) and web browsing

---

## Performance Tips

- **5GHz band** — Set the phone to 5GHz Wi-Fi Direct band for lower latency
- **Proximity** — Keep the phone and laptop within 10 meters for best signal
- **Wired bridge** — For lowest latency, connect the laptop to the console via Ethernet instead of Wi-Fi hotspot
- **Close other apps** — Reduce bandwidth usage on the phone for better gaming performance

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Device connects but no internet | Check ICS configuration. Restart the mobile hotspot. |
| High latency / lag | Switch phone to 5GHz. Move devices closer together. |
| Hotspot not appearing | Make sure Windows Mobile Hotspot is supported on your laptop. |
| VPN disconnects when hotspot is turned on | Some Wi-Fi adapters can't handle both. Try a USB Wi-Fi adapter for the hotspot. |
| Console can't find the hotspot | Set the hotspot to 2.4GHz band in Windows settings. |
