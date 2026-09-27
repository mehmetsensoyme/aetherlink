import AppKit
import SwiftUI

@MainActor
public final class AetherWindowManager: NSObject, ObservableObject, NSWindowDelegate {
    public static let shared = AetherWindowManager()
    
    private var telemetryWindow: NSWindow?
    private var pairingQRWindow: NSWindow?
    private var updateWindow: NSWindow?
    private var pairingPromptWindow: NSPanel?
    
    public override init() {
        super.init()
    }
    
    // MARK: - Device Telemetry Detail Window ("Cihaz Bilgileri")
    public func showDeviceTelemetryWindow() {
        if let win = telemetryWindow {
            win.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }
        
        let win = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 380, height: 570),
            styleMask: [.titled, .closable, .miniaturizable, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        win.title = "AetherLink • Cihaz Bilgileri"
        win.titleVisibility = .hidden
        win.titlebarAppearsTransparent = true
        win.isMovableByWindowBackground = true
        win.isReleasedWhenClosed = false
        win.isOpaque = false
        win.backgroundColor = .clear
        win.delegate = self
        win.center()
        
        let hostingView = NSHostingView(
            rootView: DeviceTelemetryDetailView(
                showInlineBack: false,
                onDismiss: { [weak self] in
                    self?.closeDeviceTelemetryWindow()
                }
            )
            .padding(.top, 24)
            .padding(.horizontal, 10)
            .padding(.bottom, 10)
        )
        win.contentView = hostingView
        self.telemetryWindow = win
        win.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
    
    public func closeDeviceTelemetryWindow() {
        telemetryWindow?.orderOut(nil)
        DeviceTelemetryManager.shared.isShowingDetailSheet = false
    }
    
    // MARK: - Pairing QR & PIN Code Window ("Cihaz Eşleştirme")
    public func showPairingQRWindow() {
        if let win = pairingQRWindow {
            win.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }
        
        let win = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 350, height: 520),
            styleMask: [.titled, .closable, .miniaturizable, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        win.title = "AetherLink • Cihaz Eşleştirme"
        win.titleVisibility = .hidden
        win.titlebarAppearsTransparent = true
        win.isMovableByWindowBackground = true
        win.isReleasedWhenClosed = false
        win.isOpaque = false
        win.backgroundColor = .clear
        win.delegate = self
        win.center()
        
        let hostingView = NSHostingView(
            rootView: PairingQRView(
                showInlineBack: false,
                onDismiss: { [weak self] in
                    self?.closePairingQRWindow()
                }
            )
            .padding(.top, 24)
            .padding(.horizontal, 10)
            .padding(.bottom, 10)
        )
        win.contentView = hostingView
        self.pairingQRWindow = win
        win.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
    
    public func closePairingQRWindow() {
        pairingQRWindow?.orderOut(nil)
        PairingManager.shared.isShowingQRModal = false
    }
    
    // MARK: - Update Notification Modal Window
    public func showUpdateWindow(updateInfo: UpdateInfo) {
        if let win = updateWindow {
            win.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }
        
        let win = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 490, height: 380),
            styleMask: [.titled, .closable, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        win.title = "AetherLink • Güncelleme Mevcut"
        win.titleVisibility = .hidden
        win.titlebarAppearsTransparent = true
        win.isMovableByWindowBackground = true
        win.isReleasedWhenClosed = false
        win.isOpaque = false
        win.backgroundColor = .clear
        win.delegate = self
        win.center()
        
        let hostingView = NSHostingView(
            rootView: UpdateModalView(updateInfo: updateInfo) { [weak self] in
                self?.closeUpdateWindow()
            }
            .padding(.top, 24)
            .padding(.horizontal, 10)
            .padding(.bottom, 10)
        )
        win.contentView = hostingView
        self.updateWindow = win
        win.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
    
    public func closeUpdateWindow() {
        updateWindow?.orderOut(nil)
        UpdateChecker.shared.isShowingSheet = false
    }
    
    // MARK: - Pairing Approval Prompt Window (Floating Panel)
    public func showPairingPromptWindow(request: PairingRequestPayload) {
        if let win = pairingPromptWindow {
            win.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }
        
        let panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 370, height: 300),
            styleMask: [.titled, .closable, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        panel.isFloatingPanel = true
        panel.level = .floating
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        panel.isReleasedWhenClosed = false
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.delegate = self
        panel.center()
        
        let hostingView = NSHostingView(
            rootView: PairingPromptView(
                request: request,
                onApprove: { [weak self] in
                    PairingManager.shared.approvePairing(request)
                    self?.closePairingPromptWindow()
                },
                onReject: { [weak self] in
                    PairingManager.shared.rejectPairing(request)
                    self?.closePairingPromptWindow()
                }
            )
            .padding(10)
        )
        panel.contentView = hostingView
        self.pairingPromptWindow = panel
        panel.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
    
    public func closePairingPromptWindow() {
        pairingPromptWindow?.orderOut(nil)
        PairingManager.shared.pendingPairingRequest = nil
    }
    
    // MARK: - NSWindowDelegate Safe Isolation
    // When the user clicks the standard Mac close button (traffic light),
    // we orderOut the window rather than deallocating or letting it terminate the application.
    public func windowShouldClose(_ sender: NSWindow) -> Bool {
        sender.orderOut(nil)
        if sender === telemetryWindow {
            DeviceTelemetryManager.shared.isShowingDetailSheet = false
        } else if sender === pairingQRWindow {
            PairingManager.shared.isShowingQRModal = false
        } else if sender === updateWindow {
            UpdateChecker.shared.isShowingSheet = false
        } else if sender === pairingPromptWindow {
            PairingManager.shared.pendingPairingRequest = nil
        }
        return false // Handled safely via orderOut
    }
}
