import AppKit
import SwiftUI

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
                    // Decline Button
                    Button(action: {
                        callManager.declineCall()
                    }) {
                        Image(systemName: "phone.down.fill")
                            .foregroundColor(.white)
                            .frame(width: 38, height: 38)
                            .background(Circle().fill(Color.red))
                    }
                    .buttonStyle(.plain)
                    
                    // Answer Button
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
                    // End Call Button
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
            // Header
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
            
            // Changelog Box
            ScrollView {
                Text(updateInfo.changelog)
                    .font(.body)
                    .foregroundColor(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
            }
            .frame(height: 180)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.primary.opacity(0.04))
            )
            
            Divider()
            
            // Bottom Action Buttons
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
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.regularMaterial)
        )
    }
}

// MARK: - Menu Bar Content View
public struct MenuBarContentView: View {
    @ObservedObject var network = NetworkManager.shared
    @ObservedObject var updater = UpdateChecker.shared
    
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
                    
                    Text(network.isConnected ? "Yerel Ağda Bağlı (AES-256)" : "Cihaz Aranıyor (mDNS/BLE)...")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // Battery Widget
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
            
            // Media Playing Widget
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
                FeatureRow(icon: "doc.on.clipboard", title: "Evrensel Pano", status: "Aktif")
                FeatureRow(icon: "bell.badge", title: "Bildirimler & Cevap", status: "Aktif")
                FeatureRow(icon: "phone.fill", title: "Arama Yansıtma", status: "Hazır")
            }
            
            Divider()
            
            // Update & Utility Footer
            HStack {
                Button(action: {
                    Task {
                        await updater.checkForUpdates(manual: true)
                    }
                }) {
                    HStack(spacing: 4) {
                        if updater.isChecking {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                        Text("Güncellemeleri Denetle")
                            .font(.caption)
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
            
            if let msg = updater.checkError {
                Text(msg)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding(14)
        .frame(width: 290)
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
            Text(title)
                .font(.caption)
            Spacer()
            Text(status)
                .font(.caption2)
                .foregroundColor(.green)
        }
    }
}
