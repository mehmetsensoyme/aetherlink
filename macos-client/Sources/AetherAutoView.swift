import AppKit
import SwiftUI

// MARK: - Aether Auto State Model
@MainActor
public final class AetherAutoState: ObservableObject {
    public static let shared = AetherAutoState()
    @Published public var dialNumber: String = ""
}

// MARK: - Aether Auto Desktop Car Console View
public struct AetherAutoView: View {
    @ObservedObject var network = NetworkManager.shared
    @ObservedObject var audioMgr = AetherAudioManager.shared
    @ObservedObject var callMgr = CallManager.shared
    @ObservedObject var mediaMgr = MediaContinuityManager.shared
    @ObservedObject var telemetryMgr = DeviceTelemetryManager.shared
    @ObservedObject var remoteVol = RemoteVolumeManager.shared
    @ObservedObject var findMy = FindMyDeviceManager.shared
    @ObservedObject var mirror = ScreenMirrorManager.shared
    @ObservedObject var autoState = AetherAutoState.shared
    @Environment(\.colorScheme) private var colorScheme
    
    var onDismiss: () -> Void = {}
    
    public init(onDismiss: @escaping () -> Void = {}) {
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack {
            // Dark Automotive Glass Background
            LinearGradient(
                colors: [Color(red: 0.08, green: 0.10, blue: 0.14), Color(red: 0.04, green: 0.05, blue: 0.08)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 14) {
                // Top Automotive Status Header
                topStatusBar
                
                // Main Content (2-Column Dashboard)
                HStack(alignment: .top, spacing: 14) {
                    // Left Column: Large Now Playing & Audio Engine Card
                    leftMediaCard
                        .frame(maxWidth: .infinity)
                    
                    // Right Column: Phone Dial Keypad & Call Card
                    rightPhoneDialCard
                        .frame(maxWidth: .infinity)
                }
                .frame(maxHeight: .infinity)
                
                // Bottom Quick Action Dock
                bottomDockBar
            }
            .padding(18)
        }
        .frame(width: 780, height: 500)
    }
    
    // MARK: - Top Status Bar
    private var topStatusBar: some View {
        HStack(spacing: 12) {
            // Close / Back button
            Button(action: onDismiss) {
                HStack(spacing: 5) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 11, weight: .bold))
                    Text("Kapat")
                        .font(.system(size: 12, weight: .semibold))
                }
                .foregroundColor(.white.opacity(0.85))
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule().fill(Color.white.opacity(0.10)))
            }
            .buttonStyle(.plain)
            
            // App Title & Brand
            HStack(spacing: 6) {
                Image(systemName: "car.fill")
                    .foregroundColor(Color(nsColor: .controlAccentColor))
                    .font(.system(size: 14, weight: .bold))
                Text("Aether Auto")
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            // Device Name & Status
            HStack(spacing: 6) {
                Circle()
                    .fill(network.isConnected ? Color.green : Color.orange)
                    .frame(width: 8, height: 8)
                Text(network.isConnected ? DeviceMarketingNameResolver.resolve(network.connectedDeviceName) : "Bağlantı Yok")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Capsule().fill(Color.white.opacity(0.08)))
            
            // Battery Status
            let batt = network.batteryState?.batteryLevel ?? telemetryMgr.telemetry?.batteryLevel ?? 0
            if batt > 0 {
                HStack(spacing: 4) {
                    Text("%\(batt)")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                    Image(systemName: (network.batteryState?.isCharging == true) ? "battery.100.bolt" : "battery.75")
                        .font(.system(size: 12))
                        .foregroundColor((network.batteryState?.isCharging == true) ? .green : .white)
                }
                .foregroundColor(.white)
                .padding(.horizontal, 8)
                .padding(.vertical, 5)
                .background(Capsule().fill(Color.white.opacity(0.08)))
            }
            
            // Live Ping (ms)
            if let ping = telemetryMgr.lastPingMs {
                HStack(spacing: 4) {
                    Image(systemName: "bolt.horizontal.fill")
                        .font(.system(size: 10))
                        .foregroundColor(.cyan)
                    Text("\(Int(ping)) ms")
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                        .foregroundColor(.cyan)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 5)
                .background(Capsule().fill(Color.cyan.opacity(0.12)))
            }
        }
    }
    
    // MARK: - Left Media & AetherAudio Card
    private var leftMediaCard: some View {
        VStack(spacing: 12) {
            // Header
            HStack {
                Image(systemName: "music.note.list")
                    .foregroundColor(.green)
                Text("Medya & Ses Köprüsü")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
                
                // AetherAudio Streaming Status Badge
                HStack(spacing: 4) {
                    Circle()
                        .fill(audioMgr.isStreaming ? Color.green : Color.gray)
                        .frame(width: 6, height: 6)
                    Text(audioMgr.isStreaming ? "AetherAudio Aktif" : "Ses Kapalı")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(audioMgr.isStreaming ? .green : .gray)
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(Capsule().fill(Color.white.opacity(0.06)))
            }
            
            // Now Playing Media Display
            let media = network.mediaState
            let isPlaying = media?.isPlaying == true
            let title = media?.trackTitle.isEmpty == false ? media!.trackTitle : "Oynatılan Parça Yok"
            let artist = media?.artist.isEmpty == false ? media!.artist : "Android Medya Çalar"
            
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(isPlaying ? Color.green.opacity(0.2) : Color.white.opacity(0.06))
                        .frame(width: 60, height: 60)
                    Image(systemName: isPlaying ? "music.quarternote.3" : "play.circle")
                        .font(.system(size: 28))
                        .foregroundColor(isPlaying ? .green : .white.opacity(0.6))
                }
                
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text(artist)
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                        .lineLimit(1)
                }
                Spacer()
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.05)))
            
            // Media Controls Bar
            HStack(spacing: 16) {
                Button(action: {
                    RemoteCommandManager.shared.executeCommand("prev_track")
                }) {
                    Image(systemName: "backward.fill")
                        .font(.system(size: 18))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(Color.white.opacity(0.08)))
                }
                .buttonStyle(.plain)
                
                Button(action: {
                    if let m = media {
                        MediaContinuityManager.shared.openCurrentMedia(m)
                    }
                }) {
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 22))
                        .foregroundColor(.black)
                        .frame(width: 52, height: 52)
                        .background(Circle().fill(Color.green))
                }
                .buttonStyle(.plain)
                
                Button(action: {
                    RemoteCommandManager.shared.executeCommand("next_track")
                }) {
                    Image(systemName: "forward.fill")
                        .font(.system(size: 18))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(Color.white.opacity(0.08)))
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
            
            // Volume Slider
            HStack(spacing: 8) {
                Button(action: { remoteVol.toggleMacMute() }) {
                    Image(systemName: remoteVol.isMacMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                        .foregroundColor(remoteVol.isMacMuted ? .red : .white.opacity(0.8))
                        .font(.system(size: 13))
                }
                .buttonStyle(.plain)
                
                Slider(value: Binding(
                    get: { Double(remoteVol.macVolume) },
                    set: { remoteVol.setMacVolume(Int($0)) }
                ), in: 0...100)
                
                Text("%\(remoteVol.macVolume)")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(.white.opacity(0.7))
                    .frame(width: 32)
            }
            .padding(.horizontal, 4)
            
            Divider().background(Color.white.opacity(0.1))
            
            // AetherAudio Streaming Toggle & Mode Selector
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Kablosuz Ses Aktarımı (Sıfır-ADB)")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.white.opacity(0.8))
                    Spacer()
                    Toggle("", isOn: Binding(
                        get: { audioMgr.isStreaming },
                        set: { enable in
                            if enable {
                                audioMgr.startAudioStream(mode: audioMgr.currentMode)
                            } else {
                                audioMgr.stopAudioStream()
                            }
                        }
                    ))
                    .toggleStyle(.switch)
                }
                
                HStack(spacing: 6) {
                    ForEach(AudioStreamMode.allCases) { mode in
                        let isSelected = (audioMgr.currentMode == mode)
                        Button(action: {
                            audioMgr.setAudioMode(mode)
                        }) {
                            Text(mode.title)
                                .font(.system(size: 10, weight: isSelected ? .bold : .medium))
                                .foregroundColor(isSelected ? .white : .white.opacity(0.6))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 5)
                                .background(
                                    RoundedRectangle(cornerRadius: 6)
                                        .fill(isSelected ? Color.green.opacity(0.3) : Color.white.opacity(0.05))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.white.opacity(0.06))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )
        )
    }
    
    // MARK: - Right Phone Dial Keypad Card
    private var rightPhoneDialCard: some View {
        VStack(spacing: 10) {
            // Header
            HStack {
                Image(systemName: "phone.fill")
                    .foregroundColor(.blue)
                Text("Hızlı Telefon Arama")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
                if callMgr.isCallActive || callMgr.activeCall?.direction == "outgoing" {
                    Text("Görüşme Sürüyor")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.green)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color.green.opacity(0.2)))
                }
            }
            
            // Dial Input Bar
            HStack(spacing: 8) {
                Text(autoState.dialNumber.isEmpty ? "Numara tuşlayın..." : autoState.dialNumber)
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(autoState.dialNumber.isEmpty ? .white.opacity(0.3) : .white)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                if !autoState.dialNumber.isEmpty {
                    Button(action: {
                        if !autoState.dialNumber.isEmpty {
                            autoState.dialNumber.removeLast()
                        }
                    }) {
                        Image(systemName: "delete.left.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.white.opacity(0.7))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.06)))
            
            // 3x4 Automotive Numeric Keypad
            let keypadRows = [
                ["1", "2", "3"],
                ["4", "5", "6"],
                ["7", "8", "9"],
                ["*", "0", "#"]
            ]
            
            VStack(spacing: 6) {
                ForEach(keypadRows, id: \.self) { row in
                    HStack(spacing: 8) {
                        ForEach(row, id: \.self) { digit in
                            Button(action: {
                                autoState.dialNumber.append(digit)
                            }) {
                                Text(digit)
                                    .font(.system(size: 18, weight: .bold, design: .rounded))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 38)
                                    .background(
                                        RoundedRectangle(cornerRadius: 10)
                                            .fill(Color.white.opacity(0.08))
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            
            // Action Call / End Buttons
            HStack(spacing: 10) {
                if callMgr.isCallActive || callMgr.activeCall != nil {
                    Button(action: {
                        callMgr.endCall()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "phone.down.fill")
                            Text("Aramayı Bitir")
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(Capsule().fill(Color.red))
                    }
                    .buttonStyle(.plain)
                } else {
                    Button(action: {
                        let clean = autoState.dialNumber.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !clean.isEmpty {
                            callMgr.startOutgoingCall(phoneNumber: clean)
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "phone.fill")
                            Text("Ara")
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(Capsule().fill(autoState.dialNumber.isEmpty ? Color.gray.opacity(0.3) : Color.green))
                    }
                    .buttonStyle(.plain)
                    .disabled(autoState.dialNumber.isEmpty || !network.isConnected)
                }
            }
            
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.white.opacity(0.06))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )
        )
    }
    
    // MARK: - Bottom Dock Bar
    private var bottomDockBar: some View {
        HStack(spacing: 10) {
            // Screen Mirroring
            Button(action: {
                if mirror.isScrcpyRunning {
                    mirror.stopMirroring()
                } else {
                    mirror.startMirroring()
                }
            }) {
                HStack(spacing: 5) {
                    Image(systemName: "display")
                        .font(.system(size: 12))
                    Text(mirror.isScrcpyRunning ? "Yansıtmayı Durdur" : "Ekran Yansıt")
                        .font(.system(size: 11, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Capsule().fill(mirror.isScrcpyRunning ? Color.red.opacity(0.4) : Color.white.opacity(0.10)))
            }
            .buttonStyle(.plain)
            
            // Find My Phone
            Button(action: {
                findMy.toggleRingPhone()
            }) {
                HStack(spacing: 5) {
                    Image(systemName: findMy.isPhoneRinging ? "bell.and.waveform.fill" : "bell.fill")
                        .font(.system(size: 12))
                        .foregroundColor(findMy.isPhoneRinging ? .yellow : .white)
                    Text(findMy.isPhoneRinging ? "Telefonu Sustur" : "Telefonu Çaldır")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Capsule().fill(Color.white.opacity(0.10)))
            }
            .buttonStyle(.plain)
            
            // Refresh Telemetry
            Button(action: {
                telemetryMgr.requestTelemetryRefresh()
                telemetryMgr.sendPing()
            }) {
                HStack(spacing: 5) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 12))
                    Text("Yenile")
                        .font(.system(size: 11, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Capsule().fill(Color.white.opacity(0.10)))
            }
            .buttonStyle(.plain)
            
            Spacer()
            
            Text("AetherLink v1.9.0 • Build 22")
                .font(.system(size: 10, weight: .medium, design: .monospaced))
                .foregroundColor(.white.opacity(0.4))
        }
    }
}
