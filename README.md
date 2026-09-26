<div align="center">

# ⚡ AetherLink
### The Missing Continuity Bridge Between Android & macOS

[![Version: v1.1.0-beta.1](https://img.shields.io/badge/Version-v1.1.0--beta.1-purple.svg)](https://github.com/mehmetsensoyme/aetherlink/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: macOS](https://img.shields.io/badge/Platform-macOS%2014%2B%20%7C%2015%20Sequoia-black)](macos-client)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%2014%20%7C%2015%20%7C%2016-green)](android-client)
[![Privacy: 100% P2P](https://img.shields.io/badge/Privacy-100%25%20Local%20P2P-brightgreen)](#-privacy--zero-cloud-guarantee)
[![Auto Update](https://img.shields.io/badge/Auto--Update-GitHub%20Releases%20API-orange)](docs/AUTO_UPDATE_SPEC.md)

[**Türkçe Dokümantasyon için Tıklayınız (README.tr.md)**](README.tr.md)

</div>

---

## 📖 Overview

**AetherLink** brings native Apple-like ecosystem continuity to Android and macOS devices. It seamlessly unifies phone calls, VoIP calls (WhatsApp, Telegram), universal notifications with inline replies, universal clipboard, wireless screen mirroring, and camera continuity — all without proprietary cloud lock-in.

Built with native performance in mind: **Swift & SwiftUI** for macOS and **Kotlin & Jetpack Compose** for Android.

---

## 🌟 Key Features

* 📱 **Wireless Screen Mirroring (Apple iPhone Mirroring Alternative):**
  Mirror your Android phone screen directly onto your Mac screen in a floating, ultra-responsive window at smooth 30 FPS over local Wi-Fi or USB/ADB with zero cloud.
* 📞 **Cellular & VoIP Call Mirroring:**
  Answer or decline incoming GSM calls, WhatsApp calls, and Telegram calls directly from a native macOS call banner. High-fidelity, ultra-low latency two-way audio powered by WebRTC.
* 💬 **Universal Notifications & Quick Reply:**
  Every notification (WhatsApp, Instagram DM, Telegram, SMS, Slack) appears in macOS Notification Center. Reply inline from Mac without unlocking your phone.
* 📋 **Universal Clipboard:**
  Copy on Mac (`Cmd+C`), paste on Android (`Paste`), and vice-versa. Supports plain text, rich text, and images with automatic loop prevention.
* 🔋 **Two-Way Hardware Battery & Power Sync:**
  Real-time IOKit and Android BatteryManager synchronization. View exact battery percentages, charging indicators, and AC power status reactively on both devices.
* 🔄 **Built-in Auto-Update & Changelog Engine:**
  Native update checker powered by GitHub Releases. Users receive an interactive glassmorphic modal with markdown changelogs whenever a new release drops.

---

## 🔒 Privacy & Zero-Cloud Guarantee

1. **No External Servers:** AetherLink operates exclusively over your local Wi-Fi subnet and Bluetooth Low Energy (BLE).
2. **Encrypted Handshake:** Initial device pairing uses an offline QR code exchange with Curve25519 (ECDH) key agreement and AES-256-GCM encryption.
3. **GDPR / KVKK Exempt:** Your personal chats, call metadata, and clipboard data never touch a remote server or telemetry pipeline.

---

## 📁 Repository Structure

```
aetherlink/
├── README.md               # English Documentation
├── README.tr.md            # Turkish Documentation
├── LICENSE                 # MIT License
├── docs/                   # Specifications & Technical Architecture
│   ├── ARCHITECTURE.md     # Deep P2P Topology & Call Pipelines
│   ├── PERMISSIONS_GUIDE.md# Modern Android 14-16 & macOS 15 Sequoia Permissions
│   └── AUTO_UPDATE_SPEC.md # GitHub Releases Changelog & Update Engine Spec
├── core-engine/            # Shared Protocol & Update Engine
│   ├── protocol.ts         # TypeScript P2P Event Contracts
│   └── update_checker.ts   # Universal GitHub Release Updater
├── macos-client/           # Native macOS SwiftUI MenuBar Application
│   ├── Info.plist          # macOS 15 Sequoia Privacy & Bonjour Configuration
│   └── AetherLink.entitlements # Hardened Runtime Network Sandbox
└── android-client/         # Modern Android Kotlin Daemon
    └── app/src/main/
        └── AndroidManifest.xml # Android 14+ Typed Foreground Services
```

---

## 🛠️ Requirements & Permissions

For a complete breakdown of permissions and modern platform security requirements (including macOS 15 Sequoia Local Network Access and Android 14+ Typed Foreground Services), see [docs/PERMISSIONS_GUIDE.md](docs/PERMISSIONS_GUIDE.md).

---

## 🙏 Acknowledgments & Credits

AetherLink is powered by and stands on the shoulders of fantastic open-source projects:
* **[scrcpy](https://github.com/Genymobile/scrcpy) by [Genymobile](https://github.com/Genymobile)** - The industry-standard ultra-low latency, high-performance Android screen mirroring and remote control engine.
* **[FFmpeg](https://ffmpeg.org/)** - For seamless video stream decoding and hardware acceleration.
* **[SDL (Simple DirectMedia Layer)](https://www.libsdl.org/)** - For cross-platform windowing, graphics rendering, and low-latency input event processing.

---

## 📄 License

Distributed under the **MIT License**. See [LICENSE](LICENSE) for more details.
