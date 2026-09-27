import AppKit
import Foundation

@MainActor
public final class ClipboardManager {
    public static let shared = ClipboardManager()
    
    private var lastChangeCount: Int = 0
    private var lastSyncedHash: String = ""
    private var timer: Timer?
    
    public init() {}
    
    public func startMonitoring() {
        lastChangeCount = NSPasteboard.general.changeCount
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self else { return }
            Task { @MainActor in
                self.checkLocalClipboard()
            }
        }
    }
    
    private func checkLocalClipboard() {
        let currentCount = NSPasteboard.general.changeCount
        guard currentCount != lastChangeCount else { return }
        lastChangeCount = currentCount
        
        guard let string = NSPasteboard.general.string(forType: .string), !string.isEmpty else { return }
        let hash = CryptoHelper.sha256(string)
        
        // Loopback protection
        guard hash != lastSyncedHash else { return }
        lastSyncedHash = hash
        
        let payload = ClipboardPayload(
            contentType: "text/plain",
            data: string,
            sha256Hash: hash,
            timestamp: Date().timeIntervalSince1970 * 1000,
            sourceDevice: "macos"
        )
        
        NetworkManager.shared.send(type: "CLIPBOARD_SYNC", payload: payload)
        print("[ClipboardManager] Outgoing clipboard synced: \(string.prefix(20))...")
    }
    
    public func handleRemoteClipboard(_ payload: ClipboardPayload) {
        // Prevent loopback if originated from macos
        guard payload.sourceDevice != "macos" else { return }
        guard payload.sha256Hash != lastSyncedHash else { return }
        
        lastSyncedHash = payload.sha256Hash
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(payload.data, forType: .string)
        lastChangeCount = NSPasteboard.general.changeCount
        print("[ClipboardManager] Incoming clipboard written: \(payload.data.prefix(20))...")
    }
}
