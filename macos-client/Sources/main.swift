import AppKit
import SwiftUI

@main
struct AetherLinkApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @ObservedObject var network = NetworkManager.shared
    
    var body: some Scene {
        MenuBarExtra {
            MenuBarContentView()
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "bolt.horizontal.circle.fill")
                if network.isConnected, let battery = network.batteryState {
                    Text("\(battery.batteryLevel)%")
                    Image(systemName: battery.isCharging ? "battery.100.bolt" : "battery.75")
                }
            }
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
