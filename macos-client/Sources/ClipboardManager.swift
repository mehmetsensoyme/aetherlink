import AppKit
import Foundation

@MainActor
public final class ClipboardManager: ObservableObject {
    public static let shared = ClipboardManager()
    
    @Published public var isMonitoringActive: Bool = true
    private var lastChangeCount: Int = 0
    private var lastSyncedHash: String = ""
    private var timer: Timer?
    
    public init() {}
    
    public func toggleMonitoring() {
        isMonitoringActive.toggle()
    }
    
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
        guard isMonitoringActive else { return }
        let currentCount = NSPasteboard.general.changeCount
        guard currentCount != lastChangeCount else { return }
        lastChangeCount = currentCount
        
        // 1. Check for image content (Screenshot, copied photo, graphics)
        if let image = NSPasteboard.general.readObjects(forClasses: [NSImage.self], options: nil)?.first as? NSImage,
           let tiff = image.tiffRepresentation,
           let bitmap = NSBitmapImageRep(data: tiff),
           let pngData = bitmap.representation(using: .png, properties: [:]) {
            // Limit to 10MB to guarantee zero-lag and prevent WebSocket buffer spikes
            if (1...10_000_000).contains(pngData.count) {
                let base64 = pngData.base64EncodedString()
                let hash = CryptoHelper.sha256(base64)
                guard hash != lastSyncedHash else { return }
                lastSyncedHash = hash
                
                let payload = ClipboardPayload(
                    contentType: "image/png",
                    data: base64,
                    sha256Hash: hash,
                    timestamp: Date().timeIntervalSince1970 * 1000,
                    sourceDevice: "macos"
                )
                NetworkManager.shared.send(type: "CLIPBOARD_SYNC", payload: payload)
                print("[ClipboardManager] Outgoing image clipboard synced (\(pngData.count) bytes)")
                return
            }
        }
        
        // 2. Check for plain text
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
        print("[ClipboardManager] Outgoing text clipboard synced: \(string.prefix(20))...")
    }
    
    public func handleRemoteClipboard(_ payload: ClipboardPayload) {
        // Prevent loopback if originated from macos
        guard payload.sourceDevice != "macos" else { return }
        guard payload.sha256Hash != lastSyncedHash else { return }
        
        lastSyncedHash = payload.sha256Hash
        
        if payload.contentType == "image/png" || payload.contentType == "image/jpeg" {
            guard let data = Data(base64Encoded: payload.data), let image = NSImage(data: data) else { return }
            NSPasteboard.general.clearContents()
            NSPasteboard.general.writeObjects([image])
            lastChangeCount = NSPasteboard.general.changeCount
            print("[ClipboardManager] Incoming image clipboard written (\(data.count) bytes)")
        } else {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(payload.data, forType: .string)
            lastChangeCount = NSPasteboard.general.changeCount
            print("[ClipboardManager] Incoming text clipboard written: \(payload.data.prefix(20))...")
        }
    }
}
