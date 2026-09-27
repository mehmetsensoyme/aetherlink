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
        
        let isOutgoing = (payload.direction == "outgoing")
        if !isOutgoing {
            // Play system ringtone for incoming calls only
            ringtoneSound = NSSound(named: "Glass")
            ringtoneSound?.loops = true
            ringtoneSound?.play()
        }
        
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
                contentRect: NSRect(x: 0, y: 0, width: 370, height: 75),
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
            panel.isReleasedWhenClosed = false
            panel.contentView = NSHostingView(rootView: CallBannerView())
            self.callWindow = panel
        } else {
            callWindow?.contentView = NSHostingView(rootView: CallBannerView())
        }
        
        // Position at top-right of main screen with smooth slide-down and fade-in animation
        if let screen = NSScreen.main, let window = callWindow {
            let screenRect = screen.visibleFrame
            let x = screenRect.maxX - 390
            let targetY = screenRect.maxY - 85
            let startY = screenRect.maxY + 20
            
            window.setFrameOrigin(NSPoint(x: x, y: startY))
            window.alphaValue = 0.0
            window.orderFrontRegardless()
            
            NSAnimationContext.runAnimationGroup { ctx in
                ctx.duration = 0.35
                ctx.timingFunction = CAMediaTimingFunction(name: .easeOut)
                window.animator().setFrameOrigin(NSPoint(x: x, y: targetY))
                window.animator().alphaValue = 1.0
            }
        }
        
        let isOutgoing = (payload.direction == "outgoing")
        let callTitle = isOutgoing
            ? (payload.appType == .cellular ? "Giden Telefon Araması" : "Giden Arama")
            : (payload.appType == .cellular ? "Gelen Telefon Araması" : "Gelen Arama")
        
        // Post native macOS banner notification as well
        let notif = NotificationPayload(
            id: payload.callId,
            key: payload.callId,
            packageName: "telecom",
            appName: callTitle,
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
        
        if let window = callWindow, let screen = NSScreen.main, window.isVisible {
            let targetY = screen.visibleFrame.maxY + 30
            NSAnimationContext.runAnimationGroup({ ctx in
                ctx.duration = 0.25
                ctx.timingFunction = CAMediaTimingFunction(name: .easeIn)
                window.animator().setFrameOrigin(NSPoint(x: window.frame.origin.x, y: targetY))
                window.animator().alphaValue = 0.0
            }, completionHandler: {
                window.orderOut(nil)
            })
        } else {
            callWindow?.orderOut(nil)
        }
    }
}
