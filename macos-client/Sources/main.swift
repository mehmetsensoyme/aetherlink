import AppKit
import SwiftUI

@main
struct AetherLinkApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {
        MenuBarExtra("AetherLink", systemImage: "bolt.horizontal.circle.fill") {
            MenuBarContentView()
        }
        .menuBarExtraStyle(.window)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        freopen("/tmp/aetherlink_debug.log", "a", stdout)
        freopen("/tmp/aetherlink_debug.log", "a", stderr)
        print("[AetherLink] applicationDidFinishLaunching triggered")
        
        // Prevent app from showing in Dock (pure menu bar daemon)
        NSApp.setActivationPolicy(.accessory)
        
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
        
        // Passive background update check
        Task {
            await UpdateChecker.shared.checkForUpdates(manual: false)
        }
        
        print("[AetherLink] Application launched and running in Menu Bar.")
    }
}
