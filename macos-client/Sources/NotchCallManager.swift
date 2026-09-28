import AppKit
import Foundation
import SwiftUI

@MainActor
public final class NotchCallManager: ObservableObject {
    public static let shared = NotchCallManager()
    
    @Published public var isPresented: Bool = false
    @Published public var isExpanded: Bool = false
    @Published public var isCallActive: Bool = false
    @Published public var callDurationSeconds: Int = 0
    
    @Published public var callerName: String = ""
    @Published public var phoneNumber: String = ""
    @Published public var callId: String = ""
    @Published public var appType: CallAppType = .cellular
    @Published public var avatarBase64: String? = nil
    @Published public var hasNotch: Bool = false
    @Published public var isAttachedToNotch: Bool = true
    @Published public var notchClearance: CGFloat = 34
    @Published public var waveAnimation: Bool = false
    
    private var window: NSPanel?
    private var ringtoneSound: NSSound?
    private var durationTimer: Timer?
    private var waveTimer: Timer?
    
    public var formattedDuration: String {
        let mins = callDurationSeconds / 60
        let secs = callDurationSeconds % 60
        return String(format: "%02d:%02d", mins, secs)
    }
    
    public init() {}
    
    public func show(
        caller: String,
        number: String,
        callId: String = UUID().uuidString,
        appType: CallAppType = .cellular,
        avatarBase64: String? = nil
    ) {
        // If already presenting this call, seamlessly update caller name/number without resetting ringtone
        if isPresented && (self.callId == callId || self.callId.isEmpty) {
            withAnimation(.easeInOut(duration: 0.25)) {
                if !caller.isEmpty && caller != "Bilinmeyen Numara" {
                    self.callerName = caller
                } else if self.callerName.isEmpty || self.callerName == "Bilinmeyen Numara" {
                    self.callerName = caller
                }
                if !number.isEmpty {
                    self.phoneNumber = number
                }
                if let avatar = avatarBase64 {
                    self.avatarBase64 = avatar
                }
            }
            return
        }

        self.callerName = caller.isEmpty ? "Bilinmeyen Numara" : caller
        self.phoneNumber = number
        self.callId = callId
        self.appType = appType
        self.avatarBase64 = avatarBase64
        self.isCallActive = false
        self.callDurationSeconds = 0
        
        MediaContinuityManager.shared.pauseMediaForIncomingCall()
        
        durationTimer?.invalidate()
        durationTimer = nil
        
        waveTimer?.invalidate()
        waveTimer = Timer.scheduledTimer(withTimeInterval: 1.1, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                withAnimation(.easeInOut(duration: 1.0)) {
                    self?.waveAnimation.toggle()
                }
            }
        }
        
        // Ringtone for incoming call
        ringtoneSound?.stop()
        ringtoneSound = NSSound(named: "Glass")
        ringtoneSound?.loops = true
        ringtoneSound?.play()
        
        setupAndDisplayPanel()
    }

    public func updateCallInfo(caller: String, number: String) {
        withAnimation(.easeInOut(duration: 0.25)) {
            if !caller.isEmpty && caller != "Bilinmeyen Numara" {
                self.callerName = caller
            }
            if !number.isEmpty {
                self.phoneNumber = number
            }
        }
    }
    
    public func show(payload: CallIncomingPayload) {
        show(
            caller: payload.displayName,
            number: payload.phoneNumber ?? "",
            callId: payload.callId,
            appType: payload.appType,
            avatarBase64: payload.avatarBase64
        )
    }
    
    public func acceptCall() {
        ringtoneSound?.stop()
        isCallActive = true
        callDurationSeconds = 0
        
        durationTimer?.invalidate()
        durationTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.callDurationSeconds += 1
            }
        }
        
        // Notify Android via socket
        let action = CallActionPayload(
            callId: self.callId,
            action: "answer",
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "CALL_ACTION", payload: action)
        NetworkManager.shared.send(type: "ACCEPT_CALL", payload: ["callId": self.callId])
        
        let phoneIp = NetworkManager.shared.connectedDeviceIP ?? "192.168.1.4"
        CallAudioStreamEngine.shared.start(phoneIp: phoneIp)
        print("[NotchCallManager] Accepted call and started audio relay: \(callId)")
    }
    
    public func rejectCall() {
        ringtoneSound?.stop()
        durationTimer?.invalidate()
        durationTimer = nil
        
        let action = CallActionPayload(
            callId: self.callId,
            action: "decline",
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "CALL_ACTION", payload: action)
        NetworkManager.shared.send(type: "REJECT_CALL", payload: ["callId": self.callId])
        print("[NotchCallManager] Rejected call: \(callId)")
        
        dismiss()
    }
    
    public func endCall() {
        ringtoneSound?.stop()
        durationTimer?.invalidate()
        durationTimer = nil
        
        let action = CallActionPayload(
            callId: self.callId,
            action: "hangup",
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "CALL_ACTION", payload: action)
        NetworkManager.shared.send(type: "REJECT_CALL", payload: ["callId": self.callId])
        print("[NotchCallManager] Ended call: \(callId)")
        
        dismiss()
    }
    
    public func dismiss() {
        ringtoneSound?.stop()
        durationTimer?.invalidate()
        durationTimer = nil
        waveTimer?.invalidate()
        waveTimer = nil
        
        MediaContinuityManager.shared.resumeMediaAfterCall()
        CallAudioStreamEngine.shared.stop()
        
        withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
            self.isExpanded = false
            self.isPresented = false
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) { [weak self] in
            guard let self = self, !self.isExpanded else { return }
            self.window?.orderOut(nil)
            self.isCallActive = false
        }
    }
    
    private func setupAndDisplayPanel() {
        guard let screen = NSScreen.main else { return }
        
        let hasNotch = (screen.safeAreaInsets.top > 0)
        self.hasNotch = hasNotch
        
        let position = CallManager.shared.bannerPosition
        let isAttached = (position == .notch && hasNotch)
        self.isAttachedToNotch = isAttached
        
        let panelWidth: CGFloat = 430
        let panelHeight: CGFloat = isAttached ? 104 : 74
        self.notchClearance = isAttached ? max(screen.safeAreaInsets.top, 34) : 0
        
        if window == nil {
            let panel = NSPanel(
                contentRect: NSRect(x: 0, y: 0, width: panelWidth, height: panelHeight),
                styleMask: [.borderless, .nonactivatingPanel],
                backing: .buffered,
                defer: false
            )
            panel.isFloatingPanel = true
            panel.level = .statusBar
            panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
            panel.isOpaque = false
            panel.backgroundColor = .clear
            panel.hasShadow = false
            panel.isReleasedWhenClosed = false
            panel.contentView = NSHostingView(rootView: NotchCallView())
            self.window = panel
        } else {
            window?.contentView = NSHostingView(rootView: NotchCallView())
        }
        
        guard let panel = window else { return }
        
        let originX = screen.frame.midX - (panelWidth / 2)
        let originY: CGFloat
        if isAttached {
            // Attached to notch: Flush with physical top edge of screen
            originY = screen.frame.maxY - panelHeight
        } else {
            // Floating pill: Positioned comfortably below menu bar / notch with an 8pt gap
            originY = screen.visibleFrame.maxY - panelHeight - 8
        }
        
        panel.setFrame(NSRect(x: originX, y: originY, width: panelWidth, height: panelHeight), display: true)
        
        self.isPresented = true
        self.isExpanded = false
        panel.orderFrontRegardless()
        
        DispatchQueue.main.async {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.75)) {
                self.isExpanded = true
            }
        }
    }
}
