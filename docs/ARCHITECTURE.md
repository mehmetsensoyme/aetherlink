# 🏗️ AetherLink: Deep Architecture & Technical Specification

> **Mission:** Build a high-performance, battery-friendly, zero-cloud continuity bridge between Android and macOS that rivals native Apple iOS/Mac continuity.

---

## 1. 🌐 Peer-to-Peer Communication Topology

AetherLink never sends unencrypted data over the public internet. All connectivity is strictly **local peer-to-peer (P2P)**.

```
┌─────────────────────────────────┐                 ┌─────────────────────────────────┐
│         Android Phone           │                 │           Apple Mac             │
│                                 │                 │                                 │
│  [AetherCoreService]            │   BLE Beacon    │  [MenuBar Status App]           │
│  - BLE Advertiser ──────────────┼────────────────►│  - BLE Central Scanner          │
│  - mDNS Publisher (_aetherlink) │                 │  - Bonjour Discovery            │
│                                 │                 │                                 │
│  [Local TLS WebSocket Server]   │◄── TLS WS ────► │  [Local WebSocket Client]       │
│  - Port 8443 (Local Only)       │  (JSON Control) │  - Port 8443                    │
│                                 │                 │                                 │
│  [WebRTC Audio Engine]          │◄── WebRTC ────► │  [CoreAudio Virtual Engine]     │
│  - Opus Audio Stream            │  (Low Latency)  │  - Low-latency Mic & Speaker    │
└─────────────────────────────────┘                 └─────────────────────────────────┘
```

---

## 2. 🔐 Zero-Trust Device Pairing (QR Code Handshake)

To prevent unauthorized devices on the local Wi-Fi from eavesdropping:
1. **Initial Setup:** The Mac client displays a cryptographically generated QR code containing:
   - Mac's local IP address and ephemeral port.
   - Public ECDH Key (Curve25519).
   - Random 32-byte pairing token.
2. **Scan:** The Android camera scans the QR code.
3. **Key Agreement:** Android and Mac compute a shared secret key via ECDH (Elliptic-curve Diffie–Hellman).
4. **Encryption:** All subsequent WebSocket payloads are encrypted using **AES-256-GCM** with unique nonces.

---

## 3. 📞 Telephony & VoIP Call Bridging Pipeline

```
[ Incoming Cellular / WhatsApp / Telegram Call ]
                       │
                       ▼
            [ AetherInCallService ]
  (Reads Call State, Caller Name, Phone Number)
                       │
                       ▼ (JSON: CALL_INCOMING)
            [ Local WebSocket Tunnel ]
                       │
                       ▼
            [ macOS Menu Bar App ]
  (Plays Ringtone + Displays Native macOS Call Banner)
                       │
        ┌──────────────┴──────────────┐
        │                             │
        ▼ (User clicks "Answer")      ▼ (User clicks "Decline")
  [ Send: CALL_ACTION.answer ]  [ Send: CALL_ACTION.decline ]
        │                             │
        ▼                             ▼
  Android Answers Line           Android Drops Call
        │
        ▼
  [ WebRTC Voice Tunnel Established ]
  Mac Mic -> Opus -> Android Speaker
  Android Mic -> Opus -> Mac Speaker
```

---

## 4. 💬 Universal Notification & RemoteInput Pipeline

How AetherLink enables replying to WhatsApp, Telegram, and SMS directly from the Mac:

1. **Capture:** `AetherNotificationListener` intercepts the incoming notification on Android.
2. **Check Replyability:** The service checks if the notification has a `NotificationCompat.Action` with `RemoteInput`.
3. **Dispatch:** Serialized notification payload (title, text, app icon, reply token) sent to Mac.
4. **Display:** Mac displays native notification via `UNUserNotificationCenter` with `UNTextInputNotificationAction`.
5. **Reply Execution:**
   - User types reply on Mac and presses Enter.
   - Mac sends `NOTIFICATION_REPLY` payload back to Android.
   - Android constructs an `Intent`, attaches the text via `RemoteInput.addResultsToIntent()`, and executes `PendingIntent.send()`.
   - The message is sent through WhatsApp/SMS without waking up the phone's screen.

---

## 5. 📋 Universal Clipboard Sync Pipeline

- **Loop Prevention:** Every clipboard update includes a `sourceDevice` tag ('macos' or 'android') and a SHA-256 hash of the content.
- **Debounce:** 300ms debounce prevents rapid typing spam.
- **Privacy:** Passwords copied from Bitwarden/1Password (flagged as `org.freedesktop.Secret` or Android `ClipDescription.EXTRA_IS_SENSITIVE`) are automatically skipped by default.
