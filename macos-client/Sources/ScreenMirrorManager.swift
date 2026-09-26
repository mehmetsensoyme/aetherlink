import AppKit
import SwiftUI
import Combine

@MainActor
public final class ScreenMirrorManager: ObservableObject {
    public static let shared = ScreenMirrorManager()
    
    @Published public var isStreaming: Bool = false
    @Published public var currentFrame: NSImage? = nil
    @Published public var frameCount: Int = 0
    @Published public var fps: Double = 0.0
    @Published public var streamResolution: CGSize = .zero
    @Published public var isWindowOpen: Bool = false
    
    private var windowController: NSWindowController?
    private var lastFrameTime: TimeInterval = 0
    private var frameTimer: Timer?
    
    public init() {}
    
    public func startStreamRequest() {
        let payload = ScreenStreamControlPayload(
            action: "start",
            quality: "high",
            fps: 30,
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "SCREEN_STREAM_CONTROL", payload: payload)
        self.isStreaming = true
        openScreenWindow()
    }
    
    public func stopStreamRequest() {
        let payload = ScreenStreamControlPayload(
            action: "stop",
            quality: "high",
            fps: 0,
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "SCREEN_STREAM_CONTROL", payload: payload)
        self.isStreaming = false
        self.currentFrame = nil
    }
    
    public func handleIncomingFrame(_ frame: ScreenStreamFramePayload) {
        guard let data = Data(base64Encoded: frame.base64Data),
              let image = NSImage(data: data) else {
            return
        }
        
        self.currentFrame = image
        self.frameCount += 1
        self.streamResolution = CGSize(width: frame.width, height: frame.height)
        
        let now = Date().timeIntervalSince1970
        if lastFrameTime > 0 {
            let delta = now - lastFrameTime
            if delta > 0 {
                self.fps = (1.0 / delta) * 0.3 + self.fps * 0.7
            }
        }
        lastFrameTime = now
        
        if !isWindowOpen {
            openScreenWindow()
        }
    }
    
    public func openScreenWindow() {
        if let wc = windowController, wc.window?.isVisible == true {
            wc.window?.makeKeyAndOrderFront(nil)
            return
        }
        
        let hostingView = NSHostingView(rootView: ScreenMirrorView())
        let window = NSWindow(
            contentRect: NSRect(x: 100, y: 100, width: 380, height: 780),
            styleMask: [.titled, .closable, .miniaturizable, .resizable, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        
        window.title = "AetherLink Ekran Yansıtma (Galaxy S25 Ultra)"
        window.titleVisibility = .hidden
        window.titlebarAppearsTransparent = true
        window.isMovableByWindowBackground = true
        window.backgroundColor = .clear
        window.contentView = hostingView
        window.center()
        window.level = .floating
        window.isReleasedWhenClosed = false
        
        let wc = NSWindowController(window: window)
        self.windowController = wc
        self.isWindowOpen = true
        
        window.orderFrontRegardless()
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
    
    public func closeScreenWindow() {
        windowController?.close()
        self.isWindowOpen = false
        if isStreaming {
            stopStreamRequest()
        }
    }
}

public struct ScreenMirrorView: View {
    @ObservedObject var mirror = ScreenMirrorManager.shared
    @ObservedObject var network = NetworkManager.shared
    
    public var body: some View {
        ZStack {
            // Background blur
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.3), radius: 20, x: 0, y: 10)
            
            VStack(spacing: 0) {
                // Top Custom Titlebar
                HStack {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(mirror.currentFrame != nil ? Color.green : Color.orange)
                            .frame(width: 8, height: 8)
                        Text(network.connectedDeviceName.isEmpty ? "Galaxy S25 Ultra" : network.connectedDeviceName)
                            .font(.system(size: 13, weight: .semibold))
                    }
                    
                    Spacer()
                    
                    if mirror.fps > 0 {
                        Text("\(Int(mirror.fps)) FPS")
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color.primary.opacity(0.08)))
                    }
                    
                    Button(action: {
                        mirror.closeScreenWindow()
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                            .font(.system(size: 16))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 8)
                
                Divider()
                
                // Screen Mirror Frame or Placeholder
                ZStack {
                    if let frame = mirror.currentFrame {
                        Image(nsImage: frame)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .cornerRadius(16)
                            .padding(8)
                    } else {
                        VStack(spacing: 16) {
                            ProgressView()
                                .controlSize(.large)
                            
                            Text("Kablosuz Ekran Akışı Bekleniyor...")
                                .font(.headline)
                                .foregroundColor(.primary)
                            
                            Text("Telefondan AetherLink 'Ekranı Paylaş' izni onaylandığında görüntü anında buraya yansıyacaktır.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                            
                            Button(action: {
                                mirror.startStreamRequest()
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "play.fill")
                                    Text("Yayını Başlat")
                                }
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                
                Divider()
                
                // Bottom Control Toolbar
                HStack(spacing: 16) {
                    Button(action: {
                        if mirror.isStreaming {
                            mirror.stopStreamRequest()
                        } else {
                            mirror.startStreamRequest()
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: mirror.isStreaming ? "stop.fill" : "play.fill")
                            Text(mirror.isStreaming ? "Durdur" : "Başlat")
                        }
                        .font(.caption)
                    }
                    .buttonStyle(.bordered)
                    
                    Spacer()
                    
                    Text("\(Int(mirror.streamResolution.width))x\(Int(mirror.streamResolution.height))")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
        }
        .frame(minWidth: 320, minHeight: 640)
        .padding(10)
    }
}
