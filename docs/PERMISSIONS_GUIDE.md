# 🔐 AetherLink: Up-to-Date Permissions & Security Matrix
> **Platform Requirements:** macOS 15+ (Sequoia) & Android 14 / 15 / 16 (API 34 - 36)

This document specifies the exact permissions, manifests, entitlements, and runtime declaration requirements to build a zero-crash, enterprise-grade continuity bridge between Android and macOS.

---

## 🤖 1. Android Platform (Targeting Android 14, 15, 16 / API 34+)

Starting with Android 14 and strengthened in Android 15/16, Google enforces strict **Foreground Service Types**, runtime declarations, and privacy boundaries.

### A. Foreground Service Types & Manifest Requirements
A generic `FOREGROUND_SERVICE` is no longer permitted on Android 14+. AetherLink declares explicit types:

| Permission | Minimum API | Reason / Usage |
|:---|:---|:---|
| `android.permission.FOREGROUND_SERVICE` | API 28+ | Base foreground service capability. |
| `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` | API 34+ (Android 14) | Required for ongoing Bluetooth Low Energy (BLE) and Wi-Fi Direct socket communication with the Mac. |
| `android.permission.FOREGROUND_SERVICE_PHONE_CALL` | API 34+ (Android 14) | Required to hold and route active incoming/outgoing cellular and VoIP calls without system termination. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | API 34+ (Android 14) | Fallback continuous continuity daemon. |

### B. Telephony, Calling & Contacts Permissions
| Permission | Level | Reason / Usage |
|:---|:---|:---|
| `android.permission.MANAGE_OWN_CALLS` | Normal | Allows AetherLink to integrate with `TelecomManager` for VoIP call handling. |
| `android.permission.READ_PHONE_STATE` | Runtime | Detect incoming GSM cellular calls, cellular network status, and caller states. |
| `android.permission.ANSWER_PHONE_CALLS` | Runtime | Allows Mac to answer an incoming phone call remotely. |
| `android.permission.CALL_PHONE` | Runtime | Allows initiating a phone call directly from macOS dialer. |
| `android.permission.READ_CALL_LOG` | Runtime | Reads incoming caller phone number and name. |
| `android.permission.READ_CONTACTS` | Runtime | Matches incoming call phone numbers with contact names and profile pictures. |

### C. Notifications & Quick Reply (WhatsApp, Telegram, SMS)
| Permission | Level | Reason / Usage |
|:---|:---|:---|
| `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Display the active persistent background status notification on Android. |
| `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | System Signature / User Granted in Settings | **Crucial:** Allows reading incoming notifications and executing `RemoteInput` actions to reply directly from Mac. |

### D. Audio, Video & WebRTC Relay
| Permission | Level | Reason / Usage |
|:---|:---|:---|
| `android.permission.RECORD_AUDIO` | Runtime | WebRTC two-way audio streaming during calls. |
| `android.permission.MODIFY_AUDIO_SETTINGS` | Normal | Routes call audio between handset, speaker, and WebRTC virtual stream. |
| `android.permission.CAMERA` | Runtime | Continuity Camera wireless streaming to macOS. |

### E. Local Discovery & Connectivity
| Permission | Level | Reason / Usage |
|:---|:---|:---|
| `android.permission.NEARBY_WIFI_DEVICES` | Runtime (API 33+) | Discovers Mac on local Wi-Fi without requesting GPS Fine Location (`usesPermissionFlags="neverForLocation"`). |
| `android.permission.BLUETOOTH_SCAN` | Runtime (API 31+) | Discovers nearby Mac via BLE beacon advertisement (`neverForLocation`). |
| `android.permission.BLUETOOTH_CONNECT` | Runtime (API 31+) | Connects to paired Mac over Bluetooth. |
| `android.permission.BLUETOOTH_ADVERTISE` | Runtime (API 31+) | Advertises Android presence to Mac when proximity is detected. |
| `android.permission.INTERNET` | Normal | Local socket and WebSocket server communication. |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Detects Wi-Fi network changes and IP changes. |
| `android.permission.WAKE_LOCK` | Normal | Prevents CPU sleep while an active call is streaming. |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Special | Prompts user to whitelist AetherLink from Samsung aggressive background app killing. |

---

## 🍏 2. macOS Platform (Targeting macOS 15 Sequoia & macOS 16)

Starting with **macOS 15 (Sequoia)**, Apple introduced the strict **Local Network Privacy Prompt** (`NSLocalNetworkUsageDescription`), making explicit declaration mandatory.

### A. Info.plist Privacy Keys
```xml
<!-- macOS 15 Sequoia Mandatory Local Network Access -->
<key>NSLocalNetworkUsageDescription</key>
<string>AetherLink requires access to your local network to discover and securely synchronize with your Android device for calls, notifications, and clipboard sharing.</string>

<!-- Bonjour (mDNS) Service Advertisements -->
<key>NSBonjourServices</key>
<array>
    <string>_aetherlink._tcp</string>
    <string>_aetherlink._udp</string>
</array>

<!-- Audio & Video Usage Descriptions -->
<key>NSMicrophoneUsageDescription</key>
<string>AetherLink uses your Mac microphone to allow you to talk during phone calls and VoIP calls initiated through your Android phone.</string>

<key>NSCameraUsageDescription</key>
<string>AetherLink uses Continuity Camera to stream your Android phone camera to Mac apps.</string>

<!-- Bluetooth Low Energy -->
<key>NSBluetoothAlwaysUsageDescription</key>
<string>AetherLink uses Bluetooth Low Energy to detect when your Android phone is near your Mac and establish an instant local link.</string>
```

### B. Hardened Runtime Entitlements (`AetherLink.entitlements`)
For macOS Sandboxed & Notarized distributions:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Network Access -->
    <key>com.apple.security.network.client</key>
    <true/>
    <key>com.apple.security.network.server</key>
    <true/>
    
    <!-- Audio & Camera Hardware -->
    <key>com.apple.security.device.audio-input</key>
    <true/>
    <key>com.apple.security.device.camera</key>
    <true/>
    <key>com.apple.security.device.bluetooth</key>
    <true/>
    
    <!-- Local User Notifications -->
    <key>com.apple.security.personal-information.addressbook</key>
    <false/>
</dict>
</plist>
```
