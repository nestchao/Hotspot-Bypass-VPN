# 功能介绍

## 工作原理

Hotspot Bypass VPN 通过两步策略绕过运营商热点限制：

1. **Wi-Fi Direct** — 不使用系统自带的移动热点（运营商可检测），而是创建 Wi-Fi Direct（P2P）直连网络。运营商看到的是普通手机间通信。
2. **SOCKS5 代理** — 在宿主手机上运行高性能 SOCKS5 代理服务器。客户端设备连接 Wi-Fi Direct 网络后，所有流量通过该代理转发。

从运营商角度看，所有流量均直接来自宿主机的蜂窝连接 —— **而非来自于热点共享设备**。

---

## Host 模式

将手机蜂窝数据分享给其他设备。

- 创建 Wi-Fi Direct 网络，SSID 为 `DIRECT-HotspotBypass`
- 自定义密码保护（默认：`87654321`）
- 频段选择：2.4GHz（更远距离）或 5GHz（更低延迟）
- 内置 SOCKS5 代理服务器（TCP + UDP），端口 8080
- 一键复制连接信息（SSID、密码、代理 IP、端口）
- 前台服务保持锁屏后不断连

## Client 模式

连接到另一台运行 Host 模式的手机，并通过代理隧道转发所有流量。

- 使用 Android VpnService + tun2socks 引擎拦截全部流量
- 所有应用、所有协议 —— 全部通过代理转发
- 专用重连按钮可快速重启隧道
- 适合游戏、流媒体和网页浏览

## Windows 桌面客户端

功能完整的 Python 应用程序，适用于 Windows 笔记本。

- **TUN 虚拟网卡** — 创建虚拟网卡，将 Windows 的全部流量路由至手机的 SOCKS5 代理
- **自动安装** — 首次运行时自动下载 tun2socks 和 wintun 依赖
- **管理员权限处理** — 需要时自动提升权限以配置网络
- **GUI 控制面板** — 清晰界面，实时状态、日志和控制
- **设备桥接** — 可通过 Windows 移动热点 + ICS 进一步分享 VPN 连接

## 非 App 设备支持

连接无法直接安装 App 的设备：

| 设备 | 方式 |
|------|------|
| **PS4 / PS5** | 通过 Windows ICS 桥接 |
| **Xbox One / Series X\|S** | 通过 Windows ICS 桥接 |
| **Nintendo Switch** | 通过 Windows ICS 桥接 |
| **MacBook** | 通过 Windows ICS 桥接 |
| **智能电视** | 通过 Windows ICS 桥接 |

## 持久连接

- **前台服务** — 以高优先级前台服务运行，防止被 Android 系统杀死
- **WakeLock + WifiLock** — 防止 CPU 和 Wi-Fi 休眠
- **AlarmManager 重启** — 服务意外停止时自动重启
- **开机自启** — 可在设备启动时自动运行

## 安全与隐私

- **无需 Root** — 仅使用标准 Android API
- **本地代理** — SOCKS5 代理仅绑定到 Wi-Fi Direct 接口
- **MIT 许可证** — 完全开源，代码可自行审查
