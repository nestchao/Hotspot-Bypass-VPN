"""
Phone Proxy Manager for Windows (Ultimate Edition)
Connects your PC to the SOCKS5 proxy running on your Android phone.
Includes Global VPN (TUN) Mode for full system capture (Games, UDP, etc).
"""

import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext
import threading
import socket
import subprocess
import os
import sys
import time
import json
import struct
import select
import urllib.request
import zipfile
import io
import platform

# ─── Windows Registry Proxy (only import on Windows) ─────────────────────────
IS_WINDOWS = sys.platform == "win32"
if IS_WINDOWS:
    import winreg
    import ctypes

# ══════════════════════════════════════════════════════════════════════════════
#  COLORS & FONTS
# ══════════════════════════════════════════════════════════════════════════════
BG          = "#0D1117"
BG2         = "#161B22"
BG3         = "#21262D"
ACCENT      = "#00F5C4"        # Cyber-teal
ACCENT2     = "#7C3AED"        # Purple
RED         = "#FF4D6D"
GREEN       = "#00C48C"
YELLOW      = "#FFBE0B"
FG          = "#E6EDF3"
FG_DIM      = "#8B949E"
BORDER      = "#30363D"

FONT_TITLE  = ("Segoe UI", 18, "bold")
FONT_HEAD   = ("Segoe UI", 11, "bold")
FONT_BODY   = ("Segoe UI", 10)
FONT_MONO   = ("Consolas", 9)
FONT_SMALL  = ("Segoe UI", 8)

CONFIG_DIR  = os.path.join(os.environ.get("APPDATA", "."), "PhoneProxyManager")
CONFIG_FILE = os.path.join(CONFIG_DIR, "config.json")
BIN_DIR     = os.path.join(CONFIG_DIR, "bin")


# ══════════════════════════════════════════════════════════════════════════════
#  GLOBAL VPN MODE (TUN) MANAGER
# ══════════════════════════════════════════════════════════════════════════════
class TunManager:
    """
    Downloads and manages tun2socks & wintun.dll. 
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

        # CHICKEN AND EGG FIX: PC has no internet to download files.
        # We must start the HTTP Bridge and download the files *through* the phone's proxy!
        self.log("Starting temporary bridge to download VPN files...")
        temp_bridge = HttpSocksBridge(self.local_port, self.phone_ip, self.phone_port, self.log)
        temp_bridge.start()
        time.sleep(1.5) # Give bridge time to start

        # Route urllib traffic through our local bridge
        proxy_handler = urllib.request.ProxyHandler({
            'http': f'http://127.0.0.1:{self.local_port}',
            'https': f'http://127.0.0.1:{self.local_port}'
        })
        opener = urllib.request.build_opener(proxy_handler)
        urllib.request.install_opener(opener)

        try:
            # Download tun2socks
            if not os.path.exists(t2s_path):
                self.log("Downloading tun2socks (VPN Core) via Proxy...")
                req = urllib.request.urlopen(t2s_url, timeout=30)
                with zipfile.ZipFile(io.BytesIO(req.read())) as z:
                    for name in z.namelist():
                        if name.endswith(".exe"):
                            with open(t2s_path, "wb") as f:
                                f.write(z.read(name))
                            break

            # Download wintun.dll
            if not os.path.exists(wt_path):
                self.log("Downloading Wintun Driver via Proxy...")
                req = urllib.request.urlopen("https://www.wintun.net/builds/wintun-0.14.1.zip", timeout=30)
                with zipfile.ZipFile(io.BytesIO(req.read())) as z:
                    wt_file = f"wintun/bin/{wt_arch}/wintun.dll"
                    with open(wt_path, "wb") as f:
                        f.write(z.read(wt_file))
                        
        except Exception as e:
            self.log(f"Download failed: {e}")
            raise Exception("Could not download VPN files. Are you sure Phone proxy is running?")
            
        finally:
            # Clean up the temporary proxy routing and bridge
            urllib.request.install_opener(urllib.request.build_opener())
            temp_bridge.stop()
            time.sleep(0.5)
            self.log("✓ Downloads complete!")

        return t2s_path

    def start(self):
        t2s_exe = self._check_dependencies()
        
        # FIX 1: Clean up any zombie routes from a previous crashed session
        self.log("Cleaning up old routing states...")
        subprocess.run(["route", "delete", "0.0.0.0", "mask", "128.0.0.0"], capture_output=True)
        subprocess.run(["route", "delete", "128.0.0.0", "mask", "128.0.0.0"], capture_output=True)

        self.log("Starting VPN Interface...")
        cmd =[
            t2s_exe,
            "-device", "tun://PhoneVPN",
            "-proxy", f"socks5://{self.phone_ip}:{self.phone_port}",
            "-loglevel", "warning"  # Changed from error to warning for better debugging
        ]

        cflags = 0x08000000 if IS_WINDOWS else 0
        self.process = subprocess.Popen(cmd, cwd=BIN_DIR, creationflags=cflags)

        self.log("Waiting for network adapter...")
        adapter_found = False
        for _ in range(15): # FIX 2: Increased wait time from 10 to 15 seconds
            res = subprocess.run(["netsh", "interface", "ipv4", "show", "interfaces"], capture_output=True, text=True)
            if "PhoneVPN" in res.stdout:
                adapter_found = True
                break
            time.sleep(1)
            
        if not adapter_found:
            self.stop() # Force cleanup
            raise Exception("Failed to create Virtual Adapter. Check if Wintun driver installed properly.")

        # FIX 3: Give Windows a moment to fully initialize the adapter before setting IPs
        time.sleep(2.0) 

        self.log("Configuring IP & DNS routing...")
        subprocess.run(["netsh", "interface", "ip", "set", "address", "name=PhoneVPN", "static", "10.0.0.2", "255.255.255.0", "10.0.0.1"], capture_output=True)
        subprocess.run(["netsh", "interface", "ip", "set", "dns", "name=PhoneVPN", "static", "8.8.8.8"], capture_output=True)

        # FIX 4: Give Windows a moment to apply the IP before modifying global routes
        time.sleep(1.0)

        self.log("Applying Global Traffic Routes (0.0.0.0/1 & 128.0.0.0/1)...")
        res1 = subprocess.run(["route", "add", "0.0.0.0", "mask", "128.0.0.0", "10.0.0.1", "metric", "1"], capture_output=True, text=True)
        res2 = subprocess.run(["route", "add", "128.0.0.0", "mask", "128.0.0.0", "10.0.0.1", "metric", "1"], capture_output=True, text=True)
        
        if "failed" in res1.stderr.lower() or "failed" in res2.stderr.lower():
            self.stop()
            raise Exception("Failed to inject routing tables. Run as Administrator!")

    def stop(self):
        self.log("Restoring original routing...")
        subprocess.run(["route", "delete", "0.0.0.0", "mask", "128.0.0.0"], capture_output=True)
        subprocess.run(["route", "delete", "128.0.0.0", "mask", "128.0.0.0"], capture_output=True)

        if self.process:
            self.log("Shutting down VPN interface...")
            self.process.terminate()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self.process = None
            
        # FIX 5: Attempt to forcefully disable the interface so it doesn't get stuck
        subprocess.run(["netsh", "interface", "set", "interface", "PhoneVPN", "disable"], capture_output=True)

# ══════════════════════════════════════════════════════════════════════════════
#  LOCAL HTTP→SOCKS5 BRIDGE
# ══════════════════════════════════════════════════════════════════════════════
class HttpSocksBridge:
    def __init__(self, local_port, socks_host, socks_port, log_fn=None):
        self.local_port  = local_port
        self.socks_host  = socks_host
        self.socks_port  = socks_port
        self.log         = log_fn or print
        self._running    = False
        self._server     = None

    def start(self):
        self._running = True
        # FIX: Ensure old server is completely dead before starting thread
        if self._server:
            try: self._server.close()
            except: pass
        threading.Thread(target=self._serve, daemon=True).start()

    def stop(self):
        self._running = False
        try:
            if self._server: self._server.close()
        except Exception: pass

    def _serve(self):
        # FIX: Added a retry loop so if the port is stuck in TIME_WAIT, it tries again.
        for attempt in range(5):
            try:
                self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                self._server.bind(("127.0.0.1", self.local_port))
                self._server.listen(200)
                self._server.settimeout(1.0)
                break # Success! Break out of the retry loop
            except OSError as e:
                self.log(f"[Bridge] Port {self.local_port} in use. Retrying ({attempt+1}/5)...")
                time.sleep(1)
        else:
            self.log(f"[Bridge] FATAL: Could not bind port {self.local_port}.")
            self._running = False
            return

        while self._running:
            try:
                conn, addr = self._server.accept()
                threading.Thread(target=self._handle, args=(conn,), daemon=True).start()
            except socket.timeout: continue
            except Exception: break

    def _handle(self, client: socket.socket):
        try:
            client.settimeout(30)
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
        except Exception: pass
        finally:
            try: client.close()
            except: pass

    def _socks5_connect(self, host: str, port: int) -> socket.socket | None:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(10)
            s.connect((self.socks_host, self.socks_port))
            s.sendall(b"\x05\x01\x00")
            if s.recv(2)[1] != 0x00: return None
            try:
                s.sendall(b"\x05\x01\x00\x01" + socket.inet_aton(host) + struct.pack(">H", port))
            except OSError:
                host_bytes = host.encode()
                s.sendall(b"\x05\x01\x00\x03" + bytes([len(host_bytes)]) + host_bytes + struct.pack(">H", port))
            if s.recv(10)[1] != 0x00: return None
            return s
        except Exception: return None

    def _pipe(self, a: socket.socket, b: socket.socket):
        a.settimeout(None); b.settimeout(None)
        sockets =[a, b]
        try:
            while True:
                r, _, e = select.select(sockets,[], sockets, 30)
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


# ══════════════════════════════════════════════════════════════════════════════
#  WINDOWS PROXY SETTINGS
# ══════════════════════════════════════════════════════════════════════════════
class WindowsProxyManager:
    REG_PATH = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings"

    @staticmethod
    def set_proxy(host: str, port: int):
        if not IS_WINDOWS: return
        try:
            key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, WindowsProxyManager.REG_PATH, 0, winreg.KEY_WRITE)
            winreg.SetValueEx(key, "ProxyServer",  0, winreg.REG_SZ, f"{host}:{port}")
            winreg.SetValueEx(key, "ProxyEnable",  0, winreg.REG_DWORD, 1)
            winreg.SetValueEx(key, "ProxyOverride", 0, winreg.REG_SZ, "localhost;127.*;10.*;172.16.*;<local>")
            winreg.CloseKey(key)
            ctypes.windll.Wininet.InternetSetOptionW(0, 39, 0, 0)
            ctypes.windll.Wininet.InternetSetOptionW(0, 37, 0, 0)
        except Exception: pass

    @staticmethod
    def clear_proxy():
        if not IS_WINDOWS: return
        try:
            key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, WindowsProxyManager.REG_PATH, 0, winreg.KEY_WRITE)
            winreg.SetValueEx(key, "ProxyEnable", 0, winreg.REG_DWORD, 0)
            winreg.CloseKey(key)
            ctypes.windll.Wininet.InternetSetOptionW(0, 39, 0, 0)
            ctypes.windll.Wininet.InternetSetOptionW(0, 37, 0, 0)
        except Exception: pass


# ══════════════════════════════════════════════════════════════════════════════
#  MAIN APPLICATION
# ══════════════════════════════════════════════════════════════════════════════
class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Phone Proxy Manager (Ultimate)")
        self.geometry("700x820")
        self.minsize(650, 750)
        self.configure(bg=BG)

        self._bridge: HttpSocksBridge | None = None
        self._tun_mgr: TunManager | None = None
        self._system_proxy_on = False
        self._config = self._load_config()

        self._build_ui()
        self._populate_config()
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    def _load_config(self):
        defaults = {"phone_ip": "192.168.49.1", "phone_port": "8080", "local_port": "7890", "mode": 4}
        try:
            if os.path.exists(CONFIG_FILE):
                with open(CONFIG_FILE) as f: defaults.update(json.load(f))
        except Exception: pass
        return defaults

    def _save_config(self):
        try:
            os.makedirs(CONFIG_DIR, exist_ok=True)
            with open(CONFIG_FILE, "w") as f: json.dump(self._config, f, indent=2)
        except Exception: pass

    def _build_ui(self):
        hdr = tk.Frame(self, bg=BG2, height=64)
        hdr.pack(fill="x", side="top")
        hdr.pack_propagate(False)

        canvas = tk.Canvas(hdr, width=14, height=14, bg=BG2, highlightthickness=0)
        canvas.place(x=20, y=25)
        canvas.create_oval(0, 0, 14, 14, fill=ACCENT, outline="")

        tk.Label(hdr, text="PHONE PROXY MANAGER", font=("Segoe UI", 14, "bold"), fg=ACCENT, bg=BG2).place(x=42, y=13)
        tk.Label(hdr, text="Routes Windows traffic through your Android phone", font=FONT_SMALL, fg=FG_DIM, bg=BG2).place(x=42, y=37)
        tk.Label(hdr, text="v3.0 TUN", font=FONT_SMALL, fg=BG3, bg=ACCENT, padx=5, pady=1).place(relx=1.0, x=-70, y=20)

        body_frame = tk.Frame(self, bg=BG)
        body_frame.pack(fill="both", expand=True, padx=16, pady=12)

        self._card_connection(body_frame)
        self._card_proxy_mode(body_frame)
        self._card_controls(body_frame)
        self._card_log(body_frame)

    def _make_card(self, parent, title):
        outer = tk.Frame(parent, bg=BG2, bd=0, highlightthickness=1, highlightbackground=BORDER)
        outer.pack(fill="x", pady=(0, 10))
        tk.Label(outer, text=title, font=FONT_HEAD, fg=FG_DIM, bg=BG2, padx=14, pady=8, anchor="w").pack(fill="x")
        tk.Frame(outer, bg=BORDER, height=1).pack(fill="x")
        inner = tk.Frame(outer, bg=BG2, padx=14, pady=12)
        inner.pack(fill="x")
        return inner

    def _make_entry(self, parent, label_text, var, width=22, row=0, col=0):
        tk.Label(parent, text=label_text, font=FONT_BODY, fg=FG_DIM, bg=BG2).grid(row=row, column=col*2, sticky="w", pady=4, padx=(0, 6))
        e = tk.Entry(parent, textvariable=var, font=FONT_MONO, width=width, bg=BG3, fg=FG, insertbackground=ACCENT, relief="flat", bd=0, highlightthickness=1, highlightbackground=BORDER, highlightcolor=ACCENT)
        e.grid(row=row, column=col*2+1, sticky="ew", pady=4, padx=(0, 14))
        return e

    def _card_connection(self, parent):
        card = self._make_card(parent, "📱  Phone Connection")
        self._phone_ip, self._phone_port, self._local_port = tk.StringVar(), tk.StringVar(), tk.StringVar()
        card.columnconfigure(1, weight=1); card.columnconfigure(3, weight=1)

        self._make_entry(card, "Phone IP", self._phone_ip, width=18, row=0, col=0)
        self._make_entry(card, "Phone Port", self._phone_port, width=8, row=0, col=1)
        self._make_entry(card, "Bridge Port", self._local_port, width=8, row=1, col=0)

        tk.Label(card, text="(Only used in Bridge mode)", font=FONT_SMALL, fg=FG_DIM, bg=BG2).grid(row=1, column=3, sticky="w")

        btn_row = tk.Frame(card, bg=BG2)
        btn_row.grid(row=2, column=0, columnspan=4, sticky="ew", pady=(8, 0))
        self._btn(btn_row, "  Test Connection", self._test_connection, color=ACCENT2, fg="white").pack(side="left")
        self._conn_status = tk.Label(btn_row, text="—", font=FONT_BODY, fg=FG_DIM, bg=BG2)
        self._conn_status.pack(side="left", padx=14)

    def _card_proxy_mode(self, parent):
        card = self._make_card(parent, "⚙️  Proxy Routing Mode")
        self._mode = tk.IntVar(value=self._config.get("mode", 4))

        modes =[
            (4, "Global VPN (TUN) ⭐ ALL APPS & GAMES", "Downloads VPN driver. Routes 100% of PC traffic (Games, UDP, Discord, Web)."),
            (1, "HTTP Bridge + System Proxy", "Routes standard traffic. Good for browsers and light web apps."),
            (2, "System Proxy Only", "Sets Windows proxy to phone directly. (Basic compatibility)"),
            (3, "HTTP Bridge Only", "Creates local port 127.0.0.1. Set up apps manually.")
        ]

        for val, label, desc in modes:
            row = tk.Frame(card, bg=BG2)
            row.pack(fill="x", pady=4)
            tk.Radiobutton(row, variable=self._mode, value=val, bg=BG2, activebackground=BG2, selectcolor=BG3, fg=ACCENT if val==4 else FG, font=("Segoe UI", 10, "bold" if val==4 else "normal"), text=label, activeforeground=ACCENT).pack(side="left")
            tk.Label(row, text=desc, font=FONT_SMALL, fg=FG_DIM, bg=BG2, wraplength=480, justify="left").pack(side="left", padx=(6, 0))

    def _card_controls(self, parent):
        card = self._make_card(parent, "▶  Controls")
        row = tk.Frame(card, bg=BG2)
        row.pack(fill="x")

        self._start_btn = self._btn(row, "  ▶  START", self._start_proxy, color=GREEN, fg="black", width=14)
        self._start_btn.pack(side="left", padx=(0, 8))

        self._stop_btn = self._btn(row, "  ■  STOP", self._stop_proxy, color=RED, fg="white", width=12, state="disabled")
        self._stop_btn.pack(side="left", padx=(0, 16))

        self._status_dot = tk.Canvas(row, width=14, height=14, bg=BG2, highlightthickness=0)
        self._status_dot.pack(side="left")
        self._dot = self._status_dot.create_oval(2, 2, 12, 12, fill=FG_DIM)

        self._status_lbl = tk.Label(row, text="Inactive", font=("Segoe UI", 10, "bold"), fg=FG_DIM, bg=BG2)
        self._status_lbl.pack(side="left", padx=6)

    def _card_log(self, parent):
        card = self._make_card(parent, "📋  Activity Log")
        self._log = scrolledtext.ScrolledText(card, height=10, font=FONT_MONO, bg=BG3, fg=ACCENT, insertbackground=ACCENT, relief="flat", bd=0, highlightthickness=0, state="disabled", wrap="word")
        self._log.pack(fill="both", expand=True)
        tk.Button(card, text="Clear", font=FONT_SMALL, bg=BG3, fg=FG_DIM, relief="flat", cursor="hand2", command=lambda: self._log.configure(state="normal") or self._log.delete("1.0", "end") or self._log.configure(state="disabled")).pack(anchor="e", pady=(4, 0))

    def _btn(self, parent, text, command, color=ACCENT, fg="black", width=None, state="normal"):
        kw = {"font": ("Segoe UI", 10, "bold"), "bg": color, "fg": fg, "relief": "flat", "cursor": "hand2", "padx": 14, "pady": 7, "command": command, "state": state, "bd": 0}
        if width: kw["width"] = width
        btn = tk.Button(parent, text=text, **kw)
        btn.bind("<Enter>", lambda e: btn.configure(bg=f"#{min(255, int(color[1:3], 16)+30):02X}{min(255, int(color[3:5], 16)+30):02X}{min(255, int(color[5:7], 16)+30):02X}"))
        btn.bind("<Leave>", lambda e: btn.configure(bg=color))
        return btn

    def _log_msg(self, msg: str):
        self.after(0, lambda:[self._log.configure(state="normal"), self._log.insert("end", f"[{time.strftime('%H:%M:%S')}] {msg}\n"), self._log.see("end"), self._log.configure(state="disabled")])

    def _set_status(self, text: str, color: str):
        self.after(0, self._status_lbl.configure, {"text": text, "fg": color})
        self.after(0, self._status_dot.itemconfigure, self._dot, {"fill": color})

    def _populate_config(self):
        self._phone_ip.set(self._config.get("phone_ip", "192.168.49.1"))
        self._phone_port.set(self._config.get("phone_port", "8080"))
        self._local_port.set(self._config.get("local_port", "7890"))

    def _test_connection(self):
        ip, port = self._phone_ip.get().strip(), int(self._phone_port.get().strip() or "8080")
        self._conn_status.configure(text="Testing…", fg=YELLOW)
        def _test():
            self._log_msg(f"Testing connection to {ip}:{port} …")
            try:
                t0 = time.time()
                socket.create_connection((ip, port), timeout=3).close()
                avg_ms = int((time.time() - t0) * 1000)
                self._log_msg(f"✓ Phone proxy reachable (~{avg_ms}ms)")
                self.after(0, self._conn_status.configure, {"text": f"✓ Connected ~{avg_ms}ms", "fg": GREEN})
            except Exception as e:
                self._log_msg(f"✗ Cannot reach {ip}:{port} — {e}")
                self.after(0, self._conn_status.configure, {"text": "✗ Unreachable", "fg": RED})
        threading.Thread(target=_test, daemon=True).start()

    def _start_proxy(self):
        ip, phone_port, local_port, mode = self._phone_ip.get().strip(), int(self._phone_port.get().strip() or "8080"), int(self._local_port.get().strip() or "7890"), self._mode.get()
        self._config.update({"phone_ip": ip, "phone_port": str(phone_port), "local_port": str(local_port), "mode": mode})
        self._save_config()

        self._start_btn.configure(state="disabled")
        self._stop_btn.configure(state="normal")
        self._set_status("Starting…", YELLOW)

        threading.Thread(target=self._do_start, args=(ip, phone_port, local_port, mode), daemon=True).start()

    def _do_start(self, ip, phone_port, local_port, mode):
        self._log_msg(f"Initiating Mode {mode} connection to {ip}:{phone_port}")

        try:
            if mode == 4:
                self._tun_mgr = TunManager(ip, phone_port, local_port, self._log_msg)
                self._tun_mgr.start()
                self._log_msg("✓ GLOBAL VPN ENABLED! All Windows traffic is now routed to the phone.")
                self._set_status("VPN Active ✓", GREEN)
                return

            if mode in (1, 3):
                self._bridge = HttpSocksBridge(local_port, ip, phone_port, self._log_msg)
                self._bridge.start()
                time.sleep(0.4)
                self._log_msg(f"✓ HTTP Bridge running on 127.0.0.1:{local_port}")

            if mode in (1, 2):
                ph_host, ph_port = ("127.0.0.1", local_port) if mode == 1 else (ip, phone_port)
                WindowsProxyManager.set_proxy(ph_host, ph_port)
                self._system_proxy_on = True
                self._log_msg(f"✓ Windows System Proxy set to {ph_host}:{ph_port}")

            self._log_msg("✓ Proxy Active!")
            self._set_status("Active ✓", GREEN)

        except Exception as e:
            self._log_msg(f"✗ ERROR: {str(e)}")
            self.after(0, self._stop_proxy) # Auto revert UI on fail

    def _stop_proxy(self):
        self._start_btn.configure(state="normal")
        self._stop_btn.configure(state="disabled")
        self._set_status("Stopping…", YELLOW)
        threading.Thread(target=self._do_stop, daemon=True).start()

    def _do_stop(self):
        if self._tun_mgr:
            self._tun_mgr.stop()
            self._tun_mgr = None

        if self._bridge:
            self._bridge.stop()
            self._bridge = None
            self._log_msg("✓ Bridge stopped")

        if self._system_proxy_on:
            WindowsProxyManager.clear_proxy()
            self._system_proxy_on = False
            self._log_msg("✓ Windows proxy cleared")

        self._log_msg("Proxy/VPN completely stopped.")
        self._set_status("Inactive", FG_DIM)

    def _on_close(self):
        # FIX: Automatically clear proxy on exit, don't rely just on the prompt
        if self._system_proxy_on:
            WindowsProxyManager.clear_proxy()
            
        if self._tun_mgr or self._bridge or self._system_proxy_on:
            if messagebox.askyesno("Quit", "Connection is still active. Stop and exit?"):
                self._do_stop()
                time.sleep(1.0) # Give it time to delete routes
                self.destroy()
        else:
            self.destroy()

if __name__ == "__main__":
    if IS_WINDOWS:
        try:
            if not ctypes.windll.shell32.IsUserAnAdmin():
                # Relaunch as admin. REQUIRED for TUN Mode network routing.
                ctypes.windll.shell32.ShellExecuteW(None, "runas", sys.executable, " ".join(sys.argv), None, 1)
                sys.exit(0)
        except Exception: pass

    app = App()
    app.mainloop()