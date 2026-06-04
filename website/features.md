# Features

## How It Works

Hotspot Bypass VPN uses a clever two-step approach to bypass carrier tethering restrictions:

1. **Wi-Fi Direct** — Instead of using the system's mobile hotspot (which carriers can detect), the app creates a Wi-Fi Direct (P2P) group. This appears to the carrier as regular phone-to-phone communication.
2. **SOCKS5 Proxy** — A high-performance SOCKS5 proxy server runs on the host phone. Client devices connect to the Wi-Fi Direct network and route all traffic through this proxy.

From the carrier's perspective, all traffic originates directly from the host phone's cellular connection — **not** from a tethered device.

---

## Host Mode

Share your phone's cellular data with other devices.

- Creates a Wi-Fi Direct network with SSID `DIRECT-HotspotBypass`
- Custom password protection (default: `87654321`)
- Band selection: 2.4GHz (longer range) or 5GHz (lower latency)
- Built-in SOCKS5 proxy server (TCP + UDP) on port 8080
- One-tap copy of connection info (SSID, password, proxy IP, port)
- Foreground service keeps connection alive with screen off

## Client Mode

Connect to another phone running Host mode and tunnel all your traffic.

- Uses Android VpnService with tun2socks engine for full traffic interception
- All apps, all protocols — everything goes through the proxy
- Dedicated reconnect button to restart the tunnel
- Works great for gaming, streaming, and browsing

## Windows Desktop Client

A full-featured Python application for Windows laptops.

- **TUN Virtual Adapter** — Creates a virtual network card that routes 100% of Windows traffic through the phone's SOCKS5 proxy
- **Automatic Setup** — Downloads tun2socks and wintun dependencies automatically on first run
- **Admin Rights Handling** — Automatically elevates privileges when needed for network configuration
- **GUI Dashboard** — Clean interface with real-time status, logs, and controls
- **Console/Device Bridge** — Can share the VPN connection onward via Windows Mobile Hotspot + ICS

## Non-App Device Support

Connect devices that cannot run the app directly:

| Device | Method |
|--------|--------|
| **PS4 / PS5** | Bridge via Windows ICS |
| **Xbox One / Series X\|S** | Bridge via Windows ICS |
| **Nintendo Switch** | Bridge via Windows ICS |
| **MacBook** | Bridge via Windows ICS (full gaming support) |
| **Smart TVs** | Bridge via Windows ICS |

## Connection Persistence

- **Foreground Service** — Runs as a high-priority foreground service to prevent Android from killing it
- **WakeLock + WifiLock** — Prevents CPU and Wi-Fi sleep
- **AlarmManager Restart** — Auto-restarts the service if it stops unexpectedly
- **Boot Receiver** — Can auto-start on device boot

## Security & Privacy

- **No Root Required** — Uses only standard Android APIs
- **Local Proxy Only** — The SOCKS5 proxy binds to the Wi-Fi Direct interface only
- **MIT License** — Fully open source, inspect the code yourself
