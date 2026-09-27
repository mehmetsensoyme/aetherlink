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
    
    public override init() {
        super.init()
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        
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
    
    public func displayNotification(_ payload: NotificationPayload) {
        guard !isNotificationsPaused else {
            print("[NotificationManager] Notification skipped (paused by user): \(payload.title)")
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
