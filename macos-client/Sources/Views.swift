import AppKit
import SwiftUI

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

// MARK: - Unified Screen Header Bar
public struct PopoverScreenHeader: View {
    let title: String
    let subtitle: String?
    let onBack: () -> Void
    
    public init(title: String, subtitle: String? = nil, onBack: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.onBack = onBack
    }
    
    public var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(nsColor: .controlAccentColor))
                    .frame(width: 26, height: 26)
                    .background(Circle().fill(Color.primary.opacity(0.08)))
            }
            .buttonStyle(.plain)
            .help("Geri")
            
            Spacer()
            
            VStack(spacing: 1) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                if let subtitle = subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }
            }
            
            Spacer()
            
            Color.clear
                .frame(width: 26, height: 26)
        }
        .padding(.horizontal, 2)
    }
}

// MARK: - Device Telemetry Detail View ("Cihaz Bilgileri")
public struct DeviceTelemetryDetailView: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var telemetryMgr = DeviceTelemetryManager.shared
    @ObservedObject var network = NetworkManager.shared
    @ObservedObject var thermalService = MacThermalService.shared
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
        } else {
            PopoverRouter.shared.popToDashboard()
        }
    }
    
    private var titleText: String {
        if network.isConnected {
            let raw = telemetry?.model ?? (network.connectedDeviceName.isEmpty ? "Bağlı Android Cihazı" : network.connectedDeviceName)
            return DeviceMarketingNameResolver.resolve(raw)
        } else {
            return "Bağlı Cihaz Yok"
        }
    }
    
    private var subtitleText: String {
        if network.isConnected {
            if let t = telemetry {
                let resolvedBrand = t.manufacturer.capitalized
                if titleText.contains(resolvedBrand) {
                    return "Android \(t.androidVersion) • Canlı Bağlantı"
                } else {
                    return "Android \(t.androidVersion) • \(resolvedBrand)"
                }
            } else {
                return "Telemetri Bekleniyor..."
            }
        } else {
            return "Bağlantı Yok"
        }
    }
    
    public var body: some View {
        VStack(spacing: 8) {
            // Symmetrical Unified Header
            PopoverScreenHeader(
                title: titleText,
                subtitle: subtitleText,
                onBack: handleBack
            )
            
            if network.isConnected {
                // 3 Progress Rings (RAM, Storage, Battery)
                HStack(spacing: 8) {
                    ActivityRingView(
                        progress: ramProgress,
                        ringColor: Color.blue,
                        ringWidth: 3.5,
                        diameter: 32,
                        icon: "memorychip",
                        title: "RAM",
                        valueText: telemetry != nil ? String(format: "%.1f GB", Double(telemetry!.ramUsedMB) / 1024.0) : "--",
                        subtitle: telemetry != nil ? "%\(Int(ramProgress * 100)) Dolu" : "--"
                    )
                    .frame(maxWidth: .infinity)
                    .frame(height: 74)
                    .glassCard(cornerRadius: 10, padding: 6)
                    
                    ActivityRingView(
                        progress: storageProgress,
                        ringColor: Color.purple,
                        ringWidth: 3.5,
                        diameter: 32,
                        icon: "internaldrive",
                        title: "Depolama",
                        valueText: telemetry != nil ? String(format: "%.0f GB", telemetry!.storageUsedGB) : "--",
                        subtitle: telemetry != nil ? String(format: "%.0f GB Toplam", telemetry!.storageTotalGB) : "--"
                    )
                    .frame(maxWidth: .infinity)
                    .frame(height: 74)
                    .glassCard(cornerRadius: 10, padding: 6)
                    
                    ActivityRingView(
                        progress: batteryProgress,
                        ringColor: Color.green,
                        ringWidth: 3.5,
                        diameter: 32,
                        icon: telemetry?.isCharging == true ? "bolt.fill" : "battery.100",
                        title: "Pil",
                        valueText: telemetry != nil ? "%\(telemetry!.batteryLevel)" : "--%",
                        subtitle: telemetry?.isCharging == true ? "Şarj Oluyor" : (telemetry?.batteryLevel == 100 ? "Tam Dolu" : "Deşarj Oluyor")
                    )
                    .frame(maxWidth: .infinity)
                    .frame(height: 74)
                    .glassCard(cornerRadius: 10, padding: 6)
                }
                
                // Hardware Telemetry Spec Rows
                VStack(spacing: 3) {
                    let phoneTempStr = telemetry != nil ? String(format: "%.1f", telemetry!.effectiveTemp) : "--"
                    let macTempStr = String(format: "%.1f", thermalService.currentTemperature)
                    
                    TelemetrySpecRow(
                        icon: "thermometer.medium",
                        label: "Donanım Isısı",
                        value: "📱 Tel: \(phoneTempStr)°C  •  💻 Mac: \(macTempStr)°C",
                        accentColor: .orange
                    )
                    
                    TelemetrySpecRow(
                        icon: "wifi",
                        label: "Kablosuz Ağ",
                        value: telemetryMgr.formattedNetwork,
                        accentColor: .blue
                    )
                    
                    if let conn = telemetryMgr.connectivity {
                        TelemetrySpecRow(
                            icon: "antenna.radiowaves.left.and.right",
                            label: "Hücresel & Operatör",
                            value: conn.formattedSummary,
                            accentColor: .indigo
                        )
                    } else {
                        TelemetrySpecRow(
                            icon: "antenna.radiowaves.left.and.right",
                            label: "Hücresel Ağ",
                            value: (telemetry?.cellularOperator?.isEmpty == false) ? telemetry!.cellularOperator! : "Mobil Veri",
                            accentColor: .indigo
                        )
                    }
                    
                    if let ping = telemetryMgr.lastPingMs {
                        TelemetrySpecRow(
                            icon: "bolt.horizontal.circle",
                            label: "Ağ Gecikmesi (RTT)",
                            value: String(format: "%.1f ms (Anlık Wi-Fi)", ping),
                            accentColor: .cyan
                        )
                    }
                    
                    TelemetrySpecRow(
                        icon: "heart.text.square.fill",
                        label: "Pil Sağlığı",
                        value: telemetry != nil ? "\(telemetry!.batteryHealth) • \(telemetry!.isCharging ? "Şarjda" : "Normal")" : "--",
                        accentColor: .green
                    )
                    
                    TelemetrySpecRow(
                        icon: "clock.arrow.circlepath",
                        label: "Açık Kalma",
                        value: telemetryMgr.formattedUptime,
                        accentColor: .purple
                    )
                }
                .glassCard(cornerRadius: 12, padding: 8)
                
                // Pinned Action Buttons (Yenile & Bağlantıyı Kes)
                HStack(spacing: 8) {
                    Button(action: {
                        telemetryMgr.requestTelemetryRefresh()
                        thermalService.readHardwareTemperature(forceFresh: true)
                        thermalService.broadcastTelemetry()
                    }) {
                        HStack(spacing: 5) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 11, weight: .semibold))
                            Text("Yenile")
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 30)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "refresh_telemetry", isActive: false)
                    
                    Button(role: .destructive, action: {
                        network.disconnectDevice(forget: false)
                        handleBack()
                    }) {
                        HStack(spacing: 5) {
                            Image(systemName: "link.badge.slash")
                                .font(.system(size: 11, weight: .semibold))
                            Text("Bağlantıyı Kes")
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity)
                        .frame(height: 30)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "disconnect_telemetry", isActive: false, activeTint: .red)
                }
            } else {
                // Empty State Glass Card (When Disconnected)
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.primary.opacity(0.06))
                            .frame(width: 44, height: 44)
                        Image(systemName: "iphone.slash")
                            .font(.system(size: 20))
                            .foregroundColor(.secondary)
                    }
                    
                    VStack(spacing: 4) {
                        Text("Bağlı Cihaz Bulunamadı")
                            .font(.system(size: 13, weight: .semibold))
                        Text("Donanım ve sistem telemetrisi görüntülemek için lütfen önce telefonunuzu eşleştirin.")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 8)
                    }
                    
                    Button(action: {
                        PopoverRouter.shared.navigateTo(.pairing)
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "qrcode")
                            Text("Cihaz Eşleştir")
                        }
                        .font(.caption.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 32)
                    }
                    .buttonStyle(.plain)
                    .glassTile(id: "telemetry_empty_pair_btn", isActive: true)
                }
                .padding(14)
                .glassCard(cornerRadius: 14, padding: 0)
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 14)
        .padding(.bottom, 12)
        .frame(width: 365)
        .background(.ultraThinMaterial)
        .onAppear {
            telemetryMgr.requestTelemetryRefresh()
            thermalService.readHardwareTemperature(forceFresh: true)
            thermalService.broadcastTelemetry()
            StatusBarController.shared.syncPopoverToFittingSize()
        }
        .background(
            Button("") {
                handleBack()
            }
            .keyboardShortcut(.escape, modifiers: [])
            .opacity(0)
            .frame(width: 0, height: 0)
        )
    }
}

// MARK: - Telemetry Spec Row
struct TelemetrySpecRow: View {
    let icon: String
    let label: String
    let value: String
    let accentColor: Color
    
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(accentColor)
                .frame(width: 22, height: 22)
                .background(Circle().fill(accentColor.opacity(0.12)))
            
            Text(label)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.secondary)
            
            Spacer(minLength: 4)
            
            Text(value)
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundColor(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .padding(.vertical, 1.5)
    }
}

// MARK: - Pairing QR View ("Cihaz Eşleştirme")
public struct PairingQRView: View {
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
        } else {
            PopoverRouter.shared.popToDashboard()
        }
    }
    
    public var body: some View {
        VStack(spacing: 8) {
            // Symmetrical Unified Header
            PopoverScreenHeader(
                title: "Cihaz Eşleştirme",
                subtitle: "AetherLink Mobil ile Tara",
                onBack: handleBack
            )
            
            // Compact Glass-framed QR Code Card
            GlassQRCodeCard(payloadUrl: pairing.pairingPayloadUrl)
            
            // Compact Confirmation Code Card
            VStack(spacing: 2) {
                Text("Eşleşme Onay Kodu")
                    .font(.caption2)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)
                
                Text(pairing.currentConfirmationCode)
                    .font(.system(size: 20, weight: .bold, design: .monospaced))
                    .tracking(3)
                    .foregroundColor(Color(nsColor: .controlAccentColor))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .glassCard(cornerRadius: 12, padding: 0, isHighlighted: true)
            
            // Instructions Text
            Text("Telefonunuzdaki AetherLink uygulamasından bu QR kodu okutun.")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
            
            // Local IP & Refresh Button Row
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
            .padding(.top, 2)
        }
        .padding(.horizontal, 14)
        .padding(.top, 14)
        .padding(.bottom, 12)
        .frame(width: 365)
        .background(.ultraThinMaterial)
        .background(
            Button("") {
                handleBack()
            }
            .keyboardShortcut(.escape, modifiers: [])
            .opacity(0)
            .frame(width: 0, height: 0)
        )
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
                
                Text(callManager.activeCall?.displayName ?? (isOutgoing ? "Giden Arama" : "Arayan"))
                    .font(.headline)
                    .lineLimit(1)
                    .truncationMode(.tail)
                
                if let phone = callManager.activeCall?.phoneNumber,
                   phone != callManager.activeCall?.displayName,
                   phone != "Bilinmeyen Numara",
                   phone != "Numara Çevriliyor",
                   phone != "Giden Arama" {
                    Text(phone)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
            .layoutPriority(1)
            
            Spacer(minLength: 12)
            
            HStack(spacing: 8) {
                if !callManager.isCallActive && !isOutgoing {
                    Button(action: {
                        callManager.declineCall()
                    }) {
                        HStack(spacing: 5) {
                            Image(systemName: "phone.down.fill")
                                .font(.system(size: 11, weight: .bold))
                            Text("Reddet")
                                .font(.system(size: 12, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Capsule().fill(Color.red))
                    }
                    .buttonStyle(.plain)
                    .fixedSize()
                    
                    Button(action: {
                        callManager.answerCall()
                    }) {
                        HStack(spacing: 5) {
                            Image(systemName: "phone.fill")
                                .font(.system(size: 11, weight: .bold))
                            Text("Cevapla")
                                .font(.system(size: 12, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Capsule().fill(Color.green))
                    }
                    .buttonStyle(.plain)
                    .fixedSize()
                } else {
                    Button(action: {
                        callManager.endCall()
                    }) {
                        HStack(spacing: 5) {
                            Image(systemName: "phone.down.fill")
                                .font(.system(size: 11, weight: .bold))
                            Text("Kapat")
                                .font(.system(size: 12, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(Capsule().fill(Color.red))
                    }
                    .buttonStyle(.plain)
                    .fixedSize()
                }
            }
            .layoutPriority(2)
            .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .glassCard(cornerRadius: 16, padding: 0)
        .frame(width: 420, height: 80)
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
    @ObservedObject var callManager = CallManager.shared
    var onBack: () -> Void
    
    public var body: some View {
        VStack(spacing: 8) {
            // Symmetrical Unified Header
            PopoverScreenHeader(
                title: "Ayarlar & Durum",
                subtitle: "AetherLink Tercihleri",
                onBack: {
                    onBack()
                    PopoverRouter.shared.popToDashboard()
                }
            )
            
            // Environment & OS Status Card
            VStack(spacing: 6) {
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
            .glassCard(cornerRadius: 12, padding: 10)
            
            // Call Banner HUD Position Card
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "phone.badge.waveform")
                        .foregroundColor(Color(nsColor: .controlAccentColor))
                    Text("Arama Bildirim Konumu")
                        .font(.caption.weight(.semibold))
                    Spacer()
                    Button("Test Et") {
                        let sample = CallIncomingPayload(
                            callId: "test-\(Int(Date().timeIntervalSince1970))",
                            appType: .cellular,
                            callerName: "Hakan YILDIRIM",
                            contact_name: "Hakan YILDIRIM",
                            phoneNumber: "+90 555 001 88 38",
                            avatarBase64: nil,
                            timestamp: Date().timeIntervalSince1970 * 1000,
                            hasVideo: false,
                            direction: "incoming"
                        )
                        callManager.handleIncomingCall(sample)
                    }
                    .font(.caption2)
                    .buttonStyle(.plain)
                    .glassTile(id: "test_call_banner", isActive: false)
                }
                
                Picker("", selection: $callManager.bannerPosition) {
                    ForEach(CallBannerPosition.allCases) { pos in
                        Text(pos.title).tag(pos)
                    }
                }
                .pickerStyle(.segmented)
                
                HStack(spacing: 4) {
                    Image(systemName: callManager.bannerPosition.icon)
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text(callManager.bannerPosition.description)
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
            }
            .glassCard(cornerRadius: 12, padding: 10)
            
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
            .glassCard(cornerRadius: 12, padding: 10)
            
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
            .glassCard(cornerRadius: 12, padding: 10)
        }
        .padding(.horizontal, 14)
        .padding(.top, 14)
        .padding(.bottom, 12)
        .frame(width: 365)
        .background(.ultraThinMaterial)
        .background(
            Button("") {
                onBack()
                PopoverRouter.shared.popToDashboard()
            }
            .keyboardShortcut(.escape, modifiers: [])
            .opacity(0)
            .frame(width: 0, height: 0)
        )
    }
}

// MARK: - Auto-Sizing NSHostingController & NSView
public class AutoSizingHostingController<Content: View>: NSHostingController<Content> {
    public override func viewDidLayout() {
        super.viewDidLayout()
        let fittingHeight = view.fittingSize.height
        if fittingHeight > 0 {
            preferredContentSize = NSSize(width: 365, height: max(ceil(fittingHeight), 220))
        }
    }
}

// MARK: - Window Background & Auto-Sizing Configurator
struct WindowBackgroundConfigurator: NSViewRepresentable {
    let currentPage: PopoverPage
    let isConnected: Bool
    let isMediaPlaying: Bool
    
    func makeNSView(context: Context) -> AutoSizingNSView {
        let view = AutoSizingNSView()
        return view
    }
    
    func updateNSView(_ nsView: AutoSizingNSView, context: Context) {
        nsView.scheduleResize()
    }
}

final class AutoSizingNSView: NSView {
    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        scheduleResize()
    }
    
    func scheduleResize() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, let window = self.window else { return }
            window.isOpaque = false
            window.backgroundColor = .clear
            
            if let contentView = window.contentView {
                contentView.wantsLayer = true
                contentView.layer?.backgroundColor = .clear
                
                let fittingHeight = contentView.fittingSize.height
                let preferred = PopoverRouter.shared.currentScreen.preferredHeight
                let targetHeight: CGFloat
                switch PopoverRouter.shared.currentScreen {
                case .deviceInfo:
                    targetHeight = fittingHeight > 100 ? max(ceil(fittingHeight), preferred) : preferred
                case .dashboard, .pairing, .settings:
                    if fittingHeight > 100 && abs(fittingHeight - preferred) < 60 {
                        targetHeight = ceil(fittingHeight)
                    } else {
                        targetHeight = preferred
                    }
                }
                let targetSize = NSSize(width: 365, height: targetHeight)
                let currentFrame = window.frame
                if abs(currentFrame.width - targetSize.width) > 1 || abs(currentFrame.height - targetSize.height) > 1 {
                    let deltaHeight = targetSize.height - currentFrame.height
                    let newOrigin = NSPoint(x: currentFrame.origin.x, y: currentFrame.origin.y - deltaHeight)
                    let newFrame = NSRect(origin: newOrigin, size: targetSize)
                    
                    NSAnimationContext.runAnimationGroup { context in
                        context.duration = 0.20
                        context.allowsImplicitAnimation = true
                        window.setFrame(newFrame, display: true, animate: true)
                    }
                }
            }
            
            if window.isVisible && !window.isKeyWindow {
                window.makeKey()
            }
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
    @ObservedObject var router = PopoverRouter.shared
    @ObservedObject var state = PopoverStateManager.shared
    @ObservedObject var findMy = FindMyDeviceManager.shared
    @ObservedObject var remoteLock = RemoteLockManager.shared
    @ObservedObject var remoteVol = RemoteVolumeManager.shared
    
    public init() {}
    
    public var body: some View {
        ZStack {
            switch router.currentScreen {
            case .dashboard:
                dashboardView
                    .transition(.opacity)
            case .deviceInfo:
                DeviceTelemetryDetailView(
                    showInlineBack: true,
                    onBack: {
                        router.popToDashboard()
                    }
                )
                .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .trailing)))
            case .pairing:
                if network.isConnected {
                    dashboardView
                        .transition(.opacity)
                } else {
                    PairingQRView(
                        showInlineBack: true,
                        onBack: {
                            router.popToDashboard()
                        }
                    )
                    .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .trailing)))
                }
            case .settings:
                SettingsView(
                    onBack: {
                        router.popToDashboard()
                    }
                )
                .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .trailing)))
            }
        }
        .frame(width: 365)
        .fixedSize(horizontal: true, vertical: true)
        .animation(.easeInOut(duration: 0.20), value: router.currentScreen)
        .background(.ultraThinMaterial)
        .background(
            WindowBackgroundConfigurator(
                currentPage: router.currentScreen,
                isConnected: network.isConnected,
                isMediaPlaying: network.mediaState?.isPlaying == true
            )
        )
    }
    
    // MARK: - Dashboard Content
    private var dashboardView: some View {
        VStack(spacing: 8) {
            // Find My Mac Active Alert Banner
            if findMy.isMacRinging {
                HStack(spacing: 8) {
                    Image(systemName: "bell.badge.fill")
                        .font(.system(size: 14))
                        .foregroundColor(.yellow)
                    Text("Telefonunuz Mac'inizi Çaldırıyor!")
                        .font(.system(size: 12, weight: .semibold))
                    Spacer()
                    Button("Sustur") {
                        findMy.silenceMac()
                    }
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(Color.yellow))
                    .buttonStyle(.plain)
                }
                .padding(9)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.yellow.opacity(0.18)))
            }
            
            // 1. Unified Top Header Glass Card
            if network.isConnected {
                // Connected State: Device market name, AES-256 status, real battery indicator, and red disconnect button
                HStack(spacing: 10) {
                    PulsingIndicatorView(isConnected: true)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(DeviceMarketingNameResolver.resolve(network.connectedDeviceName))
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                            .foregroundColor(.primary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                        
                        Text("Yerel Ağda Bağlı • AES-256")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .layoutPriority(1)
                    
                    Spacer(minLength: 4)
                        
                        // Battery Indicator Capsule
                        let batteryPct = network.batteryState?.batteryLevel ?? telemetryMgr.telemetry?.batteryLevel ?? 0
                        let isCharging = network.batteryState?.isCharging ?? (telemetryMgr.telemetry?.isCharging ?? false)
                        
                        if batteryPct > 0 {
                            HStack(spacing: 5) {
                                Text("%\(batteryPct)")
                                    .font(.system(size: 12, weight: .bold, design: .rounded))
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: true)
                                
                                Image(systemName: isCharging ? "battery.100.bolt" : (batteryPct <= 20 ? "battery.25" : "battery.75"))
                                    .font(.system(size: 13))
                                    .foregroundColor(batteryPct <= 20 ? .red : (isCharging ? .green : Color(nsColor: .controlAccentColor)))
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(colorScheme == .dark ? 0.12 : 0.08))
                            .clipShape(Capsule())
                            .layoutPriority(2)
                        }
                        
                        Spacer().frame(width: 8)
                        
                        // Minimal Red Disconnect Button
                        Button(action: {
                            network.disconnectDevice(forget: false)
                        }) {
                            Image(systemName: "power")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.red)
                                .frame(width: 24, height: 24)
                                .background(Circle().fill(Color.red.opacity(colorScheme == .dark ? 0.18 : 0.10)))
                        }
                        .buttonStyle(.plain)
                        .help("Bağlantıyı Kes")
                    }
                    .glassCard(cornerRadius: 14, padding: 10, isHighlighted: true)
                } else {
                    // Disconnected State: Single unified card ("Bağlı Cihaz Yok", "Cihaz Aranıyor...", "QR Göster" button)
                    HStack(spacing: 10) {
                        PulsingIndicatorView(isConnected: false)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Bağlı Cihaz Yok")
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                .foregroundColor(.primary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                            
                            Text("Cihaz Aranıyor...")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                        }
                        .layoutPriority(1)
                        
                        Spacer(minLength: 8)
                        
                        Button(action: {
                            router.navigateTo(.pairing)
                        }) {
                            HStack(spacing: 5) {
                                Image(systemName: "qrcode")
                                    .font(.system(size: 11, weight: .semibold))
                                Text("QR Göster")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(Color(nsColor: .controlAccentColor))
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                        }
                        .buttonStyle(.plain)
                        .glassTile(id: "header_pair_qr_btn", isActive: true)
                    }
                    .glassCard(cornerRadius: 14, padding: 10, isHighlighted: false)
                }
                
                // 2. Interactive Quick Tiles (2-Column Grid)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                    // Tile 1: Ekran Yansıt (Wireless Screen Mirroring via scrcpy)
                    Button(action: {
                        guard network.isConnected else { return }
                        if mirror.isScrcpyRunning {
                            mirror.stopMirroring()
                        } else {
                            mirror.startMirroring()
                        }
                    }) {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Image(systemName: mirror.isScrcpyRunning ? "display.trianglebadge.exclamationmark" : "display")
                                    .font(.system(size: 17, weight: .semibold))
                                    .foregroundColor(network.isConnected ? (mirror.isScrcpyRunning ? .blue : Color(nsColor: .controlAccentColor)) : .secondary)
                                Spacer()
                                Circle()
                                    .fill(network.isConnected && mirror.isScrcpyRunning ? Color.green : Color.secondary.opacity(0.25))
                                    .frame(width: 6, height: 6)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Ekran Yansıt")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(network.isConnected ? .primary : .secondary)
                                Text(network.isConnected ? (mirror.isScrcpyRunning ? "Aktif (60 FPS)" : "Durduruldu") : "Bağlantı Yok")
                                    .font(.system(size: 10))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(9)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .glassTile(
                        id: "screen_mirror",
                        isActive: network.isConnected && mirror.isScrcpyRunning,
                        activeTint: Color(nsColor: .controlAccentColor)
                    )
                    
                    // Tile 2: Bluetooth / Sistem Ses Senkronizasyonu
                    Button(action: {
                        guard network.isConnected else { return }
                        state.isAudioRoutingActive.toggle()
                    }) {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Image(systemName: state.isAudioRoutingActive ? "speaker.wave.3.fill" : "speaker.wave.2")
                                    .font(.system(size: 17, weight: .semibold))
                                    .foregroundColor(network.isConnected ? (state.isAudioRoutingActive ? .purple : .secondary) : .secondary)
                                Spacer()
                                Circle()
                                    .fill(network.isConnected && state.isAudioRoutingActive ? Color.purple : Color.secondary.opacity(0.25))
                                    .frame(width: 6, height: 6)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Bluetooth Ses")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(network.isConnected ? .primary : .secondary)
                                Text(network.isConnected ? (state.isAudioRoutingActive ? "Aktif (Mac)" : "Telefon Sesinde") : "Bağlantı Yok")
                                    .font(.system(size: 10))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(9)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .glassTile(
                        id: "audio_sync",
                        isActive: network.isConnected && state.isAudioRoutingActive,
                        activeTint: .purple
                    )
                    
                    // Tile 3: Evrensel Pano (Universal Clipboard Sync)
                    Button(action: {
                        guard network.isConnected else { return }
                        clipboard.toggleMonitoring()
                    }) {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Image(systemName: clipboard.isMonitoringActive ? "doc.on.clipboard.fill" : "doc.on.clipboard")
                                    .font(.system(size: 17, weight: .semibold))
                                    .foregroundColor(network.isConnected ? (clipboard.isMonitoringActive ? .blue : .secondary) : .secondary)
                                Spacer()
                                Circle()
                                    .fill(network.isConnected && clipboard.isMonitoringActive ? Color.blue : Color.secondary.opacity(0.25))
                                    .frame(width: 6, height: 6)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Evrensel Pano")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(network.isConnected ? .primary : .secondary)
                            Text(network.isConnected ? (clipboard.isMonitoringActive ? "Eşzamanlı" : "Duraklatıldı") : "Bağlantı Yok")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                            }
                        }
                        .padding(9)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .glassTile(
                        id: "clipboard_sync",
                        isActive: network.isConnected && clipboard.isMonitoringActive,
                        activeTint: .blue
                    )
                    
                    // Tile 4: Bildirimler ve Aramalar
                    Button(action: {
                        guard network.isConnected else { return }
                        notifManager.toggleNotificationsPaused()
                    }) {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Image(systemName: notifManager.isNotificationsPaused ? "bell.slash.fill" : "bell.badge.fill")
                                    .font(.system(size: 17, weight: .semibold))
                                    .foregroundColor(network.isConnected ? (!notifManager.isNotificationsPaused ? Color(nsColor: .controlAccentColor) : .orange) : .secondary)
                                Spacer()
                                Circle()
                                    .fill(network.isConnected && !notifManager.isNotificationsPaused ? Color.green : Color.secondary.opacity(0.25))
                                    .frame(width: 6, height: 6)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Bildirim & Çağrı")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(network.isConnected ? .primary : .secondary)
                                Text(network.isConnected ? (notifManager.isNotificationsPaused ? "Sessizde" : "Canlı Akış") : "Bağlantı Yok")
                                    .font(.system(size: 10))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(9)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .glassTile(
                        id: "notifications_sync",
                        isActive: network.isConnected && !notifManager.isNotificationsPaused,
                        activeTint: Color(nsColor: .controlAccentColor)
                    )
                }
                .opacity(network.isConnected ? 1.0 : 0.45)
                .disabled(!network.isConnected)
                
                // 2.5 KDE Connect Quick Tools Row (Find My Phone, Remote Lock, Ping RTT)
                if network.isConnected {
                    HStack(spacing: 6) {
                        // Find My Phone
                        Button(action: {
                            findMy.toggleRingPhone()
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: findMy.isPhoneRinging ? "bell.and.waveform.fill" : "bell.fill")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(findMy.isPhoneRinging ? .yellow : .primary)
                                Text(findMy.isPhoneRinging ? "Sustur" : "Çaldır")
                                    .font(.system(size: 11, weight: .medium))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 28)
                        }
                        .buttonStyle(.plain)
                        .glassTile(
                            id: "find_my_phone_tile",
                            isActive: findMy.isPhoneRinging,
                            activeTint: .yellow
                        )
                        .help("Telefonu en yüksek sesle çaldır (Sessiz modda dahi)")
                        
                        // Remote Lock Phone
                        Button(action: {
                            remoteLock.lockPhone()
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "lock.fill")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(.red)
                                Text("Kilitle")
                                    .font(.system(size: 11, weight: .medium))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 28)
                        }
                        .buttonStyle(.plain)
                        .glassTile(
                            id: "remote_lock_phone_tile",
                            isActive: false,
                            activeTint: .red
                        )
                        .help("Telefon ekranını uzaktan kilitle")
                        
                        // Ping RTT Test
                        Button(action: {
                            telemetryMgr.sendPing()
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "bolt.horizontal.fill")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(telemetryMgr.isPinging ? .orange : .cyan)
                                Text(telemetryMgr.isPinging ? "..." : (telemetryMgr.lastPingMs != nil ? "\(Int(telemetryMgr.lastPingMs!))ms" : "Ping"))
                                    .font(.system(size: 11, weight: .medium))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 28)
                        }
                        .buttonStyle(.plain)
                        .glassTile(
                            id: "ping_rtt_tile",
                            isActive: telemetryMgr.isPinging,
                            activeTint: .cyan
                        )
                        .help("Telefon ile Mac arasındaki anlık ağ gecikmesini (ping) ölç")
                    }
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
                
                // 4. Minimal Balanced Footer Bar
                HStack(spacing: 8) {
                    // "Cihaz Bilgileri" Button
                    Button(action: {
                        router.navigateTo(.deviceInfo)
                        telemetryMgr.requestTelemetryRefresh()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "info.circle")
                            Text("Bilgiler")
                        }
                        .font(.caption2.weight(.medium))
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 6).fill(Color.primary.opacity(colorScheme == .dark ? 0.08 : 0.04)))
                    }
                    .buttonStyle(.plain)
                    .help("Cihaz Bilgileri ve Donanım Telemetrisi")
                    
                    // "Ayarlar" Button
                    Button(action: {
                        router.navigateTo(.settings)
                    }) {
                        Image(systemName: "gearshape")
                            .font(.caption2.weight(.medium))
                            .foregroundColor(.secondary)
                            .padding(5)
                            .background(RoundedRectangle(cornerRadius: 6).fill(Color.primary.opacity(colorScheme == .dark ? 0.08 : 0.04)))
                    }
                    .buttonStyle(.plain)
                    .help("Uygulama Ayarları")
                    
                    Spacer()
                    
                    Text("v\(updater.currentVersion)")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundColor(.secondary.opacity(0.8))
                    
                    Button("Çıkış") {
                        NSApplication.shared.terminate(nil)
                    }
                    .buttonStyle(.plain)
                    .font(.caption2.weight(.medium))
                    .foregroundColor(.red.opacity(0.85))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Color.red.opacity(colorScheme == .dark ? 0.12 : 0.06)))
                }
                .padding(.top, 2)
                .padding(.horizontal, 2)
            }
            .padding(.horizontal, 14)
            .padding(.top, 14)
            .padding(.bottom, 12)
            .frame(width: 365)
    }
}


