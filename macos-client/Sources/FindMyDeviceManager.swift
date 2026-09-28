import AppKit
import Foundation
import SwiftUI

@MainActor
public final class FindMyDeviceManager: ObservableObject {
    public static let shared = FindMyDeviceManager()
    
    @Published public var isPhoneRinging: Bool = false
    @Published public var isMacRinging: Bool = false
    
    private var alarmSound: NSSound?
    private var ringDurationTimer: Timer?
    
    public init() {}
    
    // MARK: - Ring Phone (Mac -> Android)
    public func toggleRingPhone() {
        if isPhoneRinging {
            stopRingingPhone()
        } else {
            ringPhone()
        }
    }
    
    public func ringPhone() {
        isPhoneRinging = true
        let payload = FindMyDevicePayload(action: "ring", sourceDevice: "macos", isRinging: true)
        NetworkManager.shared.send(type: "FIND_MY_PHONE_REQUEST", payload: payload)
        print("[FindMyDevice] Sent FIND_MY_PHONE_REQUEST: ring")
        
        // Auto-reset state after 45s if no explicit stop
        ringDurationTimer?.invalidate()
        ringDurationTimer = Timer.scheduledTimer(withTimeInterval: 45.0, repeats: false) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.isPhoneRinging = false
            }
        }
    }
    
    public func stopRingingPhone() {
        isPhoneRinging = false
        ringDurationTimer?.invalidate()
        ringDurationTimer = nil
        let payload = FindMyDevicePayload(action: "stop", sourceDevice: "macos", isRinging: false)
        NetworkManager.shared.send(type: "FIND_MY_PHONE_REQUEST", payload: payload)
        print("[FindMyDevice] Sent FIND_MY_PHONE_REQUEST: stop")
    }
    
    public func handlePhoneStatusUpdate(_ payload: FindMyDevicePayload) {
        if let ringing = payload.isRinging {
            self.isPhoneRinging = ringing
            if !ringing {
                ringDurationTimer?.invalidate()
                ringDurationTimer = nil
            }
        }
    }
    
    // MARK: - Ring Mac (Android -> Mac)
    public func handleIncomingRingMacRequest(_ payload: FindMyDevicePayload) {
        if payload.action == "stop" {
            silenceMac()
            return
        }
        
        isMacRinging = true
        print("[FindMyDevice] Phone triggered Find My Mac!")
        
        // Play high-visibility alert sound in loop
        alarmSound?.stop()
        alarmSound = NSSound(named: "Glass")
        alarmSound?.loops = true
        alarmSound?.play()
        
        // Post native high-priority system notification
        let notif = NotificationPayload(
            id: UUID().uuidString,
            key: "find_my_mac_alert",
            packageName: "system",
            appName: "AetherLink Cihazımı Bul",
            title: "🔔 Telefonunuz Mac'inizi Arıyor!",
            text: "Cihazınızı bulmak için ses çalınıyor. Durdurmak için tıklayın.",
            subText: nil,
            timestamp: Date().timeIntervalSince1970 * 1000,
            canReply: false,
            replyPlaceholder: nil,
            appIconBase64: nil
        )
        NotificationManager.shared.displayNotification(notif)
    }
    
    public func silenceMac() {
        alarmSound?.stop()
        alarmSound = nil
        isMacRinging = false
        
        let payload = FindMyDevicePayload(action: "stop", sourceDevice: "macos", isRinging: false)
        NetworkManager.shared.send(type: "FIND_MY_MAC_RESPONSE", payload: payload)
        print("[FindMyDevice] Mac alarm silenced")
    }
}
