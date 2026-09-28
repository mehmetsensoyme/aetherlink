import AppKit
import CoreGraphics
import Foundation

@MainActor
public final class PresenterManager {
    public static let shared = PresenterManager()
    
    public init() {}
    
    public func handleCommand(_ action: String) {
        print("[PresenterManager] Executing presentation command: \(action)")
        switch action {
        case "next":
            // Right Arrow key (124)
            postKey(keyCode: 124)
            
        case "prev":
            // Left Arrow key (123)
            postKey(keyCode: 123)
            
        case "fullscreen":
            // Keynote: Cmd + Option + P or PowerPoint: F5 (96)
            postKeyWithModifiers(keyCode: 35, flags: [.maskCommand, .maskAlternate])
            
        case "exit":
            // Escape key (53)
            postKey(keyCode: 53)
            
        case "black":
            // 'B' key (11)
            postKey(keyCode: 11)
            
        case "white":
            // 'W' key (13)
            postKey(keyCode: 13)
            
        default:
            print("[PresenterManager] Unknown presenter command: \(action)")
        }
    }
    
    private func postKey(keyCode: CGKeyCode) {
        if let down = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: true),
           let up = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: false) {
            down.post(tap: .cghidEventTap)
            up.post(tap: .cghidEventTap)
        }
    }
    
    private func postKeyWithModifiers(keyCode: CGKeyCode, flags: CGEventFlags) {
        if let down = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: true),
           let up = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: false) {
            down.flags = flags
            up.flags = flags
            down.post(tap: .cghidEventTap)
            up.post(tap: .cghidEventTap)
        }
    }
}
