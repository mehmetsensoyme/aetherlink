<div align="center">

# ⚡ AetherLink
### Android ile macOS Arasındaki Kayıp Süreklilik (Continuity) Köprüsü

[![License: MIT](https://img.shields.io/badge/Lisans-MIT-blue.svg)](LICENSE)
[![Platform: macOS](https://img.shields.io/badge/Platform-macOS%2014%2B%20%7C%2015%20Sequoia-black)](macos-client)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%2014%20%7C%2015%20%7C%2016-green)](android-client)
[![Gizlilik: %100 P2P](https://img.shields.io/badge/Gizlilik-%25100%20Yerel%20P2P-brightgreen)](#-gizlilik-ve-sıfır-bulut-garantisi)
[![Otomatik Güncelleme](https://img.shields.io/badge/Oto--G%C3%BCncelleme-GitHub%20Releases%20API-orange)](docs/AUTO_UPDATE_SPEC.md)

[**Click here for English Documentation (README.md)**](README.md)

</div>

---

## 📖 Genel Bakış

**AetherLink**, Apple ekosisteminin en sevilen "Süreklilik" (Continuity) deneyimini Android telefonlar ile Mac bilgisayarlar arasına getiren açık kaynaklı bir köprüdür. 

Hücresel telefon aramaları, VoIP görüşmeleri (WhatsApp, Telegram vb.), doğrudan Mac'ten yanıtlanabilen bildirimler, ortak pano ve kablosuz stüdyo kamerası gibi tüm kritik özellikleri tek bir çatı altında birleştirir.

Sistem, en yüksek performans ve pil tasarrufu için Mac tarafında **Swift & SwiftUI**, Android tarafında ise **Kotlin & Jetpack Compose** ile yerel (native) olarak inşa edilmiştir.

---

## 🌟 Temel Özellikler

* 📞 **Hücresel & VoIP Arama Yansıtma:**
  Gelen normal telefon aramalarını, WhatsApp ve Telegram çağrılarını doğrudan Mac ekranında açılan yerel arama penceresinden yanıtlayın veya reddedin. WebRTC destekli iki yönlü kristal netliğinde ses akışı.
* 💬 **Tüm Uygulamalardan Bildirimler & Doğrudan Cevap:**
  WhatsApp, Instagram DM, Telegram, SMS ve Slack bildirimleri anında macOS Bildirim Merkezi'ne düşer. Telefonunuzu elinize almadan bildirimdeki kutucuğa yazıp `Enter`a basarak Mac'ten yanıt verin.
* 📋 **Evrensel Pano (Universal Clipboard):**
  Mac'te kopyalayın (`Cmd+C`), Android'de yapıştırın (`Yapıştır`). Metin, link ve görselleri döngüye girmeden anlık olarak eşitler.
* 📷 **Süreklilik Kamerası (Continuity Camera):**
  Android telefonunuzun gelişmiş kamerasını (örneğin Galaxy S25 Ultra'nın 200 MP sensörünü) FaceTime, Zoom ve Google Meet'te kablosuz stüdyo web kamerası olarak kullanın.
* 🔋 **Pil Durumu & Medya Kontrolleri:**
  Telefonun şarj yüzdesini ve şarj durumunu doğrudan Mac menü çubuğunda görün. Spotify ve YouTube çalan müzikleri Mac'ten durdurup geçin.
* 🔄 **GitHub Releases Tabanlı Otomatik Güncelleme:**
  Yeni bir sürüm yayınladığınızda uygulama içinde beliren cam efektli (Glassmorphism) modal ile kullanıcılar tek tıkla güncelleme yapabilir ve sürüm notlarını (changelog) inceleyebilir.

---

## 🔒 Gizlilik ve Sıfır-Bulut Garantisi

1. **Merkezi Sunucu Yok:** AetherLink tamamen yerel Wi-Fi ağınız ve Bluetooth Düşük Enerji (BLE) protokolü üzerinden çalışır.
2. **Kriptografik Eşleşme:** İlk kurulumda Mac ekranındaki QR kod telefonla taranır, Curve25519 (ECDH) ile paylaşımlı gizli anahtar üretilir ve tüm yerel paketler **AES-256-GCM** ile şifrelenir.
3. **KVKK / GDPR Güvencesi:** Sohbetleriniz, arama loglarınız ve panonuz asla bir üçüncü taraf sunucuya veya buluta iletilmez.

---

## 📁 Proje Dizin Yapısı

```
aetherlink/
├── README.md               # İngilizce Dokümantasyon
├── README.tr.md            # Türkçe Dokümantasyon
├── LICENSE                 # MIT Lisansı
├── docs/                   # Teknik Şartnameler ve Mimari Belgeleri
│   ├── ARCHITECTURE.md     # P2P Mimarisi & Arama/Bildirim Tünelleri
│   ├── PERMISSIONS_GUIDE.md# Android 14-16 ve macOS 15 Sequoia İzin Rehberi
│   └── AUTO_UPDATE_SPEC.md # GitHub Releases Otomatik Güncelleme Şartnamesi
├── core-engine/            # Ortak Protokol ve Güncelleme Motoru
│   ├── protocol.ts         # P2P Mesaj Sözleşmeleri ve Veri Tipleri
│   └── update_checker.ts   # Evrensel GitHub Release Güncelleyici
├── macos-client/           # Native macOS SwiftUI Menü Çubuğu Uygulaması
│   ├── Info.plist          # macOS 15 Sequoia Yerel Ağ İzinleri & Bonjour
│   └── AetherLink.entitlements # Hardened Runtime Ağ Yetkilendirmeleri
└── android-client/         # Modern Android Kotlin Arka Plan Servisi
    └── app/src/main/
        └── AndroidManifest.xml # Android 14+ Tipli Ön Plan Servisleri
```

---

## 🛠️ İzinler ve Sistem Gereksinimleri

macOS 15 Sequoia'nın yeni `NSLocalNetworkUsageDescription` kuralı ve Android 14+ `FOREGROUND_SERVICE_CONNECTED_DEVICE` / `PHONE_CALL` tipli servisleri dahil tüm detaylı izin matrisi için [docs/PERMISSIONS_GUIDE.md](docs/PERMISSIONS_GUIDE.md) belgesini inceleyebilirsiniz.

---

## 📄 Lisans

Bu proje **MIT Lisansı** ile lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına göz atabilirsiniz.
