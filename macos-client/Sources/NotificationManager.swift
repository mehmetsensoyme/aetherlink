import Foundation
import UserNotifications

@MainActor
public final class NotificationManager: NSObject, UNUserNotificationCenterDelegate {
    public static let shared = NotificationManager()
    
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
    }
    
    public func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            print("[NotificationManager] Notification permission granted: \(granted)")
        }
    }
    
    public func displayNotification(_ payload: NotificationPayload) {
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
