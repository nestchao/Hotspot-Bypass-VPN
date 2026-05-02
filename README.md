# Hotspot Bypass VPN 📱💻

A complete solution to bypass carrier hotspot data restrictions and share your internet connection (including VPN) from an Android device to other phones or laptops. 

This project is specifically designed to work with 100% compatibility, supporting both standard web traffic and **high-performance gaming** (e.g., Roblox, Discord, UDP traffic).

---

## 🌟 Key Features

### 📱 Android Application
*   **Share Mode (Host)**: 
    *   Creates a high-performance Wi-Fi Direct hotspot.
    *   Bypasses carrier "Tethering" limits by routing traffic through a local proxy.
    *   **Persistent**: Stays active in the background, even when the screen is locked or the app is swiped away.
*   **Connect Mode (Client)**:
    *   Connects to a "Host" phone and tunnels all system traffic through a VPN.
    *   Uses `tun2socks` for robust, gaming-optimized packet handling.

### 💻 Laptop Client (Python)
*   **Two Routing Modes**:
    *   **Simple Proxy**: Best for browsers and light web use.
    *   **Global VPN (TUN)**: Captures 100% of Windows traffic. Required for games like **Roblox**.
*   **Automatic Setup**: Downloads necessary drivers (`wintun`, `tun2socks`) automatically.
*   **Lightweight**: Runs in a Python virtual environment with a clean GUI.

---

## 🚀 How to Use

### 1. Setup the Host (The phone with internet)
1.  Open the Android app and go to the **Share (Host)** tab.
2.  Choose your Wi-Fi Band (5GHz is recommended for gaming).
3.  Click **START SERVICE**.
4.  Grant "Battery Optimization" exemption when prompted (this ensures the service doesn't die).
5.  Note the **SSID**, **Password**, **Proxy IP**, and **Port** shown in the info card.

### 2. Connect another Phone (Client)
1.  Connect your second phone to the Wi-Fi network created by the Host.
2.  Open the app on the second phone and go to the **Connect (Client)** tab.
3.  Enter the **Host IP** and **Port** from the Host phone.
4.  Click **START VPN**.

### 3. Connect a Laptop
1.  Connect your laptop to the Wi-Fi network created by the Host phone.
2.  Navigate to the `laptop_proxy` folder on your laptop.
3.  Run `setup_venv.bat` (first time only) to set up the environment.
4.  Run the app: `venv\Scripts\python.exe main.py`.
5.  Select **Global VPN** (for games) or **Simple Proxy**.
6.  Enter the Phone IP and click **START**.

---

## 🛠 Technical Details

*   **Bypass Method**: Uses a SOCKS5 proxy over Wi-Fi Direct to hide tethering traffic from carriers.
*   **MTU Tuning**: Optimized for gaming (MTU 1350) to reduce packet fragmentation over proxies.
*   **Persistence**: Uses Android Foreground Services, WakeLocks, and AlarmManager restart logic for maximum uptime.

## 📦 Installation / Building

### Android App
1.  Open the project in **Android Studio**.
2.  Build the APK and install it on your devices.

### Laptop App (Windows)
#### For Developers:
1.  Navigate to the `laptop_proxy` folder.
2.  Run `build_exe.bat`.
3.  The standalone `LaptopProxy.exe` will be generated in the `dist` folder.

#### For Users:
1.  Just run `LaptopProxy.exe` from the `dist` folder. 
2.  **No Python installation required.**
3.  The app will automatically request Administrator rights to set up the VPN tunnel.
