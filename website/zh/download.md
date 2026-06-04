# 下载

## Android App

获取最新版本的 Hotspot Bypass VPN Android 应用。

| 项目 | 链接 |
|------|------|
| APK 下载 | [Hotspot_Bypass_VPN.apk](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v3.0.0/Hotspot_Bypass_VPN.apk) |
| Google Play | *即将推出* |
| 源代码 | [GitHub 仓库](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot) |

### 系统要求

- Android 7.0 (API 24) 或更高版本
- 无需 Root 权限
- 支持 Wi-Fi Direct 的设备（大部分现代 Android 手机）

## Windows 客户端

Windows 笔记本用户可使用专用桌面客户端连接手机代理。

| 项目 | 链接 |
|------|------|
| 独立 EXE | [Hotspot_Bypass_VPN_Windows.exe](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v3.0.0/Hotspot_Bypass_VPN_Windows.exe) |
| 源代码 | [laptop_proxy/](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/tree/master/laptop_proxy) |

### 系统要求

- Windows 10 或 11（64 位）
- 管理员权限（TUN 虚拟网卡需要）

## 从源码构建

### Android App

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot
./gradlew assembleDebug
```

### Windows 客户端

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot/laptop_proxy
pip install -r requirements.txt
python main.py
```

构建独立可执行文件：

```bash
.\build_exe.bat
```

---

## 更新日志

### 版本 3.0.0 (2026年6月)

- 增强了服务可靠性和持久性
- 优化了游戏场景的网络性能
- 改进了 Windows 客户端稳定性
- 新增 Windows Phone Proxy Manager
- 应用 Logo 和品牌更新

### 版本 2.0.0 (2026年5月)

- 优化网络性能，降低游戏延迟
- 改进后台服务持久性和可靠性
- 增强 Windows 客户端稳定性和功能
- 添加应用 Logo 和品牌标识
