import AppKit
import Foundation

@MainActor
public final class AetherShareManager: ObservableObject {
    public static let shared = AetherShareManager()
    
    @Published public var lastReceivedFile: String? = nil
    
    public init() {}
    
    // MARK: - Incoming Share from Android
    public func handleIncomingUrl(_ urlString: String) {
        guard let url = URL(string: urlString.trimmingCharacters(in: .whitespacesAndNewlines)) else { return }
        print("[AetherShareManager] Opening shared URL: \(url)")
        NSWorkspace.shared.open(url)
        
        let notif = NotificationPayload(
            id: UUID().uuidString,
            key: "shared_url",
            packageName: "system",
            appName: "AetherDrop",
            title: "🌐 Telefondan Bağlantı Açıldı",
            text: url.absoluteString,
            subText: nil,
            timestamp: Date().timeIntervalSince1970 * 1000,
            canReply: false,
            replyPlaceholder: nil,
            appIconBase64: nil
        )
        NotificationManager.shared.displayNotification(notif)
    }
    
    public func handleIncomingText(_ text: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
        
        let notif = NotificationPayload(
            id: UUID().uuidString,
            key: "shared_text",
            packageName: "system",
            appName: "AetherDrop",
            title: "📋 Telefondan Metin Alındı",
            text: text,
            subText: "Panoya kopyalandı",
            timestamp: Date().timeIntervalSince1970 * 1000,
            canReply: false,
            replyPlaceholder: nil,
            appIconBase64: nil
        )
        NotificationManager.shared.displayNotification(notif)
    }
    
    public func handleIncomingFile(fileName: String, base64Data: String) {
        guard let data = Data(base64Encoded: base64Data) else {
            print("[AetherShareManager] Failed to decode base64 file data for: \(fileName)")
            return
        }
        
        let downloads = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
        var destination = downloads.appendingPathComponent(fileName)
        
        // Auto-rename if file already exists
        var counter = 1
        let nameWithoutExt = destination.deletingPathExtension().lastPathComponent
        let ext = destination.pathExtension
        while FileManager.default.fileExists(atPath: destination.path) {
            let newName = ext.isEmpty ? "\(nameWithoutExt)_\(counter)" : "\(nameWithoutExt)_\(counter).\(ext)"
            destination = downloads.appendingPathComponent(newName)
            counter += 1
        }
        
        do {
            try data.write(to: destination)
            self.lastReceivedFile = destination.lastPathComponent
            print("[AetherShareManager] Saved file to Downloads: \(destination.path)")
            
            // Play success sound
            NSSound(named: "Glass")?.play()
            
            // Reveal in Finder
            NSWorkspace.shared.activateFileViewerSelecting([destination])
            
            let notif = NotificationPayload(
                id: UUID().uuidString,
                key: "shared_file",
                packageName: "system",
                appName: "AetherDrop",
                title: "📥 Dosya Alındı: \(destination.lastPathComponent)",
                text: "İndirilenler klasörüne kaydedildi (\(ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file)))",
                subText: nil,
                timestamp: Date().timeIntervalSince1970 * 1000,
                canReply: false,
                replyPlaceholder: nil,
                appIconBase64: nil
            )
            NotificationManager.shared.displayNotification(notif)
        } catch {
            print("[AetherShareManager] Error saving file: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Outgoing Share (Mac -> Android)
    public func sendFileToPhone(fileURL: URL) {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        let base64 = data.base64EncodedString()
        struct ShareFilePayload: Codable {
            let fileName: String
            let base64Data: String
            let fileSize: Int
            let timestamp: Double
        }
        let payload = ShareFilePayload(
            fileName: fileURL.lastPathComponent,
            base64Data: base64,
            fileSize: data.count,
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "SHARE_FILE", payload: payload)
        print("[AetherShareManager] Sent file to phone: \(fileURL.lastPathComponent) (\(data.count) bytes)")
    }
}
