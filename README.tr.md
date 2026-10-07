<div align="center">

# ⚡ AetherLink
### Android ile macOS Arasındaki Kayıp Süreklilik (Continuity) Köprüsü

[![Version: v1.9.0](https://img.shields.io/badge/S%C3%BCr%C3%BCm-v1.9.0-purple.svg)](https://github.com/mehmetsensoyme/aetherlink/releases)
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

Hücresel telefon aramaları, VoIP görüşmeleri (WhatsApp, Telegram vb.), doğrudan Mac'ten yanıtlanabilen bildirimler, evrensel pano, kablosuz ekran yansıtma, kablosuz stüdyo kamerası, sıfır-ADB kablosuz ses köprüsü ve masaüstü araç konsolunu tek bir çatı altında birleştirir.

Sistem, en yüksek performans ve pil tasarrufu için Mac tarafında **Swift & SwiftUI**, Android tarafında ise **Kotlin & Jetpack Compose** ile yerel (native) olarak inşa edilmiştir.

---

## 🌟 Temel Özellikler

* 🎵 **AetherAudio Sıfır-ADB Kablosuz Ses Köprüsü:**
  Bluetooth eşleştirmesine ya da ADB/Geliştirici Seçeneklerine gerek kalmadan, Spotify ve telefon medya seslerini yerel UDP (port 8446) üzerinden Mac stüdyo hoparlörlerine kristal netliğinde aktarın. Donanımsal yankı engellemeli (AEC) çift yönlü Mac mikrofonlu arama köprüsünü destekler.
* 🚗 **Aether Auto (Masaüstü Araç Konsolu):**
  Android Auto ve Apple CarPlay ilhamlı 780×500 boyutunda geniş kontrol paneli; dev medya kontrolleri, ses ayarları, 3×4 numerik telefon tuş takımı ve tek tıkla arama yapma olanağı sunar.
* 📱 **Kablosuz Canlı Ekran Yansıtma (iPhone Mirroring Alternatifi):**
  Android telefon ekranınızı sıfır bulut ile, doğrudan yerel ağ veya USB/ADB üzerinden saniyede 30 kare (FPS) hızında Mac masaüstünüzdeki yüzen pencereye canlı olarak yansıtın.
* 📞 **Hücresel & VoIP Arama Yansıtma:**
  Gelen normal telefon aramalarını, WhatsApp ve Telegram çağrılarını doğrudan Mac ekranında açılan yerel arama penceresinden yanıtlayın veya reddedin. WebRTC destekli iki yönlü kristal netliğinde ses akışı.
* 💬 **Tüm Uygulamalardan Bildirimler & Doğrudan Cevap:**
  WhatsApp, Instagram DM, Telegram, SMS ve Slack bildirimleri anında macOS Bildirim Merkezi'ne düşer. Telefonunuzu elinize almadan bildirimdeki kutucuğa yazıp `Enter`a basarak Mac'ten yanıt verin.
* 📋 **Evrensel Pano (Universal Clipboard):**
  Mac'te kopyalayın (`Cmd+C`), Android'de yapıştırın (`Yapıştır`). Metin, link ve görselleri döngüye girmeden anlık olarak eşitler.
* 🔋 **İki Yönlü Donanımsal Şarj & Güç Durumu Senkronizasyonu:**
  Gerçek Apple IOKit ve Android BatteryManager sensörleri. Mac'in şarj durumunu telefonda, telefonun şarj durumunu Mac menü çubuğunda canlı izleyin.
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

## 🙏 Teşekkürler & Krediler

AetherLink projesi, açık kaynak ekosistem öncü projelerinden ilham almış ve onların güçlü altyapılarını kullanmaktadır:
* **[scrcpy](https://github.com/Genymobile/scrcpy) - [Genymobile](https://github.com/Genymobile):** Endüstri standardı haline gelmiş ultra düşük gecikmeli, 60 FPS yüksek performanslı Android ekran yansıtma ve kontrol motoru için sonsuz teşekkürler.
* **[FFmpeg](https://ffmpeg.org/):** Donanımsal video akışı çözme ve ses işleme altyapısı için.
* **[SDL (Simple DirectMedia Layer)](https://www.libsdl.org/):** Düşük gecikmeli pencereleme, grafik çıktısı ve giriş yönetimi için.

---

## 📄 Lisans

Bu proje **MIT Lisansı** ile lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına göz atabilirsiniz.
