# Değişiklik Günlüğü (Changelog)

AetherLink projesine ait tüm önemli değişiklikler, yeni özellikler ve hata düzeltmeleri bu dosyada [Keep a Changelog](https://keepachangelog.com/tr/1.1.0/) ve [Semantic Versioning](https://semver.org/) standartlarına uygun olarak belgelenmektedir.

---

## [1.3.6] - 2026-09-27

### 🚀 Düzeltmeler ve İyileştirmeler (Fixes & Improvements)
- **macOS PopoverRouter Tabanlı Navigasyon, Tavan Kesilmesini Önleme ve Buton Sabitleme:**
  - `PopoverRouter.swift` ile durum tabanlı gezinme mimarisi kuruldu (`ActiveScreen`: `dashboard` 340x350, `pairing` 340x380, `deviceInfo` 340x430, `settings` 340x340).
  - Popover içindeki tüm pencereleri kapatan `@Environment(\.dismiss)` ve `.close()` çağrıları kaldırılarak geri butonları doğrudan `PopoverRouter.shared.popToDashboard()` metoduna bağlandı; pencerenin tamamen kapanması engellendi.
  - `StatusBarController.swift` ile AppKit `NSPopover` ve `NSWindow` boyut geçişleri `NSAnimationContext.runAnimationGroup` ile pürüzsüz animasyonla senkronize edildi; pencere zeminleri `isOpaque = false` ve `backgroundColor = .clear` olarak yapılandırıldı.
  - Tüm alt sayfalardan (`DeviceTelemetryDetailView`, `PairingQRView`, `SettingsView`, `dashboardView`) `.ignoresSafeArea()` ve `.edgesIgnoringSafeArea()` kodları temizlendi; en üst başlık barlarına `.padding(.top, 22)` güvenli alan boşluğu eklenerek tavan kesilmesi (clipping) giderildi.
  - `DeviceTelemetryDetailView` içindeki 3'lü aktivite halkası küçültüldü (36 pt çap, 70 pt yükseklik), donanım liste satırları sıkılaştırıldı, canlı termal göstergesi `📱 Tel: [phoneTemp]°C • 💻 Mac: [macTemp]°C` formatına getirildi ve "Yenile" ile "Bağlantıyı Kes" butonları `.padding(.bottom, 16)` ile sayfanın en altına kalıcı olarak sabitlendi.
  - Ana Kontrol Merkezi pil rozetindeki yüzde metni yatay `HStack`, `.fixedSize(horizontal: true, vertical: true)`, `.lineLimit(1)` ve `Capsule()` ile sınırlandırılarak dikey kırılma sorunu çözüldü.
- **Android Uygulama İçi Güncelleme (OTA / In-App Update) & Güvenli Kurulum:**
  - Android 8 - 16+ uyumluluğu için `AndroidManifest.xml` içine `REQUEST_INSTALL_PACKAGES` ve `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`) izinleri eklendi.
  - `res/xml/file_paths.xml` içinde internal/external cache ve files dizinleri (`.`) tam yetkiyle FileProvider'a açıldı (`Failed to find configured root` hatası kalıcı olarak giderildi).
  - Kurulum öncesi `canRequestPackageInstalls()` kontrolü eklenerek kullanıcı doğrudan `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` ekranına yönlendirildi.
  - İndirilen pakete explicit `grantUriPermission` ve `FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_ACTIVITY_NEW_TASK`, `FLAG_ACTIVITY_CLEAR_TOP` bayrakları sağlandı.
  - **İmzalama (Keystore) Uyuşmazlığı Koruması (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` Önleme):**
    - `app/build.gradle.kts` içinde hem Debug hem de Release derlemelerinin CI ve yerel ortamda paylaşımlı `aetherlink.keystore` anahtarıyla imzalanması sağlandı.
    - `AndroidUpdateChecker` içine imza özeti (Signature SHA-256) kontrolü eklenerek, farklı anahtarla imzalanmış paketlerde sistem çökmesi yerine *"Farklı derleme türü algılandı. Lütfen mevcut sürümü kaldırıp yeni sürümü yükleyin."* uyarısı gösterildi.
  - **Version Code Doğrulaması:** İndirilen paketin `versionCode` değeri mevcut sürümden küçük veya eşitse sistemi kilitlemeden *"Zaten en güncel sürüm yüklü."* uyarısı verildi.
  - **GitHub Releases Otomatik Değişiklik Günlüğü (Changelog):**
    - GitHub Actions pipeline'ına (`release.yml`) `fetch-depth: 0` ve dinamik `Generate Changelog` adımı eklendi; önceki tag'den itibaren yapılan commit'ler `release_notes.md` dosyasına derlenerek `body_path` ile GitHub Release'e bağlandı.
    - Android uygulama içi güncelleme penceresine kaydırılabilir şık "Neler Yeni?" bölümü eklendi; GitHub API `body` içeriği zengin formatla sunuldu (boş gelmesi durumunda otomatik fallback sağlandı).
- **Çift Yönlü Donanım Isı & Telemetri Senkronizasyonu (Mac <-> Android):**
  - macOS tarafında dinamik `IOHIDEventSystemClient` ile Apple Silicon ve Intel işlemcilerde 77 donanım termal sensöründen gerçek sıcaklık ortalaması hesaplandı (`MacThermalService`).
  - Android tarafında `BatteryManager.EXTRA_TEMPERATURE` ve Android 10+ `PowerManager.OnThermalStatusChangedListener` ile batarya ısısı ve termal durumu dinamik olarak Mac'e aktarıldı.
  - Hem Mac popover'ında hem Android ana ekranında ("Karşılıklı Şarj & Güç Durumu" ve "Cihaz Donanım Bilgileri") telefon ve bilgisayar sıcaklıkları için dinamik renk kodlu ısı rozetleri (`<50°C` yeşil, `50-75°C` turuncu, `>75°C` kırmızı) eklendi.
  - Android'e çift yönlü "Donanım & Isı Telemetrisi" detay iletişim penceresi eklendi.
- **macOS Görsel Hizalama ve Taşma Düzeltmeleri:**
  - Ana Dashboard pil rozetindeki dikey 3 satıra kırılma hatası yatay `Capsule()` ve `.fixedSize()` ile çözüldü.
  - Ayarlar ekranındaki alt ve üst beyaz şeritler AppKit layer arka planı ve `.edgesIgnoringSafeArea(.all)` ile giderildi.
  - Donanım Telemetri ekranındaki başlık ve alt buton kesilmeleri üst `14 pt` güvenli alan ve sıkı halka düzeniyle tam oturtuldu.
- **Dinamik Popover Boyutlandırması & Boşluk Giderme (Auto-Sizing Dynamic Layout):**
  - Tüm sayfalardaki yapay sabit yükseklik sınırları (`currentDashboardHeight`, `currentViewHeight`) kaldırılarak, SwiftUI'ın gerçek içerik yüksekliğini hesaplayan `AutoSizingHostingController` ve `AutoSizingNSView` mimarisine geçildi (`width: 360`, `.fixedSize(horizontal: false, vertical: true)`).
  - Popover ve bağımsız pencereler içeriğin dikey boyutuna göre otomatik olarak ölçeklenir, alt kısımda hiçbir ölü boşluk (dead space) veya dikey taşma oluşmaz.
  - Aksiyon butonlarının ve altbilgi (footer) çubuğunun alt boşluğu `12 pt` padding ile pencere sınırına tam oturtuldu.
- **Başlık Metinlerinin Kesilmesini Önleme (Title Truncation Guard):**
  - Uzun tüketici cihaz isimleri (*"Samsung Galaxy S25 Ultra"*) ve *"Yerel Ağda Bağlı • AES-256"* ibarelerinin dar pencerelerde üç nokta ile kesilmesini önlemek amacıyla `.layoutPriority(1)`, `.lineLimit(1)`, `.minimumScaleFactor(0.85)` ve esnek `Spacer(minLength: 8)` eklendi; metinler ile aksiyon butonları arasındaki ayrım korundu.
- **GitHub Actions CI/CD Release Pipeline İyileştirmeleri:**
  - Pipeline'ın süresiz kilitlenmesini engellemek için `timeout-minutes: 25` sınırı konuldu.
  - Release oluşturma ve artifact yükleme yetkisi için `permissions: contents: write` ve tetikleme için `workflow_dispatch` eklendi.
  - macOS derlemesinde keychain şifresi ve provizyonlama profili beklemeden derlemeyi tamamlayan non-interactive ad-hoc imzalama uygulandı.
  - Üretilen DMG ve APK paketleri doğrudan `./artifacts/` dizininde toplanıp `softprops/action-gh-release@v2` aracılığıyla otomatik yayınlanacak şekilde yapılandırıldı.
- **Sahte Telemetri Verilerinin Temizlenmesi (`DeviceInfoView`):**
  - Cihaz bağlı değilken alt başlıkta ve kartlarda görünen sahte model (*"Android 14+ • Samsung"*), sabit sıcaklık (*"28.5°C"*) ve boş donanım halkaları kaldırıldı.
  - Cihaz bağlı değilken modern bir cam kart içinde açıklayıcı **Empty State** mesajı ve doğrudan eşleştirme sayfasına yönlendiren **"Cihaz Eşleştir"** butonu eklendi.
  - Cihaz bağlandığında yalnızca Android soketinden gelen gerçek `DeviceTelemetryPayload` telemetrisi ekrana yansıtılacak şekilde normalize edildi.
- **Bağlantı Öncesi (Disconnected) Durum Mantık Düzeltmeleri:**
  - Üst üste duran iki kart tek bir modern cam kartta birleştirildi: "Bağlı Cihaz Yok" başlığı, sarı sinyal noktası, "Cihaz Aranıyor..." alt metni ve minimal "QR Göster" cam butonu.
  - Altbilgi (footer) çubuğundaki mükerrer "Eşleştir" butonu kaldırıldı; sol tarafta "Bilgiler" ve "Ayarlar", sağ tarafta sürüm ve kırmızı "Çıkış" butonu dengeli tek bir düzleme yerleştirildi.
  - Cihaz bağlı değilken 4'lü servis ızgarası (Ekran Yansıt, Bluetooth Ses, Pano, Bildirim) devre dışı bırakıldı (`.disabled(true)`, `.opacity(0.45)`), sahte yeşil noktalar söndürülerek nötr griye çekildi ve durumları "Bağlantı Yok" yapıldı.
- **Bağlantı Sonrası (Connected) Durum Mantık Düzeltmeleri:**
  - Üst cihaz kartındaki soluk boş daire kaldırıldı; yerine telefonun gerçek pil yüzdesi (`%78`), şarj durumuna göre renklenen SF Symbol pil ikonu ve minimal kırmızı bağlantıyı kes (`power`) butonu eklendi.
  - **Dinamik Model Adı Çözümleyici (`DeviceMarketingNameResolver`):** Fabrika kodu ("SM-S938B") yerine tüketici pazar adı ("Samsung Galaxy S25 Ultra") hem macOS hem de Android (`DeviceUtils`) seviyesinde dinamik olarak çözümlendi.
  - Pasif servislerde ("Ekran Yansıt: Durduruldu") yeşil nokta gösterimi engellendi, nötr gri nokta uygulandı. Aktif servislerde canlı aksan çerçevesi belirginleştirildi.
- **Menü Çubuğu Popover Çökme Koruması:**
  - Popover açılırken dikeyde sıfıra çöküp ince bir yatay şerit olarak kalma sorunu `WindowBackgroundConfigurator` ve açık çerçeve kurallarıyla engellendi.
- **Eşleştirme Ekranı Dikey Taşma ve Kenarlık Düzeltmesi:**
  - QR kod boyutu `130x130` pt seviyesine çekilerek pencere içine kusursuz oturması sağlandı.
  - Pencerenin tavanına yapışmayı engelleyen `16 pt` üst güvenli alan eklendi.
  - Alttaki gereksiz büyük buton kaldırılarak sol üste simetrik `chevron.left` geri butonu yerleştirildi.
- **Tek Aktif Cihaz Politikası:**
  - Telefon bağlıyken eşleştirme arayüzüne ve butonlarına erişim engellendi.
- **Popover İçi Sayfa Gezinmesi ve Geri Butonu Düzeltmesi:**
  - "Cihaz Eşleştirme" ve "Cihaz Donanım Bilgileri" ekranlarındaki sol üst geri (`<`) butonuna tıklandığında popover'ın tamamen kapanmasına yol açan `dismiss()` çağrıları kaldırıldı; durum tabanlı yönlendirme (`ActiveScreen` / `currentPage = .dashboard`) ile akıcı `ZStack` sayfa geçişi sağlandı.
  - Klavye ESC kısayolunun pencereyi kapatmak yerine ana kontrol merkezine (`.dashboard`) dönmesi sağlandı.
  - Sayfa geçişleri sırasında `window.makeKey()` korunarak `NSPopover`'ın transient olarak odağını kaybetmesi ve arka planda kapanması engellendi.

---

## [1.3.5] - 2026-09-27

### 🛠️ Düzeltmeler (Fixed)
- Cihaz eşleştirme ekranındaki dikey taşma ve pencereden dışarı kesilme hatası giderildi.
- Cihaz bağlıyken hem aktif cihaz kartının hem de eşleştirme butonunun görünmesi mantık hatası giderildi.

---

## [1.3.4] - 2026-09-27

### ✨ Yenilikler (Added)
- Cihaz Eşleştirme ve Telemetri ekranlarına belirgin ve sezgisel geri/kapatma butonları (`chevron.left` ve `.keyboardShortcut(.cancelAction)`) eklendi.
- Sayfalar arası geçişi yöneten merkezi `PopoverStateManager` devreye alındı.

---

## [1.3.3] - 2026-09-27

### 🎨 Arayüz Yenilemesi (Liquid Glass & Control Center)
- **Liquid Glass / Glassmorphism:** Opak arka planlar tamamen kaldırılarak macOS `.ultraThinMaterial` ve `NSVisualEffectView` tabanlı cam morfolojisine geçildi.
- **Dinamik Tema Adaptasyonu:** Sistem Koyu/Açık moduna ve sistem aksan rengine (`NSColor.controlAccentColor`) tam uyumluluk sağlandı.
- **Control Center Kutucukları:** Ekran Yansıtma, Ses Yönlendirme, Evrensel Pano ve Bildirimler için modern cam kutucuklar eklendi.
- **Aktivite Halkaları:** RAM, Depolama ve Pil/Sıcaklık durumunu gösteren Apple Health tarzı dinamik halkalar eklendi.

---

## [1.3.2] - 2026-09-26

### 📱 Android Geliştirmeleri (Added)
- macOS tasarım diline uygun 3 adımlı interaktif karşılama (onboarding) akışı eklendi.
- Swift Concurrency thread-safety ve capture uyarıları giderildi.

---

## [1.3.1] - 2026-09-26

### 🖼️ Görsel Varlıklar (Assets)
- macOS 1024x1024 Liquid Glass uygulama ikonu Android uyarlanabilir ikonlarına (tüm mipmap yoğunlukları: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) aktarıldı.

---

## [1.3.0] - 2026-09-26

### 🔄 Dahili Otomatik Güncelleme (In-App Updater)
- macOS için GitHub Releases üzerinden DMG indirme, bağlama ve otomatik yeniden başlatma desteği (`UpdateChecker`).
- Android için GitHub Releases üzerinden APK indirme ve `FileProvider` ile doğrudan kurulum desteği.
- GitHub Actions CI/CD otomatik sürüm ve paketleme pipeline'ı (`.github/workflows/release.yml`).

---

## [1.2.1] - 2026-09-26

### 🔋 macOS Pil Widget'ı (Added)
- macOS Bildirim Merkezi için WidgetKit tabanlı pil göstergesi uzantısı (`AetherLinkWidget`).
- App Group UserDefaults üzerinden telefon pil durumu ve şarj verisinin anlık paylaşımı.

---

## [1.2.0] - 2026-09-26

### 📞 Çağrı & Medya Devamlılığı (Added)
- Çift yönlü hücresel çağrı senkronizasyonu: Gelen çağrılarda macOS HUD bildirimi ve Mac üzerinden cevaplama/reddetme.
- Spotify ve Apple Music akıllı medya devamlılığı (Continuity): Çalan parçanın tek tıkla Mac'teki eşdeğer uygulamada açılması.

---

## [1.1.0] - 2026-09-26

### ⚡ Temel Süreklilik Özellikleri (Core Continuity)
- Düşük gecikmeli kablosuz ekran yansıtma (`scrcpy` 60 FPS video ve ses aktarımı).
- Gerçek zamanlı çift yönlü pil senkronizasyonu (macOS IOKit & Android BatteryManager).
- Çift yönlü şifreli Evrensel Pano (Universal Clipboard) senkronizasyonu.
- Yerel ağ üzerinde otomatik mDNS & UDP Broadcast cihaz keşfi ve AES-256 el sıkışması.
