import tkinter as tk
from tkinter import ttk, messagebox
import threading
import socket
import struct
import select
import sys
import os
import time
import subprocess
import urllib.request
import zipfile
import io
import platform
# import symbols

# --- Windows specific imports ---
IS_WINDOWS = sys.platform == "win32"
if IS_WINDOWS:
    import winreg
    import ctypes

# Constants for binary management
CONFIG_DIR = os.path.join(os.environ.get("APPDATA", "."), "LaptopProxy")
BIN_DIR = os.path.join(CONFIG_DIR, "bin")

class TunManager:
    """
    Manages tun2socks & wintun.dll. 
    Creates a Virtual Network Adapter to capture 100% of Windows traffic.
    """
    def __init__(self, phone_ip, phone_port, local_port, log_fn):
        self.phone_ip = phone_ip
        self.phone_port = phone_port
        self.local_port = local_port
        self.log = log_fn
        self.process = None

    def _check_dependencies(self):
        os.makedirs(BIN_DIR, exist_ok=True)
        t2s_path = os.path.join(BIN_DIR, "tun2socks.exe")
        wt_path  = os.path.join(BIN_DIR, "wintun.dll")

        if os.path.exists(t2s_path) and os.path.exists(wt_path):
            return t2s_path

        # Determine architecture
        arch = platform.machine().lower()
        if 'arm' in arch or 'aarch' in arch:
            t2s_url = "https://github.com/xjasonlyu/tun2socks/releases/download/v2.5.2/tun2socks-windows-arm64.zip"
            wt_arch = "arm64"
        else:
            t2s_url = "https://github.com/xjasonlyu/tun2socks/releases/download/v2.5.2/tun2socks-windows-amd64.zip"
            wt_arch = "amd64"

        self.log("Downloading VPN dependencies through bridge...")
        # Start a temporary bridge for downloading
        temp_bridge = HttpSocksBridge(self.local_port, self.phone_ip, self.phone_port, self.log)
        temp_bridge.start()
        time.sleep(1.5)

        proxy_handler = urllib.request.ProxyHandler({
            'http': f'http://127.0.0.1:{self.local_port}',
            'https': f'http://127.0.0.1:{self.local_port}'
        })
        opener = urllib.request.build_opener(proxy_handler)
        urllib.request.install_opener(opener)

        try:
            if not os.path.exists(t2s_path):
                self.log("Downloading tun2socks...")
                req = urllib.request.urlopen(t2s_url, timeout=30)
                with zipfile.ZipFile(io.BytesIO(req.read())) as z:
                    for name in z.namelist():
                        if name.endswith(".exe"):
                            with open(t2s_path, "wb") as f:
                                f.write(z.read(name))
                            break

            if not os.path.exists(wt_path):
                self.log("Downloading wintun.dll...")
                req = urllib.request.urlopen("https://www.wintun.net/builds/wintun-0.14.1.zip", timeout=30)
                with zipfile.ZipFile(io.BytesIO(req.read())) as z:
                    wt_file = f"wintun/bin/{wt_arch}/wintun.dll"
                    with open(wt_path, "wb") as f:
                        f.write(z.read(wt_file))
            self.log("Dependencies downloaded successfully.")
        except Exception as e:
            self.log(f"Download failed: {e}")
            raise e
        finally:
            urllib.request.install_opener(urllib.request.build_opener())
            temp_bridge.stop()
            time.sleep(0.5)

        return t2s_path

    def start(self):
        t2s_exe = self._check_dependencies()
        
        self.log("Cleaning up old routes...")
        subprocess.run(["route", "delete", "0.0.0.0", "mask", "128.0.0.0"], capture_output=True)
        subprocess.run(["route", "delete", "128.0.0.0", "mask", "128.0.0.0"], capture_output=True)

        self.log("Starting VPN Interface...")
        cmd =[
            t2s_exe,
            "-device", "tun://LaptopProxyVPN",
            "-proxy", f"socks5://{self.phone_ip}:{self.phone_port}",
            "-loglevel", "warning"
        ]

        # Hide window on Windows
        cflags = 0x08000000 if IS_WINDOWS else 0
        self.process = subprocess.Popen(cmd, cwd=BIN_DIR, creationflags=cflags)

        self.log("Waiting for network adapter...")
        adapter_found = False
        for _ in range(15):
            res = subprocess.run(["netsh", "interface", "ipv4", "show", "interfaces"], capture_output=True, text=True)
            if "LaptopProxyVPN" in res.stdout:
                adapter_found = True
                break
            time.sleep(1)
            
        if not adapter_found:
            self.stop()
            raise Exception("Failed to create Virtual Adapter.")

        time.sleep(2.0) 

        self.log("Configuring IP & DNS...")
        subprocess.run(["netsh", "interface", "ip", "set", "address", "name=LaptopProxyVPN", "static", "10.0.0.2", "255.255.255.0", "10.0.0.1"], capture_output=True)
        subprocess.run(["netsh", "interface", "ip", "set", "dns", "name=LaptopProxyVPN", "static", "8.8.8.8"], capture_output=True)

        time.sleep(1.0)

        self.log("Applying global routes...")
        subprocess.run(["route", "add", "0.0.0.0", "mask", "128.0.0.0", "10.0.0.1", "metric", "1"], capture_output=True)
        subprocess.run(["route", "add", "128.0.0.0", "mask", "128.0.0.0", "10.0.0.1", "metric", "1"], capture_output=True)
        self.log("Global VPN Active.")

    def stop(self):
        self.log("Restoring routes...")
        subprocess.run(["route", "delete", "0.0.0.0", "mask", "128.0.0.0"], capture_output=True)
        subprocess.run(["route", "delete", "128.0.0.0", "mask", "128.0.0.0"], capture_output=True)

        if self.process:
            self.log("Stopping VPN process...")
            self.process.terminate()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self.process = None
            
        subprocess.run(["netsh", "interface", "set", "interface", "LaptopProxyVPN", "disable"], capture_output=True)
        self.log("VPN Stopped.")

class HttpSocksBridge:
    def __init__(self, local_port, socks_host, socks_port, log_fn=None):
        self.local_port = local_port
        self.socks_host = socks_host
        self.socks_port = socks_port
        self.log = log_fn or print
        self._running = False
        self._server = None

    def start(self):
        self._running = True
        threading.Thread(target=self._serve, daemon=True).start()

    def stop(self):
        self._running = False
        if self._server:
            try:
                self._server.close()
            except:
                pass

    def _serve(self):
        try:
            self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self._server.bind(("127.0.0.1", self.local_port))
            self._server.listen(100)
            self._server.settimeout(1.0)
            self.log(f"Bridge listening on 127.0.0.1:{self.local_port}")
        except Exception as e:
            self.log(f"Error starting bridge: {e}")
            return

        while self._running:
            try:
                conn, addr = self._server.accept()
                threading.Thread(target=self._handle, args=(conn,), daemon=True).start()
            except socket.timeout:
                continue
            except Exception:
                break

    def _handle(self, client):
        try:
            client.settimeout(10)
            data = b""
            while b"\r\n" not in data:
                chunk = client.recv(4096)
                if not chunk: return
                data += chunk

            first_line = data.split(b"\r\n")[0].decode("utf-8", errors="replace")
            parts = first_line.split()
            if len(parts) < 3: return
            method, target = parts[0], parts[1]

            if method.upper() == "CONNECT":
                host, port = target.rsplit(":", 1)
                port = int(port)
            else:
                from urllib.parse import urlparse
                parsed = urlparse(target)
                host, port = parsed.hostname or "", parsed.port or 80

            relay = self._socks5_connect(host, port)
            if relay is None:
                client.sendall(b"HTTP/1.1 502 Bad Gateway\r\n\r\n")
                return

            if method.upper() == "CONNECT":
                client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
                self._pipe(client, relay)
            else:
                relay.sendall(data)
                self._pipe(client, relay)
        except Exception:
            pass
        finally:
            try: client.close()
            except: pass

    def _socks5_connect(self, host, port):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(5)
            s.connect((self.socks_host, self.socks_port))
            s.sendall(b"\x05\x01\x00")
            if s.recv(2)[1] != 0x00: return None
            
            # Request connection
            try:
                # IPv4
                s.sendall(b"\x05\x01\x00\x01" + socket.inet_aton(host) + struct.pack(">H", port))
            except:
                # Domain name
                host_bytes = host.encode()
                s.sendall(b"\x05\x01\x00\x03" + bytes([len(host_bytes)]) + host_bytes + struct.pack(">H", port))
            
            res = s.recv(10)
            if len(res) < 2 or res[1] != 0x00: return None
            return s
        except Exception:
            return None

    def _pipe(self, a, b):
        a.settimeout(None)
        b.settimeout(None)
        sockets = [a, b]
        try:
            while True:
                r, _, e = select.select(sockets, [], sockets, 30)
                if e: break
                for s in r:
                    other = b if s is a else a
                    chunk = s.recv(32768)
                    if not chunk: return
                    other.sendall(chunk)
        finally:
            for s in (a, b):
                try: s.close()
                except: pass

class IcsManager:
    """
    Automates Windows Mobile Hotspot and Internet Connection Sharing (ICS).
    Shares the LaptopProxyVPN connection with consoles/Macs.
    """
    def __init__(self, log_fn):
        self.log = log_fn

    def _run_ps(self, script):
        cmd = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script]
        return subprocess.run(cmd, capture_output=True, text=True)

    def get_hotspot_info(self):
        script = """
        $TetheringManager = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking.NetworkOperators, ContentType=WindowsRuntime]::CreateFromConnectionProfile([Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType=WindowsRuntime]::GetInternetConnectionProfile())
        if ($TetheringManager) {
            $access = $TetheringManager.GetCurrentAccessPointConfiguration()
            Write-Output "$($access.Ssid)|$($access.Passphrase)"
        }
        """
        res = self._run_ps(script)
        if res.returncode == 0 and "|" in res.stdout:
            ssid, pwd = res.stdout.strip().split("|")
            return ssid, pwd
        return None, None

    def enable_sharing(self, enable=True):
        action = "Enable" if enable else "Disable"
        self.log(f"{action}ing Console Bridge (Hotspot + ICS)...")

        script = f"""
        # 0. Force-restart SharedAccess to clear stale COM state
        Write-Output "INFO: Restarting SharedAccess service..."
        Set-Service SharedAccess -StartupType Automatic -ErrorAction SilentlyContinue
        Stop-Service SharedAccess -Force -ErrorAction SilentlyContinue
        Start-Sleep -s 1
        Start-Service SharedAccess -ErrorAction SilentlyContinue
        Start-Sleep -s 2

        # 1. Start/Stop Mobile Hotspot
        try {{
            $TetheringManager = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking.NetworkOperators, ContentType=WindowsRuntime]::CreateFromConnectionProfile(
                [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType=WindowsRuntime]::GetInternetConnectionProfile()
            )
            if ("{action}" -eq "Enable") {{
                $TetheringManager.StartTetheringAsync() | Out-Null
                Start-Sleep -s 3
            }} else {{
                $TetheringManager.StopTetheringAsync() | Out-Null
                # CLEANUP FIREWALL RULES ON STOP
                Remove-NetFirewallRule -DisplayName "Hotspot ICS Allow" -ErrorAction SilentlyContinue
                Remove-NetFirewallRule -DisplayName "ICS Hotspot Subnet" -ErrorAction SilentlyContinue
                Write-Output "INFO: Firewall rules removed."
                exit 0
            }}
        }} catch {{
            Write-Output "WARN: Tethering manager error: $_"
        }}

        # 2. Locate the Wi-Fi Direct hotspot adapter
        $hotspotAdapter = Get-NetAdapter | Where-Object {{
            $_.InterfaceDescription -like '*Microsoft Wi-Fi Direct Virtual Adapter*' -and $_.Status -eq 'Up'
        }} | Select-Object -First 1

        if (-not $hotspotAdapter) {{
            $hotspotAdapter = Get-NetAdapter | Where-Object {{
                $_.InterfaceDescription -like '*Microsoft Wi-Fi Direct Virtual Adapter*'
            }} | Select-Object -First 1
        }}

        if ($hotspotAdapter) {{
            $hotspotName = $hotspotAdapter.Name
            $hotspotAlias = $hotspotAdapter.InterfaceAlias
            Write-Output "INFO: Hotspot found on interface: $hotspotAlias"
            
            # 3. FIREWALL FIX (DURING ENABLE)
            Write-Output "INFO: Applying Windows Firewall bypass rules..."
            Remove-NetFirewallRule -DisplayName "Hotspot ICS Allow" -ErrorAction SilentlyContinue
            Remove-NetFirewallRule -DisplayName "ICS Hotspot Subnet" -ErrorAction SilentlyContinue
            
            # Rule 1: Allow everything on the specific virtual interface
            New-NetFirewallRule -DisplayName "Hotspot ICS Allow" -Direction Inbound -Action Allow -InterfaceAlias $hotspotAlias -Enabled True -ErrorAction SilentlyContinue
            
            # Rule 2: Allow the standard ICS subnet (192.168.137.0/24)
            New-NetFirewallRule -DisplayName "ICS Hotspot Subnet" -Direction Inbound -Action Allow -LocalAddress "192.168.137.0/24" -Enabled True -ErrorAction SilentlyContinue
        }}

        # 4. Configure ICS via COM
        $netShare = New-Object -ComObject HNetCfg.HNetShare
        $publicConn  = $null
        $privateConn = $null

        foreach ($conn in $netShare.EnumEveryConnection) {{
            try {{
                $props  = $netShare.NetConnectionProps($conn)
                $config = $netShare.INetSharingConfigurationForINetConnection($conn)

                if ($config.SharingEnabled) {{
                    $config.DisableSharing()
                    Start-Sleep -m 200
                }}

                if ($props.Name -eq 'LaptopProxyVPN') {{
                    $publicConn = $config
                }}
                if ($props.Name -eq $hotspotName) {{
                    $privateConn = $config
                }}
            }} catch {{ }}
        }}

        if ("{action}" -eq "Enable") {{
            if ($publicConn -and $privateConn) {{
                $publicConn.EnableSharing(0)
                Start-Sleep -m 500
                $privateConn.EnableSharing(1)
                Write-Output "DONE"
            }} else {{
                Write-Error "Could not find VPN or Hotspot adapter for ICS."
            }}
        }} else {{
            Write-Output "DONE"
        }}
        """
        res = self._run_ps(script)
        for line in res.stdout.splitlines():
            if line.startswith("INFO:"): self.log(line)
        
        if res.returncode == 0 and "DONE" in res.stdout:
            self.log(f"✓ Console Bridge {action}d successfully.")
        else:
            self.log(f"✗ Error: {res.stderr.strip() or res.stdout.strip()}")

class App:
    def __init__(self, root):
        self.root = root
        self.root.title("Hotspot Proxy Client (Global)")
        self.root.geometry("450x500") 
        
        self.tun_mgr = None
        self.ics_mgr = IcsManager(self.log)
        self.share_enabled = tk.BooleanVar(value=False)
        
        # UI Elements
        frame = ttk.Frame(root, padding="20")
        frame.pack(fill="both", expand=True)
        
        ttk.Label(frame, text="Phone IP:").grid(row=0, column=0, sticky="w", pady=5)
        self.phone_ip = ttk.Entry(frame)
        self.phone_ip.insert(0, "192.168.49.1")
        self.phone_ip.grid(row=0, column=1, sticky="ew", pady=5)
        
        ttk.Label(frame, text="Phone Port (SOCKS5):").grid(row=1, column=0, sticky="w", pady=5)
        self.phone_port = ttk.Entry(frame)
        self.phone_port.insert(0, "8080")
        self.phone_port.grid(row=1, column=1, sticky="ew", pady=5)
        
        ttk.Label(frame, text="Local Bridge Port:").grid(row=2, column=0, sticky="w", pady=5)
        self.local_port = ttk.Entry(frame)
        self.local_port.insert(0, "7890")
        self.local_port.grid(row=2, column=1, sticky="ew", pady=5)

        # Mode Selection (Global VPN is now default)
        self.mode = tk.IntVar(value=2) # 2: Global VPN
        ttk.Label(frame, text="Routing Mode:").grid(row=3, column=0, sticky="w", pady=5)
        ttk.Label(frame, text="GLOBAL VPN", foreground="green", font=("Segoe UI", 9, "bold")).grid(row=3, column=1, sticky="w", pady=5)
        
        self.status_var = tk.StringVar(value="Status: Disconnected")
        ttk.Label(frame, textvariable=self.status_var).grid(row=4, column=0, columnspan=2, pady=10)
        
        self.btn_start = ttk.Button(frame, text="START", command=self.start)
        self.btn_start.grid(row=5, column=0, pady=10, padx=5, sticky="ew")
        
        self.btn_stop = ttk.Button(frame, text="STOP", command=self.stop, state="disabled")
        self.btn_stop.grid(row=5, column=1, pady=10, padx=5, sticky="ew")
        
        self.log_text = tk.Text(frame, height=10, state="disabled", font=("Consolas", 9))
        self.log_text.grid(row=6, column=0, columnspan=2, pady=5, sticky="nsew")
        
        frame.columnconfigure(1, weight=1)
        frame.rowconfigure(6, weight=1)

    def log(self, msg):
        try:
            def _log():
                self.log_text.configure(state="normal")
                self.log_text.insert("end", f"[{time.strftime('%H:%M:%S')}] {msg}\n")
                self.log_text.see("end")
                self.log_text.configure(state="disabled")
            self.root.after(0, _log)
        except:
            print(f"[{time.strftime('%H:%M:%S')}] {msg}")

    def start(self):
        ip = self.phone_ip.get().strip()
        p_port = int(self.phone_port.get().strip())
        l_port = int(self.local_port.get().strip())
        mode = self.mode.get()
        share = self.share_enabled.get()

        self.btn_start.configure(state="disabled")
        self.btn_stop.configure(state="normal")
        self.status_var.set("Status: Starting...")
        
        threading.Thread(target=self._do_start, args=(ip, p_port, l_port, mode, share), daemon=True).start()

    def _do_start(self, ip, p_port, l_port, mode, share):
        try:
            self.log(f"Starting Global VPN to {ip}:{p_port}")
            self.tun_mgr = TunManager(ip, p_port, l_port, self.log)
            self.tun_mgr.start()
            self.log("✓ Global VPN Active.")

            if share and mode == 2:
                self.ics_mgr.enable_sharing(True)

            self.root.after(0, self.status_var.set, "Status: Connected")
        except Exception as e:
            self.log(f"✗ Error: {e}")
            self.root.after(0, self.stop)

    def stop(self):
        self.status_var.set("Status: Stopping...")
        threading.Thread(target=self._do_stop, daemon=True).start()

    def _do_stop(self, is_closing=False):
        if self.share_enabled.get():
            self.ics_mgr.enable_sharing(False)

        if self.tun_mgr:
            self.tun_mgr.stop()
            self.tun_mgr = None
            self.log("VPN stopped.")

        if not is_closing:
            self.root.after(0, self.status_var.set, "Status: Disconnected")
            self.root.after(0, self.btn_start.configure, {"state": "normal"})
            self.root.after(0, self.btn_stop.configure, {"state": "disabled"})
            self.log("Stopped.")

def main():
    root = tk.Tk()
    app = App(root)
    
    def on_closing():
        if app.tun_mgr or app.bridge or app.system_proxy_on:
            print("Cleaning up before exit...")
            app._do_stop(is_closing=True)
        root.destroy()
        
    root.protocol("WM_DELETE_WINDOW", on_closing)
    root.mainloop()

if __name__ == "__main__":
    if IS_WINDOWS:
        try:
            if not ctypes.windll.shell32.IsUserAnAdmin():
                # Relaunch as admin. REQUIRED for TUN Mode network routing.
                ctypes.windll.shell32.ShellExecuteW(None, "runas", sys.executable, " ".join(sys.argv), None, 1)
                sys.exit(0)
        except Exception: pass

    main()