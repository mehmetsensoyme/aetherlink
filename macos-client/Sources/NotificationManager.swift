import AppKit
import Foundation
import UserNotifications

@MainActor
public final class NotificationManager: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    public static let shared = NotificationManager()
    
    @Published public var isAuthorized: Bool = false
    @Published public var authorizationStatus: UNAuthorizationStatus = .notDetermined
    @Published public var isNotificationsPaused: Bool = false
    
    public func toggleNotificationsPaused() {
        isNotificationsPaused.toggle()
    }
    
    @Published public var smartDuplicateFilterEnabled: Bool = true {
        didSet {
            UserDefaults.standard.set(smartDuplicateFilterEnabled, forKey: "aetherlink_smart_notif_filter")
            broadcastSuppressedPackagesToAndroid()
        }
    }
    
    @Published public var blacklistedPackages: Set<String> = [] {
        didSet {
            UserDefaults.standard.set(Array(blacklistedPackages), forKey: "aetherlink_notif_blacklist")
            broadcastSuppressedPackagesToAndroid()
        }
    }
    
    public struct DuplicateAppRule {
        public let androidPackages: Set<String>
        public let macBundleIds: Set<String>
        public let macAppNames: Set<String>
    }
    
    public let duplicateRules: [DuplicateAppRule] = [
        DuplicateAppRule(
            androidPackages: ["com.whatsapp", "com.whatsapp.w4b"],
            macBundleIds: ["net.whatsapp.WhatsApp", "net.whatsapp.WhatsApp.desktop"],
            macAppNames: ["whatsapp"]
        ),
        DuplicateAppRule(
            androidPackages: ["org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus"],
            macBundleIds: ["ru.keepcoder.Telegram", "com.tdesktop.Telegram"],
            macAppNames: ["telegram"]
        ),
        DuplicateAppRule(
            androidPackages: ["com.Slack"],
            macBundleIds: ["com.tinyspeck.slackmacgap"],
            macAppNames: ["slack"]
        ),
        DuplicateAppRule(
            androidPackages: ["com.discord"],
            macBundleIds: ["com.hnc.Discord"],
            macAppNames: ["discord"]
        ),
        DuplicateAppRule(
            androidPackages: ["com.spotify.music", "com.spotify.lite"],
            macBundleIds: ["com.spotify.client"],
            macAppNames: ["spotify"]
        ),
        DuplicateAppRule(
            androidPackages: ["org.thoughtcrime.securesms"],
            macBundleIds: ["org.whispersystems.signal-desktop"],
            macAppNames: ["signal"]
        ),
        DuplicateAppRule(
            androidPackages: ["com.microsoft.teams", "com.microsoft.teams2"],
            macBundleIds: ["com.microsoft.teams", "com.microsoft.teams2"],
            macAppNames: ["microsoft teams", "teams"]
        ),
        DuplicateAppRule(
            androidPackages: ["com.google.android.apps.messaging", "com.samsung.android.messaging"],
            macBundleIds: ["com.apple.MobileSMS"],
            macAppNames: ["messages", "mesajlar"]
        )
    ]
    
    public override init() {
        super.init()
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        
        self.smartDuplicateFilterEnabled = UserDefaults.standard.object(forKey: "aetherlink_smart_notif_filter") as? Bool ?? true
        if let savedBlacklist = UserDefaults.standard.stringArray(forKey: "aetherlink_notif_blacklist") {
            self.blacklistedPackages = Set(savedBlacklist)
        }
        
        // Listen for Mac application launches / exits to update active duplicate suppression
        let centerWorkspace = NSWorkspace.shared.notificationCenter
        centerWorkspace.addObserver(forName: NSWorkspace.didLaunchApplicationNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.broadcastSuppressedPackagesToAndroid()
            }
        }
        centerWorkspace.addObserver(forName: NSWorkspace.didTerminateApplicationNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.broadcastSuppressedPackagesToAndroid()
            }
        }
        
        // Register Inline Reply Action
        let replyAction = UNTextInputNotificationAction(
            identifier: "AETHER_REPLY_ACTION",
            title: "Yanıtla",
            options: [],
            textInputButtonTitle: "Gönder",
            textInputPlaceholder: "Mesajınızı yazın..."
        )
        
        let category = UNNotificationCategory(
            identifier: "AETHER_MESSAGE_CATEGORY",
            actions: [replyAction],
            intentIdentifiers: [],
            options: []
        )
        
        center.setNotificationCategories([category])
        refreshStatus()
    }
    
    public func refreshStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            Task { @MainActor in
                self.authorizationStatus = settings.authorizationStatus
                self.isAuthorized = (settings.authorizationStatus == .authorized)
            }
        }
    }
    
    public func requestAuthorization(completion: ((Bool) -> Void)? = nil) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            Task { @MainActor in
                self.isAuthorized = granted
                self.refreshStatus()
                if let error = error {
                    print("[NotificationManager] Notification authorization error: \(error)")
                } else {
                    print("[NotificationManager] Notification authorization granted: \(granted)")
                }
                completion?(granted)
            }
        }
    }
    
    public func openNotificationSettings() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.notifications") {
            NSWorkspace.shared.open(url)
        }
    }
    
    public func isAppAlreadyHandledOnMac(packageName: String, appName: String) -> Bool {
        guard smartDuplicateFilterEnabled else { return false }
        let runningApps = NSWorkspace.shared.runningApplications
        let lowerAppName = appName.lowercased()
        
        for rule in duplicateRules {
            if rule.androidPackages.contains(packageName) || rule.macAppNames.contains(lowerAppName) {
                let isRunning = runningApps.contains { app in
                    if let bid = app.bundleIdentifier, rule.macBundleIds.contains(bid) {
                        return true
                    }
                    if let name = app.localizedName?.lowercased(), rule.macAppNames.contains(name) {
                        return true
                    }
                    return false
                }
                if isRunning {
                    return true
                }
            }
        }
        return false
    }
    
    public func getSuppressedAndroidPackages() -> [String] {
        var suppressed = Set(blacklistedPackages)
        if smartDuplicateFilterEnabled {
            let runningApps = NSWorkspace.shared.runningApplications
            for rule in duplicateRules {
                let isRunning = runningApps.contains { app in
                    if let bid = app.bundleIdentifier, rule.macBundleIds.contains(bid) {
                        return true
                    }
                    if let name = app.localizedName?.lowercased(), rule.macAppNames.contains(name) {
                        return true
                    }
                    return false
                }
                if isRunning {
                    suppressed.formUnion(rule.androidPackages)
                }
            }
        }
        return Array(suppressed)
    }
    
    public func broadcastSuppressedPackagesToAndroid() {
        struct FilterPayload: Codable {
            let suppressedPackages: [String]
            let timestamp: Double
        }
        let list = getSuppressedAndroidPackages()
        NetworkManager.shared.send(
            type: "NOTIFICATION_FILTER_SYNC",
            payload: FilterPayload(suppressedPackages: list, timestamp: Date().timeIntervalSince1970 * 1000)
        )
        print("[NotificationManager] Synced suppressed packages to Android: \(list)")
    }
    
    public func displayNotification(_ payload: NotificationPayload) {
        guard !isNotificationsPaused else {
            print("[NotificationManager] Notification skipped (paused by user): \(payload.title)")
            return
        }
        
        // 1. Blacklist check
        if blacklistedPackages.contains(payload.packageName) {
            print("[NotificationManager] 🔇 Suppressed blacklisted notification from \(payload.appName) (\(payload.packageName))")
            return
        }
        
        // 2. Smart Duplicate Mac App Check
        if isAppAlreadyHandledOnMac(packageName: payload.packageName, appName: payload.appName) {
            print("[NotificationManager] 🔇 Suppressed duplicate notification from \(payload.appName) (\(payload.packageName)) because it is already active on Mac.")
            return
        }
        let content = UNMutableNotificationContent()
        content.title = "\(payload.appName): \(payload.title)"
        content.body = payload.text
        content.sound = .default
        content.userInfo = ["key": payload.key]
        
        if payload.canReply {
            content.categoryIdentifier = "AETHER_MESSAGE_CATEGORY"
        }
        
        let request = UNNotificationRequest(
            identifier: payload.id,
            content: content,
            trigger: nil // deliver immediately
        )
        
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("[NotificationManager] Error posting notification: \(error)")
            }
        }
    }
    
    // Ensure notifications display even when app is active
    public nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if #available(macOS 11.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound, .badge])
        }
    }
    
    // Handle inline reply entered by user on macOS
    public nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == "AETHER_REPLY_ACTION",
           let textResponse = response as? UNTextInputNotificationResponse {
            let key = response.notification.request.content.userInfo["key"] as? String ?? ""
            let replyText = textResponse.userText
            
            Task { @MainActor in
                let replyPayload = NotificationReplyPayload(
                    notificationKey: key,
                    replyText: replyText,
                    timestamp: Date().timeIntervalSince1970 * 1000
                )
                NetworkManager.shared.send(type: "NOTIFICATION_REPLY", payload: replyPayload)
                print("[NotificationManager] Sent inline reply for \(key): \(replyText)")
            }
        }
        completionHandler()
    }
}
