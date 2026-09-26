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
    
    public let currentVersion = "1.2.1"
    public let repoOwner = "mehmetsensoyme"
    public let repoName = "aetherlink"
    
    @Published public var availableUpdate: UpdateInfo? = nil
    @Published public var isChecking: Bool = false
    @Published public var checkError: String? = nil
    @Published public var isShowingSheet: Bool = false
    
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
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                if manual {
                    self.checkError = "Şu anda en güncel sürümü kullanıyorsunuz veya GitHub bağlantısı sağlanamadı."
                }
                isChecking = false
                return
            }
            
            let release = try JSONDecoder().decode(GitHubReleaseDTO.self, from: data)
            let latestVer = release.tag_name.replacingOccurrences(of: "v", with: "")
            
            if isNewerVersion(current: currentVersion, latest: latestVer) {
                let downloadUrl = release.assets.first(where: { $0.name.hasSuffix(".dmg") })?.browser_download_url ?? release.html_url
                
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
                self.checkError = "Güncelleme denetlenirken hata oluştu: \(error.localizedDescription)"
            }
        }
        isChecking = false
    }
    
    private func isNewerVersion(current: String, latest: String) -> Bool {
        let currentParts = current.split(separator: ".").compactMap { Int($0) }
        let latestParts = latest.split(separator: ".").compactMap { Int($0) }
        
        for i in 0..<max(currentParts.count, latestParts.count) {
            let c = i < currentParts.count ? currentParts[i] : 0
            let l = i < latestParts.count ? latestParts[i] : 0
            if l > c { return true }
            if l < c { return false }
        }
        return false
    }
}
