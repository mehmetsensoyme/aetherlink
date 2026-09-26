import AppKit
import SwiftUI

// MARK: - Pairing QR & Confirmation Code View
public struct PairingQRView: View {
    @ObservedObject var pairing = PairingManager.shared
    @ObservedObject var network = NetworkManager.shared
    let onDismiss: () -> Void
    
    public var body: some View {
        VStack(spacing: 16) {
            // Title
            HStack {
                Image(systemName: "qrcode.viewfinder")
                    .font(.title2)
                    .foregroundColor(.blue)
                Text("AetherLink Cihaz Eşleştirme")
                    .font(.headline)
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
            }
            
            Divider()
            
            // Generated QR Code
            if let qrImage = QRCodeGenerator.generateQRCode(from: pairing.pairingPayloadUrl, size: CGSize(width: 170, height: 170)) {
                Image(nsImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 170, height: 170)
                    .padding(8)
                    .background(Color.white)
                    .cornerRadius(12)
                    .shadow(color: .black.opacity(0.1), radius: 4)
            }
            
            // 6-digit PIN Confirmation Badge
            VStack(spacing: 4) {
                Text("Eşleşme Onay Kodu:")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Text(pairing.currentConfirmationCode)
                    .font(.system(size: 24, weight: .bold, design: .monospaced))
                    .tracking(2)
                    .foregroundColor(.primary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.blue.opacity(0.1))
                    )
            }
            
            Text("Telefonunuzda AetherLink uygulamasından bu QR kodu tarayın veya aynı yerel ağdayken otomatik bulunmasını sağlayın.")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 8)
            
            Divider()
            
            HStack {
                Text("Yerel IP: \(network.localIPAddress):8443")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Button("Yeni Kod Üret") {
                    pairing.generateNewConfirmationCode()
                }
                .font(.caption)
            }
        }
        .padding(18)
        .frame(width: 320)
        .background(RoundedRectangle(cornerRadius: 16).fill(.regularMaterial))
    }
}

// MARK: - Incoming Pairing Request Prompt
public struct PairingPromptView: View {
    let request: PairingRequestPayload
    let onApprove: () -> Void
    let onReject: () -> Void
    
    public var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 36))
                .foregroundColor(.blue)
            
            Text("Yeni Cihaz Bağlanmak İstiyor")
                .font(.headline)
            
            Text("Cihaz Adı: \(request.deviceName)")
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            VStack(spacing: 4) {
                Text("Telefon Ekranındaki Onay Kodu:")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Text(request.confirmationCode)
                    .font(.system(size: 26, weight: .bold, design: .monospaced))
                    .tracking(2)
                    .foregroundColor(.blue)
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 8).fill(Color.primary.opacity(0.05)))
            
            Text("Telefonunuzdaki kod ile yukarıdaki kod aynıysa eşleşmeyi onaylayın.")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            HStack(spacing: 12) {
                Button("Reddet", role: .cancel, action: onReject)
                    .keyboardShortcut(.cancelAction)
                
                Button("Onayla ve Bağlan", action: onApprove)
                    .buttonStyle(.borderedProminent)
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 340)
        .background(RoundedRectangle(cornerRadius: 16).fill(.regularMaterial))
    }
}

// MARK: - Floating Call Banner View
public struct CallBannerView: View {
    @ObservedObject var callManager = CallManager.shared
    
    public var body: some View {
        HStack(spacing: 14) {
            // App / Avatar Icon
            ZStack {
                Circle()
                    .fill(appBadgeColor.opacity(0.2))
                    .frame(width: 48, height: 48)
                
                Image(systemName: appIconName)
                    .font(.system(size: 22))
                    .foregroundColor(appBadgeColor)
            }
            
            // Caller Info
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(appNameLabel)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(appBadgeColor)
                        .textCase(.uppercase)
                    
                    if callManager.isCallActive {
                        Text("• Bağlandı")
                            .font(.caption2)
                            .foregroundColor(.green)
                    }
                }
                
                Text(callManager.activeCall?.callerName ?? "Arayan")
                    .font(.headline)
                    .foregroundColor(.primary)
                    .lineLimit(1)
                
                if let phone = callManager.activeCall?.phoneNumber {
                    Text(phone)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
            
            // Action Buttons
            HStack(spacing: 10) {
                if !callManager.isCallActive {
                    Button(action: {
                        callManager.declineCall()
                    }) {
                        Image(systemName: "phone.down.fill")
                            .foregroundColor(.white)
                            .frame(width: 38, height: 38)
                            .background(Circle().fill(Color.red))
                    }
                    .buttonStyle(.plain)
                    
                    Button(action: {
                        callManager.answerCall()
                    }) {
                        Image(systemName: "phone.fill")
                            .foregroundColor(.white)
                            .frame(width: 38, height: 38)
                            .background(Circle().fill(Color.green))
                    }
                    .buttonStyle(.plain)
                } else {
                    Button(action: {
                        callManager.endCall()
                    }) {
                        Image(systemName: "phone.down.fill")
                            .foregroundColor(.white)
                            .frame(width: 38, height: 38)
                            .background(Circle().fill(Color.red))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
        )
        .frame(width: 340)
    }
    
    private var appBadgeColor: Color {
        switch callManager.activeCall?.appType {
        case .whatsapp: return .green
        case .telegram: return .blue
        default: return .primary
        }
    }
    
    private var appIconName: String {
        switch callManager.activeCall?.appType {
        case .whatsapp: return "message.fill"
        case .telegram: return "paperplane.fill"
        default: return "phone.fill"
        }
    }
    
    private var appNameLabel: String {
        switch callManager.activeCall?.appType {
        case .whatsapp: return "WhatsApp"
        case .telegram: return "Telegram"
        default: return "Hücresel Arama"
        }
    }
}

// MARK: - In-App Update & Changelog Modal View
public struct UpdateModalView: View {
    let updateInfo: UpdateInfo
    let onDismiss: () -> Void
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                Image(systemName: "arrow.triangle.2.circlepath.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(.blue)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("Yeni Sürüm Mevcut!")
                        .font(.title3)
                        .fontWeight(.bold)
                    
                    Text("AetherLink v\(updateInfo.latestVersion) • Mevcut: v\(updateInfo.currentVersion)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
            }
            
            Divider()
            
            Text("Yenilikler ve Değişiklikler:")
                .font(.headline)
            
            ScrollView {
                Text(updateInfo.changelog)
                    .font(.body)
                    .foregroundColor(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
            }
            .frame(height: 180)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.primary.opacity(0.04)))
            
            Divider()
            
            HStack {
                Button("Daha Sonra") {
                    onDismiss()
                }
                .keyboardShortcut(.cancelAction)
                
                Spacer()
                
                Button(action: {
                    if let url = URL(string: updateInfo.downloadUrl) {
                        NSWorkspace.shared.open(url)
                    }
                    onDismiss()
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.down.circle.fill")
                        Text("Şimdi Güncelle (.dmg)")
                    }
                    .padding(.horizontal, 8)
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 460)
        .background(RoundedRectangle(cornerRadius: 16).fill(.regularMaterial))
    }
}

// MARK: - Device Hardware & Telemetry Detail View
public struct DeviceTelemetryDetailView: View {
    @ObservedObject var telemetryMgr = DeviceTelemetryManager.shared
    @ObservedObject var network = NetworkManager.shared
    let onDismiss: () -> Void
    
    public var body: some View {
        VStack(spacing: 16) {
            // Header
            HStack {
                Image(systemName: "iphone.gen3")
                    .font(.title2)
                    .foregroundColor(.blue)
                VStack(alignment: .leading, spacing: 2) {
                    Text(telemetryMgr.telemetry?.model ?? "Galaxy S25 Ultra")
                        .font(.headline)
                    Text("Android \(telemetryMgr.telemetry?.androidVersion ?? "15") • Samsung One UI")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
            }
            
            Divider()
            
            // Grid of specs
            VStack(spacing: 10) {
                // Battery & Temp
                TelemetryRow(
                    icon: "battery.100.bolt",
                    label: "Pil Durumu",
                    value: "\(telemetryMgr.telemetry?.batteryLevel ?? 0)% (\(telemetryMgr.telemetry?.isCharging == true ? "Şarj Oluyor" : "Pilde"))",
                    detail: "\(String(format: "%.1f", telemetryMgr.telemetry?.batteryTempCelsius ?? 28.5))°C • \(telemetryMgr.telemetry?.batteryHealth ?? "İyi")"
                )
                
                // RAM
                TelemetryRow(
                    icon: "memorychip",
                    label: "Bellek (RAM)",
                    value: telemetryMgr.formattedRam,
                    detail: "Kullanılan / Toplam"
                )
                
                // Storage
                TelemetryRow(
                    icon: "internaldrive",
                    label: "Dahili Depolama",
                    value: telemetryMgr.formattedStorage,
                    detail: "\(String(format: "%.1f", telemetryMgr.telemetry?.storageUsedGB ?? 0.0)) GB Dolu"
                )
                
                // Network
                TelemetryRow(
                    icon: "wifi",
                    label: "Ağ & Operatör",
                    value: telemetryMgr.formattedNetwork,
                    detail: telemetryMgr.telemetry?.cellularOperator ?? "Mobil Veri"
                )
                
                // Uptime
                TelemetryRow(
                    icon: "clock",
                    label: "Çalışma Süresi",
                    value: telemetryMgr.formattedUptime,
                    detail: "Açılıştan beri"
                )
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.primary.opacity(0.04)))
            
            Divider()
            
            // Actions
            HStack {
                Button(action: {
                    telemetryMgr.requestTelemetryRefresh()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.clockwise")
                        Text("Yenile")
                    }
                    .font(.caption)
                }
                .buttonStyle(.bordered)
                
                Spacer()
                
                Button(role: .destructive, action: {
                    network.disconnectDevice(forget: false)
                    onDismiss()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "link.badge.slash")
                        Text("Bağlantıyı Kes")
                    }
                    .font(.caption)
                }
                .buttonStyle(.bordered)
            }
        }
        .padding(18)
        .frame(width: 360)
        .background(RoundedRectangle(cornerRadius: 16).fill(.regularMaterial))
    }
}

struct TelemetryRow: View {
    let icon: String
    let label: String
    let value: String
    let detail: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(.blue)
                .frame(width: 24)
            
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                Text(value)
                    .font(.system(size: 13, weight: .semibold))
            }
            
            Spacer()
            
            Text(detail)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
    }
}

// MARK: - Menu Bar Content View
public struct MenuBarContentView: View {
    @ObservedObject var network = NetworkManager.shared
    @ObservedObject var updater = UpdateChecker.shared
    @ObservedObject var pairing = PairingManager.shared
    @ObservedObject var mirror = ScreenMirrorManager.shared
    @ObservedObject var telemetryMgr = DeviceTelemetryManager.shared
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header: Device Status
            HStack(spacing: 10) {
                Circle()
                    .fill(network.isConnected ? Color.green : Color.orange)
                    .frame(width: 10, height: 10)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(network.connectedDeviceName)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    
                    Text(network.isConnected ? "Yerel Ağda Bağlı (AES-256)" : "Cihaz Aranıyor (mDNS)...")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                if let battery = network.batteryState {
                    HStack(spacing: 4) {
                        Text("\(battery.batteryLevel)%")
                            .font(.caption)
                            .fontWeight(.medium)
                        
                        Image(systemName: battery.isCharging ? "battery.100.bolt" : "battery.75")
                            .foregroundColor(battery.batteryLevel < 20 ? .red : .green)
                    }
                }
            }
            .padding(.bottom, 4)
            
            Divider()
            
            // Quick Continuity Actions (Screen Mirroring & Specs)
            HStack(spacing: 8) {
                Button(action: {
                    if mirror.hasScrcpyInstalled {
                        mirror.launchScrcpyMirror(wirelessIp: "192.168.1.4")
                    } else if mirror.isStreaming {
                        mirror.stopStreamRequest()
                    } else {
                        mirror.startStreamRequest()
                    }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: (mirror.isStreaming || mirror.isScrcpyRunning) ? "display.trianglebadge.exclamationmark" : "display")
                        Text((mirror.isStreaming || mirror.isScrcpyRunning) ? "Yansıtmayı Durdur" : (mirror.hasScrcpyInstalled ? "Ekranı Yansıt (Scrcpy 60fps)" : "Ekranı Yansıt"))
                    }
                    .font(.caption)
                }
                .buttonStyle(.borderedProminent)
                .tint((mirror.isStreaming || mirror.isScrcpyRunning) ? .red : .blue)
                
                Button(action: {
                    telemetryMgr.isShowingDetailSheet = true
                    telemetryMgr.requestTelemetryRefresh()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "info.circle")
                        Text("Cihaz Bilgileri")
                    }
                    .font(.caption)
                }
                .buttonStyle(.bordered)
            }
            
            Divider()
            
            // Pairing & Disconnect Action Bar
            HStack {
                Button(action: {
                    pairing.isShowingQRModal = true
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "qrcode")
                        Text(pairing.isPaired ? "Eşleşme Kodu (\(pairing.currentConfirmationCode))" : "Eşleştir (QR & Kod)")
                    }
                    .font(.caption)
                }
                .buttonStyle(.bordered)
                
                Spacer()
                
                if network.isConnected {
                    Button(action: {
                        network.disconnectDevice(forget: false)
                    }) {
                        Text("Bağlantıyı Kes")
                            .font(.caption2)
                            .foregroundColor(.orange)
                    }
                    .buttonStyle(.plain)
                } else if pairing.isPaired {
                    Button(action: {
                        network.disconnectDevice(forget: true)
                    }) {
                        Text("Cihazı Unut")
                            .font(.caption2)
                            .foregroundColor(.red)
                    }
                    .buttonStyle(.plain)
                }
            }
            
            Divider()
            
            // Media Widget
            if let media = network.mediaState, media.isPlaying {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Image(systemName: "music.note")
                            .foregroundColor(.pink)
                        Text(media.trackTitle)
                            .font(.caption)
                            .fontWeight(.medium)
                            .lineLimit(1)
                    }
                    Text(media.artist)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .padding(.leading, 18)
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color.primary.opacity(0.04)))
                
                Divider()
            }
            
            // Feature Quick Status
            VStack(spacing: 6) {
                FeatureRow(icon: "display", title: "Kablosuz Ekran Yansıtma", status: mirror.isStreaming ? "Aktif" : "Hazır")
                FeatureRow(icon: "doc.on.clipboard", title: "Evrensel Pano", status: "Aktif")
                FeatureRow(icon: "bell.badge", title: "Bildirimler & Cevap", status: "Aktif")
                FeatureRow(icon: "phone.fill", title: "Arama Yansıtma", status: "Hazır")
            }
            
            Divider()
            
            // Footer
            HStack {
                Button(action: {
                    Task {
                        await updater.checkForUpdates(manual: true)
                    }
                }) {
                    HStack(spacing: 4) {
                        if updater.isChecking {
                            ProgressView().controlSize(.small)
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                        Text("Güncellemeleri Denetle").font(.caption)
                    }
                }
                .buttonStyle(.plain)
                
                Spacer()
                
                Text("v\(updater.currentVersion)")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                
                Button("Çıkış") {
                    NSApplication.shared.terminate(nil)
                }
                .buttonStyle(.plain)
                .font(.caption)
                .foregroundColor(.red)
            }
        }
        .padding(14)
        .frame(width: 320)
        .sheet(isPresented: Binding(
            get: { pairing.isShowingQRModal },
            set: { pairing.isShowingQRModal = $0 }
        )) {
            PairingQRView {
                pairing.isShowingQRModal = false
            }
        }
        .sheet(isPresented: Binding(
            get: { telemetryMgr.isShowingDetailSheet },
            set: { telemetryMgr.isShowingDetailSheet = $0 }
        )) {
            DeviceTelemetryDetailView {
                telemetryMgr.isShowingDetailSheet = false
            }
        }
        .sheet(isPresented: Binding(
            get: { updater.isShowingSheet },
            set: { updater.isShowingSheet = $0 }
        )) {
            if let info = updater.availableUpdate {
                UpdateModalView(updateInfo: info) {
                    updater.isShowingSheet = false
                }
            }
        }
        .sheet(isPresented: Binding(
            get: { pairing.pendingPairingRequest != nil },
            set: { _ in }
        )) {
            if let req = pairing.pendingPairingRequest {
                PairingPromptView(
                    request: req,
                    onApprove: { pairing.approvePairing(req) },
                    onReject: { pairing.rejectPairing(req) }
                )
            }
        }
    }
}

struct FeatureRow: View {
    let icon: String
    let title: String
    let status: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .font(.caption)
                .frame(width: 16)
                .foregroundColor(.secondary)
            Text(title).font(.caption)
            Spacer()
            Text(status).font(.caption2).foregroundColor(.green)
        }
    }
}
