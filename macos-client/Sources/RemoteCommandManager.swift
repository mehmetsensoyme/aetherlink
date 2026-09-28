import AppKit
import Foundation

@MainActor
public final class RemoteCommandManager {
    public static let shared = RemoteCommandManager()
    
    public init() {}
    
    public func executeCommand(_ key: String) {
        print("[RemoteCommandManager] Executing remote command: \(key)")
        
        switch key {
        case "screenshot":
            takeScreenshot()
            
        case "sleep":
            sleepMac()
            
        case "emptyTrash":
            emptyTrash()
            
        case "showDesktop":
            showDesktop()
            
        case "openDownloads":
            let downloadsUrl = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
            NSWorkspace.shared.open(downloadsUrl)
            
        case "openTerminal":
            if let termUrl = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.apple.Terminal") {
                NSWorkspace.shared.openApplication(at: termUrl, configuration: NSWorkspace.OpenConfiguration())
            }
            
        default:
            print("[RemoteCommandManager] Unrecognized command key: \(key)")
        }
    }
    
    private func takeScreenshot() {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        let timestamp = formatter.string(from: Date())
        
        let desktop = FileManager.default.urls(for: .desktopDirectory, in: .userDomainMask).first!
        let targetPath = desktop.appendingPathComponent("Ekran_Resmi_\(timestamp).png").path
        
        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
        proc.arguments = ["-x", targetPath]
        try? proc.run()
        
        NSSound(named: "Tink")?.play()
        
        // Post notification
        let notif = NotificationPayload(
            id: UUID().uuidString,
            key: "screenshot_alert",
            packageName: "system",
            appName: "AetherLink Ekran Görüntüsü",
            title: "📸 Ekran Görüntüsü Kaydedildi",
            text: "Masaüstüne kaydedildi: Ekran_Resmi_\(timestamp).png",
            subText: nil,
            timestamp: Date().timeIntervalSince1970 * 1000,
            canReply: false,
            replyPlaceholder: nil,
            appIconBase64: nil
        )
        NotificationManager.shared.displayNotification(notif)
    }
    
    private func sleepMac() {
        let script = "tell application \"System Events\" to sleep"
        NSAppleScript(source: script)?.executeAndReturnError(nil)
    }
    
    private func emptyTrash() {
        let script = "tell application \"Finder\" to empty trash"
        NSAppleScript(source: script)?.executeAndReturnError(nil)
        NSSound(named: "Trash")?.play()
    }
    
    private func showDesktop() {
        let script = "tell application \"System Events\" to key code 103" // F11 key code
        NSAppleScript(source: script)?.executeAndReturnError(nil)
    }
}
