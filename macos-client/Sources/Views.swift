import AppKit
import SwiftUI

// MARK: - Navigation Hierarchy for Popover
public enum PopoverPage: String, Equatable {
    case dashboard
    case telemetry
    case pairing
    case settings
}

// MARK: - Pulsing Connection Dot Indicator
public struct PulsingIndicatorView: View {
    let isConnected: Bool
    
    public init(isConnected: Bool) {
        self.isConnected = isConnected
    }
    
    public var body: some View {
        ZStack {
            Circle()
                .fill(isConnected ? Color.green : Color.orange)
                .frame(width: 8, height: 8)
            
            Circle()
                .stroke(isConnected ? Color.green : Color.orange, lineWidth: 1.5)
                .frame(width: 16, height: 16)
                .opacity(isConnected ? 0.6 : 0.25)
        }
        .frame(width: 20, height: 20)
    }
}

// MARK: - Glassframed QR Code View
public struct GlassQRCodeCard: View {
    @Environment(\.colorScheme) private var colorScheme
    let payloadUrl: String
    
    public var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(colorScheme == .dark ? Color.white.opacity(0.95) : Color.white)
                .shadow(
                    color: Color.black.opacity(colorScheme == .dark ? 0.25 : 0.10),
                    radius: 8,
                    x: 0,
                    y: 4
                )
            
            if let qrImage = QRCodeGenerator.generateQRCode(from: payloadUrl, size: CGSize(width: 114, height: 114)) {
                Image(nsImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .padding(8)
            } else {
                ProgressView()
            }
        }
        .frame(width: 130, height: 130)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(
                    LinearGradient(
                        colors: [Color.white.opacity(0.8), Color.white.opacity(0.2)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1
                )
        )
    }
}

// MARK: - Device Telemetry Detail View ("Cihaz Bilgileri")
public struct DeviceTelemetryDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var telemetryMgr = DeviceTelemetryManager.shared
    @ObservedObject var network = NetworkManager.shared
    var showInlineBack: Bool = false
    var onBack: (() -> Void)? = nil
    var onDismiss: (() -> Void)? = nil
    
    public init(
        showInlineBack: Bool = false,
        onBack: (() -> Void)? = nil,
        onDismiss: (() -> Void)? = nil
    ) {
        self.showInlineBack = showInlineBack
        self.onBack = onBack
        self.onDismiss = onDismiss
    }
    
    private var telemetry: DeviceTelemetryPayload? {
        telemetryMgr.telemetry
    }
    
    private var ramProgress: Double {
        guard let t = telemetry, t.ramTotalMB > 0 else { return 0.0 }
        return Double(t.ramUsedMB) / Double(t.ramTotalMB)
    }
    
    private var storageProgress: Double {
        guard let t = telemetry, t.storageTotalGB > 0 else { return 0.0 }
        return Double(t.storageUsedGB) / Double(t.storageTotalGB)
    }
    
    private var batteryProgress: Double {
        guard let t = telemetry else { return 0.0 }
        return Double(t.batteryLevel) / 100.0
    }
    
    private func handleBack() {
        if let onBack = onBack {
            onBack()
        } else if let onDismiss = onDismiss {
            onDismiss()
        }
        PopoverStateManager.shared.currentPage = .dashboard
        dismiss()
    }
    
    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 10) {
                // Header (Symmetrical: Back Button, Centered Title, Dummy Spacer)
                HStack {
                    Button(action: {
                        handleBack()
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(nsColor: .controlAccentColor))
                            .frame(width: 24, height: 24)
                            .background(Circle().fill(Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.06)))
                    }
                    .buttonStyle(.plain)
                    .help("Geri")
                    
                    Spacer()
                    
                    VStack(spacing: 1) {
                        Text(telemetry?.model ?? (network.connectedDeviceName.isEmpty || network.connectedDeviceName == "Bağlantı Kesildi" ? "Android Cihazı" : network.connectedDeviceName))
                            .font(.headline)
                            .lineLimit(1)
                        Text("Android \(telemetry?.androidVersion ?? "14+") • \(telemetry?.manufacturer ?? "Samsung")")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer()
                    
                    Color.clear
                        .frame(width: 24, height: 24)
                }
                .padding(.horizontal, 2)
                
                // Apple Health / Activity Style Progress Rings (RAM, Storage, Battery - max 90pt height)
                HStack(spacing: 8) {
                    ActivityRingView(
                        progress: ramProgress,
                        ringColor: Color.blue,
                        ringWidth: 5.5,
                        diameter: 40,
                        icon: "memorychip",
                        title: "RAM",
                        valueText: telemetryMgr.formattedRam.components(separatedBy: "(").first?.trimmingCharacters(in: .whitespaces) ?? "--",
                        subtitle: "\(Int(ramProgress * 100))% Dolu"
                    )
                    .frame(maxWidth: .infinity)
                    .glassCard(cornerRadius: 12, padding: 6)
                    
                    ActivityRingView(
                        progress: storageProgress,
                        ringColor: Color.purple,
                        ringWidth: 5.5,
                        diameter: 40,
                        icon: "internaldrive",
                        title: "Depolama",
                        valueText: "\(String(format: "%.0f", telemetry?.storageUsedGB ?? 0.0)) GB",
                        subtitle: "\(String(format: "%.0f", telemetry?.storageTotalGB ?? 0.0)) GB Toplam"
                    )
                    .frame(maxWidth: .infinity)
                    .glassCard(cornerRadius: 12, padding: 6)
                    
                    ActivityRingView(
                        progress: batteryProgress,
                        ringColor: Color.green,
                        ringWidth: 5.5,
                        diameter: 40,
                        icon: telemetry?.isCharging == true ? "bolt.fill" : "battery.100",
                        title: "Pil & Isı",
                        valueText: "\(telemetry?.batteryLevel ?? 0)%",
                        subtitle: "\(String(format: "%.1f", telemetry?.batteryTempCelsius ?? 28.5))°C"
                    )
                    .frame(maxWidth: .infinity)
                    .glassCard(cornerRadius: 12, padding: 6)
                }
                
                // Hardware Telemetry Spec Rows (Tightened padding)
                VStack(spacing: 6) {
                    TelemetrySpecRow(
                        icon: "wifi",
                        label: "Kablosuz Ağ & Hız",
                        value: telemetryMgr.formattedNetwork,
                        accentColor: .blue
                    )
                    
                    TelemetrySpecRow(
                        icon: "antenna.radiowaves.left.and.right",
                        label: "Hücresel Bağlantı",
                        value: telemetry?.cellularOperator ?? "Mobil Veri",
                        accentColor: .indigo
                    )
                    
                    TelemetrySpecRow(
                        icon: "clock.arrow.circlepath",
                        label: "Sistem Çalışma Süresi",
                        value: telemetryMgr.formattedUptime,
                        accentColor: .orange
                    )
                    
                    TelemetrySpecRow(
                        icon: "heart.text.square.fill",
                        label: "Pil Sağlığı",
                        value: "\(telemetry?.batteryHealth ?? "İyi") • \(telemetry?.isCharging == true ? "Hızlı Şarj" : "Deşarj")",
                        accentColor: .green
                    )
                }
                .glassCard(cornerRadius: 12, padding: 8)
                
                // Action Buttons (Yenile & Bağlantıyı Kes)
                HStack(spacing: 10) {
                    Button(action: {
                        telemetryMgr.requestTelemetryRefresh()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.clockwise")
                            Text("Yenile")
                        }
                        .font(.caption.weight(.medium))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 7)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "refresh_telemetry", isActive: false)
                    
                    Button(role: .destructive, action: {
                        network.disconnectDevice(forget: false)
                        handleBack()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "link.badge.slash")
                            Text("Bağlantıyı Kes")
                        }
                        .font(.caption.weight(.medium))
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 7)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "disconnect_telemetry", isActive: false, activeTint: .red)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 12)
        }
        .frame(width: 320, height: 420)
        .background(.ultraThinMaterial)
        .keyboardShortcut(.cancelAction)
    }
}

// MARK: - Telemetry Spec Row
struct TelemetrySpecRow: View {
    let icon: String
    let label: String
    let value: String
    let accentColor: Color
    
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(accentColor)
                .frame(width: 22, height: 22)
                .background(Circle().fill(accentColor.opacity(0.12)))
            
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            
            Spacer()
            
            Text(value)
                .font(.caption.weight(.semibold))
                .foregroundColor(.primary)
                .lineLimit(1)
        }
    }
}

// MARK: - Pairing QR View ("Cihaz Eşleştirme")
public struct PairingQRView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var pairing = PairingManager.shared
    @ObservedObject var network = NetworkManager.shared
    var showInlineBack: Bool = false
    var onBack: (() -> Void)? = nil
    var onDismiss: (() -> Void)? = nil
    
    public init(
        showInlineBack: Bool = false,
        onBack: (() -> Void)? = nil,
        onDismiss: (() -> Void)? = nil
    ) {
        self.showInlineBack = showInlineBack
        self.onBack = onBack
        self.onDismiss = onDismiss
    }
    
    private func handleBack() {
        if let onBack = onBack {
            onBack()
        } else if let onDismiss = onDismiss {
            onDismiss()
        }
        PopoverStateManager.shared.currentPage = .dashboard
        dismiss()
    }
    
    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 12) {
                // 1. Single-line Clean Header Bar (Symmetrical with Top Safe Area)
                HStack {
                    Button(action: {
                        handleBack()
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(nsColor: .controlAccentColor))
                            .frame(width: 24, height: 24)
                            .background(Circle().fill(Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.06)))
                    }
                    .buttonStyle(.plain)
                    .help("Geri")
                    
                    Spacer()
                    
                    Text("Cihaz Eşleştirme")
                        .font(.headline)
                    
                    Spacer()
                    
                    Color.clear
                        .frame(width: 24, height: 24)
                }
                
                // 2. Compact Glass-framed QR Code Card (130x130)
                GlassQRCodeCard(payloadUrl: pairing.pairingPayloadUrl)
                    .padding(.top, 2)
                
                // 3. Compact Confirmation Code Card (SF Mono, padding 8, size 22)
                VStack(spacing: 2) {
                    Text("Eşleşme Onay Kodu")
                        .font(.caption2)
                        .fontWeight(.medium)
                        .foregroundColor(.secondary)
                    
                    Text(pairing.currentConfirmationCode)
                        .font(.system(size: 22, weight: .bold, design: .monospaced))
                        .tracking(3)
                        .foregroundColor(Color(nsColor: .controlAccentColor))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .glassCard(cornerRadius: 12, padding: 0, isHighlighted: true)
                
                // 4. Instructions Text (2 lines max, fixedSize)
                Text("Telefonunuzdaki AetherLink uygulamasından bu QR kodu okutun.")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 4)
                
                // 5. Local IP & Refresh Button Row
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: "network")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Text("\(network.localIPAddress):8443")
                            .font(.caption2.monospaced())
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer()
                    
                    Button(action: {
                        pairing.generateNewConfirmationCode()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                            Text("Yeni Kod")
                        }
                        .font(.caption2.weight(.medium))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "new_pairing_code", isActive: false)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 16)
        }
        .frame(width: 320, height: 390)
        .background(.ultraThinMaterial)
        .keyboardShortcut(.cancelAction)
        .onReceive(network.$isConnected) { isConnected in
            if isConnected {
                handleBack()
            }
        }
    }
}

// MARK: - In-App Update Modal View
public struct UpdateModalView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var updater = UpdateChecker.shared
    let updateInfo: UpdateInfo
    let onDismiss: () -> Void
    
    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    Image(systemName: "arrow.triangle.2.circlepath.circle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(Color(nsColor: .controlAccentColor))
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Yeni Sürüm Mevcut!")
                            .font(.title3.bold())
                        Text("AetherLink v\(updateInfo.latestVersion) • Mevcut: v\(updateInfo.currentVersion)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }
                
                VStack(alignment: .leading, spacing: 6) {
                    Text("Yenilikler ve İyileştirmeler:")
                        .font(.caption.bold())
                        .foregroundColor(.secondary)
                    
                    ScrollView {
                        Text(updateInfo.changelog)
                            .font(.caption)
                            .foregroundColor(.primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(10)
                    }
                    .frame(height: 120)
                    .glassCard(cornerRadius: 12, padding: 0)
                }
                
                if updater.isDownloading {
                    VStack(alignment: .leading, spacing: 4) {
                        ProgressView(value: updater.downloadProgress, total: 1.0)
                            .progressViewStyle(.linear)
                        
                        HStack {
                            Text(updater.installStatusText ?? "İndiriliyor...")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("%\(Int(updater.downloadProgress * 100))")
                                .font(.caption2.bold())
                                .foregroundColor(Color(nsColor: .controlAccentColor))
                        }
                    }
                }
                
                if let error = updater.installError {
                    HStack(spacing: 6) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.red)
                        Text(error)
                            .font(.caption2)
                            .foregroundColor(.red)
                    }
                }
                
                HStack {
                    Button("Daha Sonra") {
                        onDismiss()
                        dismiss()
                    }
                    .disabled(updater.isDownloading)
                    .buttonStyle(.plain)
                    .foregroundColor(.secondary)
                    .font(.caption)
                    
                    Spacer()
                    
                    if updater.isDownloading {
                        HStack(spacing: 6) {
                            ProgressView().controlSize(.small)
                            Text("Güncelleniyor...").font(.caption)
                        }
                    } else {
                        Button(action: {
                            Task {
                                await updater.downloadAndInstallUpdate(update: updateInfo)
                            }
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.down.circle.fill")
                                Text("Şimdi Güncelle")
                            }
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                        .glassTile(id: "update_now", isActive: true)
                    }
                }
            }
            .padding(16)
        }
        .frame(width: 440, height: 390)
        .background(.ultraThinMaterial)
    }
}

// MARK: - Incoming Pairing Request Prompt (Floating Panel)
public struct PairingPromptView: View {
    @Environment(\.dismiss) private var dismiss
    let request: PairingRequestPayload
    let onApprove: () -> Void
    let onReject: () -> Void
    
    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 10) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 30))
                    .foregroundColor(Color(nsColor: .controlAccentColor))
                
                Text("Yeni Cihaz Bağlantı İsteği")
                    .font(.headline)
                
                Text("Cihaz: \(request.deviceName)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                
                VStack(spacing: 2) {
                    Text("Eşleşme Kodu:")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Text(request.confirmationCode)
                        .font(.system(size: 24, weight: .bold, design: .monospaced))
                        .tracking(3)
                        .foregroundColor(Color(nsColor: .controlAccentColor))
                }
                .glassCard(cornerRadius: 12, padding: 8, isHighlighted: true)
                
                Text("Telefon ekranındaki kod ile yukarıdaki kod aynıysa onaylayın.")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                
                HStack(spacing: 10) {
                    Button("Reddet", role: .cancel) {
                        onReject()
                        dismiss()
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "reject_pairing", isActive: false, activeTint: .red)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    
                    Button("Onayla ve Bağlan") {
                        onApprove()
                        dismiss()
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "approve_pairing", isActive: true)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }
            }
            .padding(16)
        }
        .frame(width: 330, height: 340)
        .background(.ultraThinMaterial)
    }
}

// MARK: - Floating Call Banner View
public struct CallBannerView: View {
    @ObservedObject var callManager = CallManager.shared
    
    private var isOutgoing: Bool {
        callManager.activeCall?.direction == "outgoing"
    }
    
    public var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(appBadgeColor.opacity(0.18))
                    .frame(width: 44, height: 44)
                
                Image(systemName: appIconName)
                    .font(.system(size: 20))
                    .foregroundColor(appBadgeColor)
            }
            
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(appNameLabel)
                        .font(.caption2.weight(.bold))
                        .foregroundColor(appBadgeColor)
                        .textCase(.uppercase)
                    
                    if callManager.isCallActive {
                        Text("• Bağlandı")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.green)
                    } else if isOutgoing {
                        Text("• Aranıyor...")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.orange)
                    }
                }
                
                Text(callManager.activeCall?.callerName ?? (isOutgoing ? "Numara Çevriliyor" : "Arayan"))
                    .font(.headline)
                    .lineLimit(1)
                
                if let phone = callManager.activeCall?.phoneNumber {
                    Text(phone)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
            
            HStack(spacing: 8) {
                if !callManager.isCallActive && !isOutgoing {
                    Button(action: {
                        callManager.declineCall()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "phone.down.fill")
                            Text("Reddet")
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(Color.red))
                    }
                    .buttonStyle(.plain)
                    
                    Button(action: {
                        callManager.answerCall()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "phone.fill")
                            Text("Cevapla")
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(Color.green))
                    }
                    .buttonStyle(.plain)
                } else {
                    Button(action: {
                        callManager.endCall()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "phone.down.fill")
                            Text("Kapat")
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(Color.red))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .glassCard(cornerRadius: 16, padding: 0)
        .frame(width: 370)
    }
    
    private var appBadgeColor: Color {
        if isOutgoing { return .blue }
        switch callManager.activeCall?.appType {
        case .whatsapp: return .green
        case .telegram: return .blue
        default: return Color(nsColor: .controlAccentColor)
        }
    }
    
    private var appIconName: String {
        switch callManager.activeCall?.appType {
        case .whatsapp: return "message.fill"
        case .telegram: return "paperplane.fill"
        default: return isOutgoing ? "phone.arrow.up.right.fill" : "phone.fill"
        }
    }
    
    private var appNameLabel: String {
        if isOutgoing { return "Giden Arama" }
        switch callManager.activeCall?.appType {
        case .whatsapp: return "WhatsApp"
        case .telegram: return "Telegram"
        default: return "Hücresel Arama"
        }
    }
}

// MARK: - Settings & System Status View
public struct SettingsView: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var updater = UpdateChecker.shared
    @ObservedObject var notifManager = NotificationManager.shared
    var onBack: () -> Void
    
    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 10) {
                // Header (Symmetrical: Back Button, Centered Title, Dummy Spacer)
                HStack {
                    Button(action: {
                        onBack()
                        PopoverStateManager.shared.currentPage = .dashboard
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(nsColor: .controlAccentColor))
                            .frame(width: 24, height: 24)
                            .background(Circle().fill(Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.06)))
                    }
                    .buttonStyle(.plain)
                    .help("Geri")
                    
                    Spacer()
                    
                    Text("Ayarlar & Durum")
                        .font(.headline)
                    
                    Spacer()
                    
                    Color.clear
                        .frame(width: 24, height: 24)
                }
                .padding(.horizontal, 2)
                
                // Environment & OS Status Card
                VStack(spacing: 8) {
                    HStack {
                        Image(systemName: "macwindow.and.cursorarrow")
                            .foregroundColor(Color(nsColor: .controlAccentColor))
                        Text("macOS Uyumluluğu")
                            .font(.caption)
                        Spacer()
                        Text("macOS \(ProcessInfo.processInfo.operatingSystemVersion.majorVersion).\(ProcessInfo.processInfo.operatingSystemVersion.minorVersion)")
                            .font(.caption.bold())
                    }
                    
                    HStack {
                        Image(systemName: "drop.fill")
                            .foregroundColor(.cyan)
                        Text("Liquid Glass Efekti")
                            .font(.caption)
                        Spacer()
                        Text("Ultra-Thin Material")
                            .font(.caption2)
                            .foregroundColor(.green)
                    }
                    
                    HStack {
                        Image(systemName: "paintpalette.fill")
                            .foregroundColor(.purple)
                        Text("Tema Görünümü")
                            .font(.caption)
                        Spacer()
                        Text(colorScheme == .dark ? "Koyu (Dark)" : "Açık (Light)")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
                .glassCard(cornerRadius: 14, padding: 12)
                
                // Notification Settings Card
                HStack {
                    Image(systemName: notifManager.isAuthorized ? "bell.badge.fill" : "bell.slash.fill")
                        .foregroundColor(notifManager.isAuthorized ? .green : .orange)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Sistem Bildirimleri")
                            .font(.caption.weight(.semibold))
                        Text(notifManager.isAuthorized ? "İzin Verildi" : "İzin Gerekli")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer()
                    
                    Button(action: {
                        notifManager.openNotificationSettings()
                    }) {
                        Text(notifManager.isAuthorized ? "Yönet" : "İzin İste")
                            .font(.caption2)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "open_notifs", isActive: false)
                }
                .glassCard(cornerRadius: 14, padding: 12)
                
                // Updater Card
                HStack {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .foregroundColor(Color(nsColor: .controlAccentColor))
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("AetherLink Sürümü")
                            .font(.caption.weight(.semibold))
                        Text("v\(updater.currentVersion)")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer()
                    
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
                            Text("Denetle")
                        }
                        .font(.caption2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "check_updates_settings", isActive: false)
                }
                .glassCard(cornerRadius: 14, padding: 12)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .frame(width: 320, height: 380)
        .background(.ultraThinMaterial)
        .keyboardShortcut(.cancelAction)
    }
}

// MARK: - Window Background & Size Configurator
struct WindowBackgroundConfigurator: NSViewRepresentable {
    let currentPage: PopoverPage
    
    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.async {
            configure(window: view.window)
        }
        return view
    }
    
    func updateNSView(_ nsView: NSView, context: Context) {
        DispatchQueue.main.async {
            configure(window: nsView.window)
        }
    }
    
    private func configure(window: NSWindow?) {
        guard let window = window else { return }
        window.isOpaque = false
        window.backgroundColor = .clear
        window.minSize = NSSize(width: 320, height: 380)
        
        let targetSize: NSSize
        switch currentPage {
        case .pairing:
            targetSize = NSSize(width: 320, height: 390)
        case .telemetry:
            targetSize = NSSize(width: 320, height: 420)
        case .settings:
            targetSize = NSSize(width: 320, height: 380)
        case .dashboard:
            targetSize = NSSize(width: 320, height: 440)
        }
        
        if abs(window.frame.width - targetSize.width) > 1 || abs(window.frame.height - targetSize.height) > 1 {
            window.setContentSize(targetSize)
        }
    }
}

// MARK: - Main Menu Bar Content View (Control Center Architecture)
public struct MenuBarContentView: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var network = NetworkManager.shared
    @ObservedObject var updater = UpdateChecker.shared
    @ObservedObject var pairing = PairingManager.shared
    @ObservedObject var mirror = ScreenMirrorManager.shared
    @ObservedObject var telemetryMgr = DeviceTelemetryManager.shared
    @ObservedObject var notifManager = NotificationManager.shared
    @ObservedObject var clipboard = ClipboardManager.shared
    @ObservedObject var state = PopoverStateManager.shared
    
    public init() {}
    
    private var currentViewHeight: CGFloat {
        switch state.currentPage {
        case .pairing:
            return 390
        case .telemetry:
            return 420
        case .settings:
            return 380
        case .dashboard:
            return 440
        }
    }
    
    public var body: some View {
        Group {
            switch state.currentPage {
            case .dashboard:
                dashboardView
            case .telemetry:
                DeviceTelemetryDetailView(
                    showInlineBack: true,
                    onBack: {
                        state.currentPage = .dashboard
                    }
                )
            case .pairing:
                if network.isConnected {
                    dashboardView
                } else {
                    PairingQRView(
                        showInlineBack: true,
                        onBack: {
                            state.currentPage = .dashboard
                        }
                    )
                }
            case .settings:
                SettingsView(
                    onBack: {
                        state.currentPage = .dashboard
                    }
                )
            }
        }
        .frame(width: 320, height: currentViewHeight)
        .background(.ultraThinMaterial)
        .background(WindowBackgroundConfigurator(currentPage: state.currentPage))
    }
    
    // MARK: - Dashboard Content
    private var dashboardView: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 12) {
            // 1. Top Header Glass Card (Control Center Device Pill)
            HStack(spacing: 10) {
                PulsingIndicatorView(isConnected: network.isConnected)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(network.connectedDeviceName.isEmpty ? "Cihaz Aranıyor..." : network.connectedDeviceName)
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .lineLimit(1)
                    
                    Text(network.isConnected ? "Yerel Ağda Bağlı • AES-256" : "Cihaz Aranıyor (mDNS & UDP)...")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                
                Spacer()
                
                // Battery Indicator Capsule
                if let battery = network.batteryState {
                    HStack(spacing: 4) {
                        Text("\(battery.batteryLevel)%")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                        
                        Image(systemName: battery.isCharging ? "battery.100.bolt" : (battery.batteryLevel < 20 ? "battery.25" : "battery.75"))
                            .font(.system(size: 13))
                            .foregroundColor(battery.batteryLevel < 20 ? .red : (battery.isCharging ? .green : .primary))
                    }
                    .padding(.horizontal, 7)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Color.primary.opacity(colorScheme == .dark ? 0.08 : 0.05))
                    )
                }
                
                // Minimal Disconnect Icon Button
                if network.isConnected {
                    Button(action: {
                        network.disconnectDevice(forget: false)
                    }) {
                        Image(systemName: "link.badge.slash")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.secondary)
                            .frame(width: 26, height: 26)
                            .background(Circle().fill(Color.primary.opacity(0.06)))
                    }
                    .buttonStyle(.plain)
                    .help("Bağlantıyı Kes")
                }
            }
            .glassCard(cornerRadius: 16, padding: 12, isHighlighted: network.isConnected)
            
            // 2. Disconnected Hero Pairing Card (Only visible when disconnected)
            if !network.isConnected {
                HStack(spacing: 12) {
                    Image(systemName: "qrcode.viewfinder")
                        .font(.system(size: 24))
                        .foregroundColor(Color(nsColor: .controlAccentColor))
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cihaz Eşleştir")
                            .font(.system(size: 13, weight: .semibold))
                        Text("QR kod veya onay koduyla bağlanın")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer()
                    
                    Button(action: {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            state.currentPage = .pairing
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "qrcode")
                            Text("QR Göster")
                        }
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "hero_pair_btn", isActive: true)
                }
                .glassCard(cornerRadius: 14, padding: 10, isHighlighted: true)
            }
            
            // 3. Interactive Quick Tiles (2-Column Grid)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                // Tile 1: Ekran Yansıt (Wireless Screen Mirroring via scrcpy)
                Button(action: {
                    if mirror.isScrcpyRunning {
                        mirror.stopMirroring()
                    } else {
                        mirror.startMirroring()
                    }
                }) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: mirror.isScrcpyRunning ? "display.trianglebadge.exclamationmark" : "display")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(mirror.isScrcpyRunning ? .red : Color(nsColor: .controlAccentColor))
                            Spacer()
                            Circle()
                                .fill(mirror.isScrcpyRunning ? Color.green : Color.clear)
                                .frame(width: 6, height: 6)
                        }
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Ekran Yansıt")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.primary)
                            Text(mirror.isScrcpyRunning ? "Aktif (60 FPS)" : "Durduruldu")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .glassTile(
                    id: "screen_mirror",
                    isActive: mirror.isScrcpyRunning,
                    activeTint: Color(nsColor: .controlAccentColor)
                )
                
                // Tile 2: Bluetooth / Sistem Ses Senkronizasyonu
                Button(action: {
                    state.isAudioRoutingActive.toggle()
                }) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: state.isAudioRoutingActive ? "speaker.wave.3.fill" : "speaker.wave.2")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(state.isAudioRoutingActive ? .green : .secondary)
                            Spacer()
                            Circle()
                                .fill(state.isAudioRoutingActive ? Color.green : Color.clear)
                                .frame(width: 6, height: 6)
                        }
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Bluetooth Ses")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.primary)
                            Text(state.isAudioRoutingActive ? "Aktif (Mac)" : "Telefon Sesinde")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .glassTile(
                    id: "audio_sync",
                    isActive: state.isAudioRoutingActive,
                    activeTint: .green
                )
                
                // Tile 3: Evrensel Pano (Universal Clipboard Sync)
                Button(action: {
                    clipboard.toggleMonitoring()
                }) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: clipboard.isMonitoringActive ? "doc.on.clipboard.fill" : "doc.on.clipboard")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(clipboard.isMonitoringActive ? .blue : .orange)
                            Spacer()
                            Circle()
                                .fill(clipboard.isMonitoringActive ? Color.green : Color.orange)
                                .frame(width: 6, height: 6)
                        }
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Evrensel Pano")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.primary)
                            Text(clipboard.isMonitoringActive ? "Eşzamanlı" : "Duraklatıldı")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .glassTile(
                    id: "clipboard_sync",
                    isActive: clipboard.isMonitoringActive,
                    activeTint: .blue
                )
                
                // Tile 4: Bildirimler ve Aramalar
                Button(action: {
                    notifManager.toggleNotificationsPaused()
                }) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: notifManager.isNotificationsPaused ? "bell.slash.fill" : "bell.badge.fill")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(notifManager.isNotificationsPaused ? .orange : Color(nsColor: .controlAccentColor))
                            Spacer()
                            Circle()
                                .fill(!notifManager.isNotificationsPaused ? Color.green : Color.orange)
                                .frame(width: 6, height: 6)
                        }
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Bildirim & Çağrı")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.primary)
                            Text(notifManager.isNotificationsPaused ? "Sessizde" : "Canlı Akış")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .glassTile(
                    id: "notifications_sync",
                    isActive: !notifManager.isNotificationsPaused,
                    activeTint: Color(nsColor: .controlAccentColor)
                )
            }
            
            // 3. Dynamic Media Player Card (If Media is Playing)
            if let media = network.mediaState, media.isPlaying && !media.trackTitle.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: media.packageName.contains("spotify") ? "play.circle.fill" : "music.note")
                        .font(.system(size: 20))
                        .foregroundColor(media.packageName.contains("spotify") ? .green : .pink)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(media.trackTitle)
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                        Text(media.artist.isEmpty ? (media.packageName.contains("spotify") ? "Spotify" : "Apple Music") : media.artist)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                    
                    Spacer()
                    
                    Button(action: {
                        MediaContinuityManager.shared.openCurrentMedia(media)
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.up.right.square")
                            Text("Aç")
                        }
                        .font(.caption2.weight(.medium))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "open_media_player", isActive: false)
                }
                .glassCard(cornerRadius: 14, padding: 10)
            }
            
            // 4. Minimal Footer Bar
            HStack {
                // "Cihaz Bilgileri" Button
                Button(action: {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        state.currentPage = .telemetry
                    }
                    telemetryMgr.requestTelemetryRefresh()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "info.circle")
                        Text("Bilgiler")
                    }
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
                
                // "Eşleştir" Button (Only visible when disconnected)
                if !network.isConnected {
                    Button(action: {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            state.currentPage = .pairing
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "qrcode")
                            Text("Eşleştir")
                        }
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                }
                
                // "Ayarlar" Button
                Button(action: {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        state.currentPage = .settings
                    }
                }) {
                    Image(systemName: "gearshape")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .padding(4)
                }
                .buttonStyle(.plain)
                
                Spacer()
                
                Text("v\(updater.currentVersion)")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary.opacity(0.8))
                
                Button("Çıkış") {
                    NSApplication.shared.terminate(nil)
                }
                .buttonStyle(.plain)
                .font(.caption2)
                .foregroundColor(.red.opacity(0.85))
                .padding(.leading, 4)
            }
            .padding(.top, 2)
            .padding(.horizontal, 2)
        }
        .padding(14)
        }
        .frame(width: 320, height: 440)
    }
}


