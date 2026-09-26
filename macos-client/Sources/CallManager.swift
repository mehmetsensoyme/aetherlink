import AppKit
import Foundation
import SwiftUI

@MainActor
public final class CallManager: ObservableObject {
    public static let shared = CallManager()
    
    @Published public var activeCall: CallIncomingPayload? = nil
    @Published public var isCallActive: Bool = false
    
    private var callWindow: NSPanel?
    private var ringtoneSound: NSSound?
    
    public init() {}
    
    public func handleIncomingCall(_ payload: CallIncomingPayload) {
        self.activeCall = payload
        self.isCallActive = false
        
        // Play system ringtone
        ringtoneSound = NSSound(named: "Glass")
        ringtoneSound?.loops = true
        ringtoneSound?.play()
        
        showCallBanner(payload)
    }
    
    public func answerCall() {
        guard let call = activeCall else { return }
        ringtoneSound?.stop()
        isCallActive = true
        
        let action = CallActionPayload(
            callId: call.callId,
            action: "answer",
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "CALL_ACTION", payload: action)
        print("[CallManager] Answered call: \(call.callId)")
    }
    
    public func declineCall() {
        guard let call = activeCall else { return }
        ringtoneSound?.stop()
        
        let action = CallActionPayload(
            callId: call.callId,
            action: "decline",
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "CALL_ACTION", payload: action)
        
        dismissCallBanner()
        print("[CallManager] Declined call: \(call.callId)")
    }
    
    public func endCall() {
        guard let call = activeCall else { return }
        let action = CallActionPayload(
            callId: call.callId,
            action: "hangup",
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "CALL_ACTION", payload: action)
        dismissCallBanner()
    }
    
    private func showCallBanner(_ payload: CallIncomingPayload) {
        if callWindow == nil {
            let panel = NSPanel(
                contentRect: NSRect(x: 0, y: 0, width: 340, height: 130),
                styleMask: [.borderless, .nonactivatingPanel],
                backing: .buffered,
                defer: false
            )
            panel.isFloatingPanel = true
            panel.level = .statusBar
            panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
            panel.isOpaque = false
            panel.backgroundColor = .clear
            panel.hasShadow = true
            panel.contentView = NSHostingView(rootView: CallBannerView())
            self.callWindow = panel
        }
        
        // Position at top-right of main screen (like native Apple notifications/calls)
        if let screen = NSScreen.main {
            let screenRect = screen.visibleFrame
            let x = screenRect.maxX - 360
            let y = screenRect.maxY - 140
            callWindow?.setFrameOrigin(NSPoint(x: x, y: y))
        }
        
        callWindow?.orderFrontRegardless()
        
        // Post native macOS banner notification as well
        let notif = NotificationPayload(
            id: payload.callId,
            key: payload.callId,
            packageName: "telecom",
            appName: payload.appType == .cellular ? "Gelen Telefon Araması" : "Gelen Arama",
            title: payload.callerName,
            text: payload.phoneNumber ?? "Bilinmeyen Numara",
            subText: nil,
            timestamp: payload.timestamp,
            canReply: false,
            replyPlaceholder: nil,
            appIconBase64: nil
        )
        NotificationManager.shared.displayNotification(notif)
    }
    
    public func dismissCallBanner() {
        ringtoneSound?.stop()
        activeCall = nil
        isCallActive = false
        callWindow?.orderOut(nil)
    }
}
