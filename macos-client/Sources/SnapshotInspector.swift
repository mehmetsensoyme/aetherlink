import AppKit
import SwiftUI

@MainActor
public final class SnapshotInspector {
    public static let shared = SnapshotInspector()
    
    private init() {}
    
    public func captureAll(outputDirectory: URL) {
        try? FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: true)
        print("[SnapshotInspector] Starting automated screenshot capture to \(outputDirectory.path)...")
        
        // 1. MenuBar Disconnected State
        captureView(
            name: "mac_menubar_disconnected",
            outputDirectory: outputDirectory,
            width: 365,
            height: 330
        ) {
            MenuBarContentView()
        }
        
        // 2. MenuBar Connected State (Simulate active connection)
        let origConnected = NetworkManager.shared.isConnected
        let origDeviceName = NetworkManager.shared.connectedDeviceName
        let origBattery = NetworkManager.shared.batteryState
        
        NetworkManager.shared.isConnected = true
        NetworkManager.shared.connectedDeviceName = "Samsung Galaxy S25 Ultra"
        NetworkManager.shared.batteryState = BatteryPayload(batteryLevel: 88, isCharging: true, powerSaveMode: false, temperatureCelsius: 29.5)
        
        captureView(
            name: "mac_menubar_connected",
            outputDirectory: outputDirectory,
            width: 365,
            height: 370
        ) {
            MenuBarContentView()
        }
        
        // Restore connection state
        NetworkManager.shared.isConnected = origConnected
        NetworkManager.shared.connectedDeviceName = origDeviceName
        NetworkManager.shared.batteryState = origBattery
        
        // 3. Onboarding & Permissions Guide
        captureView(
            name: "mac_onboarding",
            outputDirectory: outputDirectory,
            width: 540,
            height: 600
        ) {
            OnboardingView(onComplete: {})
        }
        
        // 4. Continuity Camera Floating HUD
        captureView(
            name: "mac_continuity_camera",
            outputDirectory: outputDirectory,
            width: 720,
            height: 480
        ) {
            ContinuityCameraView()
        }
        
        // 5. Pairing QR View
        captureView(
            name: "mac_pairing_qr",
            outputDirectory: outputDirectory,
            width: 365,
            height: 330
        ) {
            PairingQRView(showInlineBack: true, onBack: {})
        }
        
        // 6. Device Telemetry View (Disconnected)
        captureView(
            name: "mac_device_telemetry_disconnected",
            outputDirectory: outputDirectory,
            width: 365,
            height: 240
        ) {
            DeviceTelemetryDetailView(showInlineBack: true, onBack: {})
        }
        
        // 7. Device Telemetry View (Connected)
        NetworkManager.shared.isConnected = true
        NetworkManager.shared.connectedDeviceName = "Samsung Galaxy S25 Ultra"
        DeviceTelemetryManager.shared.telemetry = DeviceTelemetryPayload()
        
        captureView(
            name: "mac_device_telemetry_connected",
            outputDirectory: outputDirectory,
            width: 365,
            height: 380
        ) {
            DeviceTelemetryDetailView(showInlineBack: true, onBack: {})
        }
        
        NetworkManager.shared.isConnected = false
        
        // 8. Settings View
        captureView(
            name: "mac_settings",
            outputDirectory: outputDirectory,
            width: 365,
            height: 450
        ) {
            SettingsView(onBack: {})
        }
        
        // 9. MenuBar Light Mode Disconnected
        captureView(
            name: "mac_menubar_light_disconnected",
            outputDirectory: outputDirectory,
            width: 365,
            height: 330,
            colorScheme: .light
        ) {
            MenuBarContentView()
        }
        
        // 10. MenuBar Light Mode Connected
        NetworkManager.shared.isConnected = true
        NetworkManager.shared.connectedDeviceName = "Samsung Galaxy S25 Ultra"
        NetworkManager.shared.batteryState = BatteryPayload(batteryLevel: 88, isCharging: true, powerSaveMode: false, temperatureCelsius: 29.5)
        
        captureView(
            name: "mac_menubar_light_connected",
            outputDirectory: outputDirectory,
            width: 365,
            height: 370,
            colorScheme: .light
        ) {
            MenuBarContentView()
        }
        
        NetworkManager.shared.isConnected = false
        
        // 11. Notch Call Incoming (Ringing) State
        NotchCallManager.shared.callerName = "Ahmet Yılmaz"
        NotchCallManager.shared.phoneNumber = "+90 555 123 45 67"
        NotchCallManager.shared.isExpanded = true
        NotchCallManager.shared.isPresented = true
        NotchCallManager.shared.isAttachedToNotch = true
        NotchCallManager.shared.notchClearance = 32
        NotchCallManager.shared.isCallActive = false
        NotchCallManager.shared.appType = .cellular
        
        captureView(
            name: "mac_notch_incoming",
            outputDirectory: outputDirectory,
            width: 440,
            height: 110,
            wrapInContainer: false
        ) {
            NotchCallView()
        }
        
        // 12. Notch Call Active State
        NotchCallManager.shared.isCallActive = true
        NotchCallManager.shared.callDurationSeconds = 142 // 02:22
        
        captureView(
            name: "mac_notch_active",
            outputDirectory: outputDirectory,
            width: 440,
            height: 110,
            wrapInContainer: false
        ) {
            NotchCallView()
        }
        
        // Reset NotchCallManager state
        NotchCallManager.shared.isPresented = false
        NotchCallManager.shared.isExpanded = false
        NotchCallManager.shared.isCallActive = false
        
        print("[SnapshotInspector] All screenshots captured successfully!")
    }
    
    private func captureView<V: View>(
        name: String,
        outputDirectory: URL,
        width: CGFloat,
        height: CGFloat,
        colorScheme: ColorScheme? = nil,
        wrapInContainer: Bool = true,
        @ViewBuilder content: () -> V
    ) {
        let window = NSWindow(
            contentRect: NSRect(x: 100, y: 100, width: width, height: height),
            styleMask: [.titled, .closable, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        window.titlebarAppearsTransparent = true
        window.titleVisibility = .hidden
        window.isOpaque = false
        window.backgroundColor = .clear
        window.hasShadow = true
        window.isReleasedWhenClosed = false
        if let cs = colorScheme {
            window.appearance = NSAppearance(named: cs == .dark ? .darkAqua : .aqua)
        }
        
        @ViewBuilder
        func buildView() -> some View {
            if wrapInContainer {
                content()
                    .frame(width: width, height: height)
                    .background(RoundedRectangle(cornerRadius: 16).fill(.ultraThinMaterial))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.18), lineWidth: 0.8))
            } else {
                content()
                    .frame(width: width, height: height)
            }
        }
        
        let finalView = buildView().environment(\.colorScheme, colorScheme ?? .dark)
        
        let hostingView = NSHostingView(rootView: finalView)
        hostingView.frame = NSRect(x: 0, y: 0, width: width, height: height)
        if let app = window.appearance {
            hostingView.appearance = app
        }
        window.contentView = hostingView
        window.makeKeyAndOrderFront(nil)
        
        // Run loop to let SwiftUI render completely
        RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.40))
        
        let fileURL = outputDirectory.appendingPathComponent("\(name).png")
        
        if let rep = hostingView.bitmapImageRepForCachingDisplay(in: hostingView.bounds) {
            let effectiveApp = hostingView.appearance ?? window.appearance ?? NSAppearance(named: .darkAqua)!
            effectiveApp.performAsCurrentDrawingAppearance {
                hostingView.cacheDisplay(in: hostingView.bounds, to: rep)
            }
            if let pngData = rep.representation(using: .png, properties: [:]) {
                try? pngData.write(to: fileURL)
            }
        }
        
        window.orderOut(nil)
        print("[SnapshotInspector] Captured: \(fileURL.lastPathComponent)")
    }
}
