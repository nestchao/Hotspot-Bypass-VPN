# Windows 客户端使用指南

## 概述

Windows 桌面客户端将笔记本连接到手机的 SOCKS5 代理，创建 TUN 虚拟网卡，将**全部**系统流量路由至手机连接。

---

## 安装

### 方式一：独立 EXE（推荐）

1. 从[下载页面](/zh/download)下载 `Hotspot_Bypass_VPN_Windows.exe`。
2. 运行可执行文件。如 Windows SmartScreen 显示警告，请点击**更多信息** → **仍要运行**。
3. 应用会请求**管理员权限** — 请接受（TUN 虚拟网卡需要）。

### 方式二：从源码运行

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot/laptop_proxy
pip install -r requirements.txt
python main.py
```

---

## 快速开始

![Windows 客户端截图](/images/screenshot-windows.png)
*Windows 客户端主界面*

### 第一步：手机设置

确保手机运行在 **Host 模式**（参见 [Android 使用指南](/zh/guide/android)）。

记录连接信息：
- **SSID：** `DIRECT-HotspotBypass`
- **密码：** `87654321`
- **代理：** `192.168.49.1:8080`

### 第二步：连接笔记本到 Wi-Fi Direct

1. 打开笔记本的 Wi-Fi 设置。
2. 找到并连接到 **DIRECT-HotspotBypass**。
3. 输入密码 **87654321**。

### 第三步：启动 Windows 客户端

1. 打开 Windows 客户端应用。
2. 输入代理详情：
   - **代理 IP：** `192.168.49.1`
   - **代理端口：** `8080`
3. 点击**启动 VPN**。
4. 应用首次运行时自动下载 tun2socks 和 wintun 依赖，然后创建 TUN 虚拟网卡。

### 第四步：验证

- 查看状态指示器 — 应显示**已连接**。
- 打开浏览器访问任意网站 — 流量应通过手机路由。
- 查看实时日志了解连接详情。

---

## 常见问题

| 问题 | 解决方案 |
|------|----------|
| "需要管理员权限" | 右键 EXE → **以管理员身份运行** |
| tun2socks 下载失败 | 检查网络连接。应用可先通过手机代理连接。 |
| TUN 网卡未创建 | 以管理员身份运行应用。关闭其他 VPN 软件。 |
| 连接后无网络 | 尝试重启 VPN。检查手机是否仍在 Host 模式。 |
| 游戏卡顿 / 高延迟 | 将手机切换到 5GHz Wi-Fi Direct 频段。缩短设备间距离。 |
| 桥接模式不工作 | 确保 ICS 配置正确。重启移动热点。 |
