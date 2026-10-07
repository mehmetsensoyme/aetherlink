import AppKit
import SwiftUI

@main
struct AetherLinkApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @ObservedObject var network = NetworkManager.shared
    @ObservedObject var telemetry = DeviceTelemetryManager.shared
    @ObservedObject var themeMgr = AppThemeManager.shared
    
    var body: some Scene {
        MenuBarExtra {
            MenuBarContentView()
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "bolt.horizontal.circle.fill")
                if network.isConnected {
                    if let battery = network.batteryState {
                        Text("\(battery.batteryLevel)%")
                        Image(systemName: battery.isCharging ? "battery.100.bolt" : "battery.75")
                    }
                    if themeMgr.showPingInMenuBar, let ping = telemetry.lastPingMs {
                        Text("• \(Int(ping))ms")
                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                    }
                }
            }
        }
        .menuBarExtraStyle(.window)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        if CommandLine.arguments.contains("--snapshot") {
            let outDir = URL(fileURLWithPath: "/tmp/mac_snapshots")
            SnapshotInspector.shared.captureAll(outputDirectory: outDir)
            exit(0)
        }
        
        freopen("/tmp/aetherlink_debug.log", "a", stdout)
        freopen("/tmp/aetherlink_debug.log", "a", stderr)
        print("[AetherLink] applicationDidFinishLaunching triggered")
        
        // Prevent app from showing in Dock (pure menu bar daemon)
        NSApp.setActivationPolicy(.accessory)
        
        // Initialize Status Bar Popover controller & PopoverRouter size sync
        _ = StatusBarController.shared
        
        // Start periodic telemetry & latency ping
        DeviceTelemetryManager.shared.startPeriodicPing()
        
        // Request Notification authorization
        NotificationManager.shared.requestAuthorization()
        
        // Start Local mDNS & WebSocket Server
        NetworkManager.shared.startServer()
        
        // Start UDP Broadcast Discovery Responder (Port 8444)
        UDPDiscoveryResponder.shared.start()
        
        // Start Universal Clipboard Sync
        ClipboardManager.shared.startMonitoring()
        
        // Start Mac Battery Monitor & Broadcaster
        MacBatteryMonitor.shared.startMonitoring()
        
        // Start Mac Hardware Thermal Service & Broadcaster
        MacThermalService.shared.startMonitoring()
        
        // Passive background update check
        Task {
            await UpdateChecker.shared.checkForUpdates(manual: false)
        }
        
        // Show Onboarding & Permissions window if first launch
        if !UserDefaults.standard.bool(forKey: "aetherlink_has_completed_onboarding") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                AetherWindowManager.shared.showOnboardingWindow()
            }
        }
        
        // Register exit and signal cleanup handlers for scrcpy process lifecycle
        atexit {
            ScreenMirrorManager.shared.terminateScrcpySync()
        }
        signal(SIGINT) { _ in
            ScreenMirrorManager.shared.terminateScrcpySync()
            exit(0)
        }
        signal(SIGTERM) { _ in
            ScreenMirrorManager.shared.terminateScrcpySync()
            exit(0)
        }
        
        print("[AetherLink] Application launched and running in Menu Bar.")
    }
    
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return false
    }
    
    func applicationWillTerminate(_ notification: Notification) {
        print("[AetherLink] Application terminating, cleaning up scrcpy processes...")
        ScreenMirrorManager.shared.terminateScrcpy()
    }
}
