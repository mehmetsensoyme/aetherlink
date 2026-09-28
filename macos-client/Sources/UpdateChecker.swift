import AppKit
import Foundation

public struct GitHubAssetDTO: Codable {
    public let name: String
    public let browser_download_url: String
}

public struct GitHubReleaseDTO: Codable {
    public let tag_name: String
    public let name: String?
    public let body: String?
    public let html_url: String
    public let published_at: String
    public let assets: [GitHubAssetDTO]
}

public struct UpdateInfo: Identifiable {
    public let id = UUID()
    public let currentVersion: String
    public let latestVersion: String
    public let title: String
    public let changelog: String
    public let downloadUrl: String
    public let releaseDate: String
}

@MainActor
public final class UpdateChecker: ObservableObject {
    public static let shared = UpdateChecker()
    
    public var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.5.0"
    }
    public let repoOwner = "mehmetsensoyme"
    public let repoName = "aetherlink"
    
    @Published public var availableUpdate: UpdateInfo? = nil
    @Published public var isChecking: Bool = false
    @Published public var checkError: String? = nil
    @Published public var isShowingSheet: Bool = false
    
    // In-App Download and Installation States
    @Published public var isDownloading: Bool = false
    @Published public var downloadProgress: Double = 0.0
    @Published public var installStatusText: String? = nil
    @Published public var installError: String? = nil
    
    public init() {}
    
    public func checkForUpdates(manual: Bool = false) async {
        isChecking = true
        checkError = nil
        
        guard let url = URL(string: "https://api.github.com/repos/\(repoOwner)/\(repoName)/releases/latest") else {
            isChecking = false
            return
        }
        
        var request = URLRequest(url: url)
        request.setValue("application/vnd.github.v3+json", forHTTPHeaderField: "Accept")
        request.setValue("AetherLink-Mac/\(currentVersion)", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 15
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                if manual { self.checkError = "Geçersiz sunucu yanıtı alındı." }
                isChecking = false
                return
            }
            
            if httpResponse.statusCode == 403 {
                if manual {
                    self.checkError = "GitHub API erişim sınırına ulaşıldı. Lütfen bir süre sonra tekrar deneyin."
                }
                isChecking = false
                return
            }
            
            guard httpResponse.statusCode == 200 else {
                if manual {
                    self.checkError = "Güncelleme kontrol edilemedi (HTTP \(httpResponse.statusCode))."
                }
                isChecking = false
                return
            }
            
            let release = try JSONDecoder().decode(GitHubReleaseDTO.self, from: data)
            let latestVer = release.tag_name.replacingOccurrences(of: "v", with: "")
            
            if isNewerVersion(current: currentVersion, latest: latestVer) {
                // Find DMG asset or fallback to HTML release page
                let downloadUrl = release.assets.first(where: { $0.name.hasSuffix(".dmg") })?.browser_download_url
                    ?? release.assets.first(where: { $0.name.hasSuffix(".pkg") })?.browser_download_url
                    ?? release.html_url
                
                let update = UpdateInfo(
                    currentVersion: currentVersion,
                    latestVersion: latestVer,
                    title: release.name ?? release.tag_name,
                    changelog: release.body ?? "Bu sürüm için açıklama eklenmedi.",
                    downloadUrl: downloadUrl,
                    releaseDate: release.published_at.prefix(10).description
                )
                self.availableUpdate = update
                self.isShowingSheet = true
                Task { @MainActor in
                    AetherWindowManager.shared.showUpdateWindow(updateInfo: update)
                }
            } else if manual {
                self.checkError = "Tebrikler! AetherLink en güncel sürümde (v\(currentVersion))."
            }
        } catch {
            if manual {
                self.checkError = "Güncelleme kontrol edilemedi: İnternet bağlantınızı kontrol edin."
            }
        }
        isChecking = false
    }
    
    public func downloadAndInstallUpdate(update: UpdateInfo) async {
        isDownloading = true
        downloadProgress = 0.0
        installError = nil
        installStatusText = "Güncelleme paketi indiriliyor..."
        
        guard let url = URL(string: update.downloadUrl) else {
            installError = "Geçersiz indirme adresi."
            isDownloading = false
            return
        }
        
        // If the URL is not a direct DMG download, open in browser
        if !update.downloadUrl.hasSuffix(".dmg") {
            NSWorkspace.shared.open(url)
            isDownloading = false
            installStatusText = nil
            return
        }
        
        do {
            let tempDmg = FileManager.default.temporaryDirectory.appendingPathComponent("AetherLink-Update.dmg")
            try? FileManager.default.removeItem(at: tempDmg)
            
            var request = URLRequest(url: url)
            request.setValue("AetherLink-Mac/\(currentVersion)", forHTTPHeaderField: "User-Agent")
            
            let (asyncBytes, response) = try await URLSession.shared.bytes(for: request)
            let total = Double(response.expectedContentLength)
            var data = Data()
            if total > 0 {
                data.reserveCapacity(Int(total))
            }
            
            var lastReported = 0
            for try await byte in asyncBytes {
                data.append(byte)
                if total > 0 && data.count - lastReported > 150_000 {
                    lastReported = data.count
                    let prog = min(Double(data.count) / total, 0.99)
                    self.downloadProgress = prog
                    self.installStatusText = "İndiriliyor: %\(Int(prog * 100))"
                }
            }
            try data.write(to: tempDmg)
            
            self.downloadProgress = 1.0
            self.installStatusText = "Paket açılıyor ve doğrulanıyor..."
            
            let mountPoint = "/tmp/aetherlink_dmg_mount"
            // Unmount if previously mounted
            let detachPre = Process()
            detachPre.executableURL = URL(fileURLWithPath: "/usr/bin/hdiutil")
            detachPre.arguments = ["detach", mountPoint, "-force"]
            try? detachPre.run()
            detachPre.waitUntilExit()
            
            try? FileManager.default.removeItem(atPath: mountPoint)
            try? FileManager.default.createDirectory(atPath: mountPoint, withIntermediateDirectories: true)
            
            // Attach DMG
            let attachTask = Process()
            attachTask.executableURL = URL(fileURLWithPath: "/usr/bin/hdiutil")
            attachTask.arguments = ["attach", tempDmg.path, "-nobrowse", "-readonly", "-mountpoint", mountPoint]
            try attachTask.run()
            attachTask.waitUntilExit()
            
            let sourceApp = "\(mountPoint)/AetherLink.app"
            guard FileManager.default.fileExists(atPath: sourceApp) else {
                throw NSError(domain: "AetherLinkUpdate", code: 1, userInfo: [NSLocalizedDescriptionKey: "DMG içinde AetherLink.app bulunamadı."])
            }
            
            var targetApp = Bundle.main.bundlePath
            if !targetApp.hasSuffix(".app") {
                targetApp = "/Applications/AetherLink.app"
            }
            
            self.installStatusText = "Uygulama güncellenip yeniden başlatılıyor..."
            
            // Generate clean relauncher script
            let scriptPath = "/tmp/aetherlink_relaunch.sh"
            let currentPid = ProcessInfo.processInfo.processIdentifier
            let scriptContent = """
            #!/bin/bash
            PID=\(currentPid)
            SRC="\(sourceApp)"
            DEST="\(targetApp)"
            MOUNT="\(mountPoint)"
            DMG="\(tempDmg.path)"

            # Wait for running app to exit cleanly
            while kill -0 "$PID" 2>/dev/null; do
                sleep 0.2
            done

            # Replace target app with new release
            rm -rf "$DEST"
            cp -R "$SRC" "$DEST"

            # Detach DMG
            hdiutil detach "$MOUNT" -force 2>/dev/null || true
            rm -rf "$MOUNT"
            rm -f "$DMG"
            rm -f "/tmp/aetherlink_relaunch.sh"

            # Launch updated app
            open "$DEST"
            """
            
            try scriptContent.write(toFile: scriptPath, atomically: true, encoding: .utf8)
            
            let chmodTask = Process()
            chmodTask.executableURL = URL(fileURLWithPath: "/bin/chmod")
            chmodTask.arguments = ["+x", scriptPath]
            try chmodTask.run()
            chmodTask.waitUntilExit()
            
            let launchTask = Process()
            launchTask.executableURL = URL(fileURLWithPath: "/bin/bash")
            launchTask.arguments = ["-c", "nohup /tmp/aetherlink_relaunch.sh >/dev/null 2>&1 &"]
            try launchTask.run()
            
            // Terminate current app after short delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                NSApp.terminate(nil)
            }
        } catch {
            self.isDownloading = false
            self.installError = "Güncelleme yüklenemedi: \(error.localizedDescription)"
            self.installStatusText = nil
        }
    }
    
    private func isNewerVersion(current: String, latest: String) -> Bool {
        let currentParts = current.replacingOccurrences(of: "v", with: "").split(separator: "-")[0].split(separator: ".").compactMap { Int($0) }
        let latestParts = latest.replacingOccurrences(of: "v", with: "").split(separator: "-")[0].split(separator: ".").compactMap { Int($0) }
        
        for i in 0..<max(currentParts.count, latestParts.count) {
            let c = i < currentParts.count ? currentParts[i] : 0
            let l = i < latestParts.count ? latestParts[i] : 0
            if l > c { return true }
            if l < c { return false }
        }
        return false
    }
}
