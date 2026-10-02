import SwiftUI
import AppKit

public struct ContinuityCameraView: View {
    @ObservedObject var camera = ContinuityCameraManager.shared
    @ObservedObject var network = NetworkManager.shared
    @Environment(\.colorScheme) private var colorScheme
    
    public init() {}
    
    public var body: some View {
        ZStack {
            // Background Canvas
            Color.black.edgesIgnoringSafeArea(.all)
            
            // Video Stream Canvas
            if let frame = camera.currentFrame {
                Image(nsImage: frame)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .transition(.opacity)
            } else {
                // Placeholder / Waiting State
                VStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .fill(Color(nsColor: .controlAccentColor).opacity(0.12))
                            .frame(width: 80, height: 80)
                        
                        Image(systemName: "video.fill")
                            .font(.system(size: 32))
                            .foregroundColor(Color(nsColor: .controlAccentColor))
                    }
                    
                    VStack(spacing: 6) {
                        Text("Süreklilik Kamerası Başlatılıyor")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                        
                        Text(network.connectedDeviceName.isEmpty ? "Android cihazına bağlanılıyor..." : "\(network.connectedDeviceName) kamerasından canlı veri bekleniyor...")
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.70))
                    }
                    
                    ProgressView()
                        .scaleEffect(0.9)
                        .colorScheme(.dark)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            
            // HUD Overlays
            VStack {
                // Top Telemetry Header
                HStack(spacing: 8) {
                    // Live Status Pill
                    HStack(spacing: 6) {
                        Circle()
                            .fill(camera.isStreaming ? Color.green : Color.orange)
                            .frame(width: 8, height: 8)
                        
                        Text(camera.isStreaming ? "CANLI" : "BAĞLANIYOR")
                            .font(.system(size: 10, weight: .black, design: .rounded))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(.ultraThinMaterial))
                    .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 0.8))
                    
                    // Device Name Pill
                    Text(DeviceMarketingNameResolver.resolve(network.connectedDeviceName))
                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                        .foregroundColor(.white.opacity(0.90))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(.ultraThinMaterial))
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 0.8))
                    
                    Spacer()
                    
                    // Telemetry Stats (FPS, Latency, Bitrate)
                    if camera.isStreaming {
                        HStack(spacing: 8) {
                            // FPS
                            Text("\(camera.currentFps) FPS")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(camera.currentFps >= 25 ? .green : .yellow)
                            
                            Text("•")
                                .font(.system(size: 10))
                                .foregroundColor(.white.opacity(0.4))
                            
                            // Latency
                            Text("\(Int(camera.latencyMs)) ms")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(camera.latencyMs < 50 ? .cyan : .orange)
                            
                            Text("•")
                                .font(.system(size: 10))
                                .foregroundColor(.white.opacity(0.4))
                            
                            // Bitrate
                            Text(String(format: "%.1f Mb/s", camera.bitrateMbps))
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(.white.opacity(0.85))
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(.ultraThinMaterial))
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 0.8))
                    }
                    
                    // Resolution Toggle (1080p / 720p)
                    Menu {
                        Button("1080p Full HD (60 FPS)") {
                            camera.setResolution("1080p")
                        }
                        Button("720p HD (Ultra Düşük Gecikme)") {
                            camera.setResolution("720p")
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Text(camera.currentResolution.uppercased())
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                            Image(systemName: "chevron.down")
                                .font(.system(size: 9, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(.ultraThinMaterial))
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 0.8))
                    }
                    .menuStyle(.borderlessButton)
                    .fixedSize()
                }
                .padding(.horizontal, 14)
                .padding(.top, 14)
                
                Spacer()
                
                // Snapshot Notification Toast
                if camera.showSnapshotToast {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text(camera.toastMessage)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Capsule().fill(Color.black.opacity(0.80)))
                    .overlay(Capsule().stroke(Color.white.opacity(0.25), lineWidth: 0.8))
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .padding(.bottom, 12)
                }
                
                // Bottom Floating Glass Control Bar
                HStack(spacing: 12) {
                    // Lens Switch Button
                    Button(action: {
                        camera.switchCamera()
                    }) {
                        VStack(spacing: 3) {
                            Image(systemName: "arrow.triangle.2.circlepath.camera.fill")
                                .font(.system(size: 16))
                            Text(camera.currentLens == "back" ? "Arka 200MP" : "Ön Kamera")
                                .font(.system(size: 10, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .frame(width: 72, height: 48)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .help("Kamera lensini değiştir (Ön / Arka)")
                    
                    // Torch / Flashlight Toggle
                    Button(action: {
                        camera.toggleTorch()
                    }) {
                        VStack(spacing: 3) {
                            Image(systemName: camera.isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                                .font(.system(size: 16))
                                .foregroundColor(camera.isTorchOn ? .yellow : .white)
                            Text(camera.isTorchOn ? "Flaş Açık" : "Flaş")
                                .font(.system(size: 10, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .frame(width: 60, height: 48)
                        .background(RoundedRectangle(cornerRadius: 12).fill(camera.isTorchOn ? Color.yellow.opacity(0.25) : Color.white.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .disabled(camera.currentLens != "back")
                    .opacity(camera.currentLens == "back" ? 1.0 : 0.4)
                    .help("El feneri / flaşı aç ya da kapat")
                    
                    // Studio Microphone Toggle
                    Button(action: {
                        camera.toggleMic()
                    }) {
                        VStack(spacing: 3) {
                            Image(systemName: camera.isMicActive ? "mic.fill" : "mic.slash.fill")
                                .font(.system(size: 16))
                                .foregroundColor(camera.isMicActive ? .green : .red)
                            Text(camera.isMicActive ? "Stüdyo Mik" : "Mik Sessiz")
                                .font(.system(size: 10, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .frame(width: 70, height: 48)
                        .background(RoundedRectangle(cornerRadius: 12).fill(camera.isMicActive ? Color.green.opacity(0.20) : Color.red.opacity(0.20)))
                    }
                    .buttonStyle(.plain)
                    .help("Telefonun stüdyo mikrofon sesini Mac'te aç veya sustur")
                    
                    // Snapshot Button
                    Button(action: {
                        camera.takeSnapshot()
                        camera.triggerSnapshotToast("Fotoğraf Resimler klasörüne kaydedildi")
                    }) {
                        VStack(spacing: 3) {
                            Image(systemName: "camera.shutter.button.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.cyan)
                            Text("Fotoğraf")
                                .font(.system(size: 10, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .frame(width: 64, height: 48)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.cyan.opacity(0.20)))
                    }
                    .buttonStyle(.plain)
                    .help("Yüksek çözünürlüklü anlık görüntü yakala")
                    
                    Divider()
                        .frame(height: 30)
                        .background(Color.white.opacity(0.25))
                    
                    // Stop Stream Button
                    Button(action: {
                        camera.stopStream()
                    }) {
                        VStack(spacing: 3) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(.red)
                            Text("Kapat")
                                .font(.system(size: 10, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .frame(width: 54, height: 48)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.red.opacity(0.20)))
                    }
                    .buttonStyle(.plain)
                    .help("Kamera yayınını sonlandır")
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(Color.white.opacity(0.18), lineWidth: 0.8)
                )
                .shadow(color: Color.black.opacity(0.40), radius: 14, x: 0, y: 6)
                .padding(.bottom, 16)
            }
        }
        .frame(minWidth: 680, minHeight: 460)
    }
}
