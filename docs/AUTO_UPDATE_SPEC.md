# 🔄 AetherLink: Auto-Update & Semantic Release Specification

> **Target:** Seamless in-app version checks, GitHub Releases integration, and interactive markdown changelog presentation.

---

## 1. ⚙️ Overview
AetherLink uses a zero-infrastructure, GitHub-native update delivery pipeline:
- **No private hosting required:** GitHub Releases provides free, unlimited, global CDN asset delivery.
- **Privacy-first:** The application queries GitHub's public API directly via HTTPS (`https://api.github.com/repos/{owner}/{repo}/releases/latest`). No user telemetry is attached.
- **Non-intrusive:** Checks run passively on startup and periodically (every 24 hours). The user can dismiss or snooze.

---

## 2. 🎨 In-App Changelog Modal Specification

When an update is detected (`remoteVersion > localVersion`), an interactive modal appears on the client:

```
┌────────────────────────────────────────────────────────┐
│  ⚡ AetherLink Güncellemesi Mevcut!                    │
│  Sürüm v1.1.0 • Yayın Tarihi: 26 Eylül 2026            │
├────────────────────────────────────────────────────────┤
│  Yenilikler ve Değişiklikler (Changelog):             │
│                                                        │
│  ✨ Yeni Özellikler:                                   │
│  • WhatsApp sesli aramaları Mac üzerinden yanıtlanıyor │
│  • Panoda görsel (PNG) kopyala-yapıştır desteği        │
│                                                        │
│  🐛 Hata Düzeltmeleri:                                 │
│  • Wi-Fi ağı değiştiğinde otomatik yeniden bağlanma    │
│  • macOS 15 Sequoia yerel ağ izin uyarısı düzeltildi   │
│                                                        │
├────────────────────────────────────────────────────────┤
│  [ Şimdi İndir ve Güncelle (.dmg) ]    [ Daha Sonra ]  │
└────────────────────────────────────────────────────────┘
```

### Visual Specifications
- **Design Style:** macOS Native Glassmorphism (Translucent material background, subtle borders, system fonts).
- **Changelog Renderer:** Full GitHub-Flavored Markdown (GFM) with support for bullet lists, bold text, code tags, and links.
- **Buttons:**
  - `Primary Action`: Downloads the platform asset (`.dmg` for Mac, `.apk` for Android) and triggers installation.
  - `Secondary Action`: Snoozes the notification for 48 hours or until next manual check.

---

## 3. 🏷️ GitHub Release Workflow & Asset Naming

To guarantee automated asset matching, release builds in GitHub Actions must follow strict naming:

| Platform | Asset Name Pattern | Example |
|:---|:---|:---|
| **macOS (Universal / Apple Silicon)** | `AetherLink-{version}-macOS.dmg` | `AetherLink-v1.1.0-macOS.dmg` |
| **Android (Universal APK)** | `AetherLink-{version}-Android.apk` | `AetherLink-v1.1.0-Android.apk` |

---

## 4. 🧪 Implementation Details
- **Core Engine:** Written in TypeScript (`core-engine/update_checker.ts`), usable across Node, Electron, Tauri, or Swift bridges.
- **Swift / macOS Integration:** Leverages `URLSession` and lightweight JSON decoding directly mapped to `NSAlert` or SwiftUI `sheet(isPresented:)`.
- **Android Integration:** Kotlin coroutine querying `releases/latest` and displaying a Jetpack Compose `AlertDialog` with direct APK download intent.
