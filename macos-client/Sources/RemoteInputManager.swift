import AppKit
import CoreGraphics
import Foundation

@MainActor
public final class RemoteInputManager {
    public static let shared = RemoteInputManager()
    
    private var isLeftMouseDown: Bool = false
    
    public init() {}
    
    public func handleRemoteInput(_ payload: RemoteInputPayload) {
        switch payload.action {
        case "move":
            guard let dx = payload.dx, let dy = payload.dy else { return }
            moveCursor(dx: CGFloat(dx), dy: CGFloat(dy))
            
        case "click":
            leftClick()
            
        case "rightClick":
            rightClick()
            
        case "doubleClick":
            doubleClick()
            
        case "scroll":
            guard let dx = payload.dx, let dy = payload.dy else { return }
            scroll(dx: CGFloat(dx), dy: CGFloat(dy))
            
        case "key":
            if let keyStr = payload.key, !keyStr.isEmpty {
                typeString(keyStr)
            }
            
        default:
            break
        }
    }
    
    private func currentCursorPosition() -> CGPoint {
        // NSEvent.mouseLocation has Y inverted (bottom-up), CGEvent has Y top-down
        if let event = CGEvent(source: nil) {
            return event.location
        }
        let loc = NSEvent.mouseLocation
        let screenHeight = NSScreen.main?.frame.height ?? 1080
        return CGPoint(x: loc.x, y: screenHeight - loc.y)
    }
    
    private func moveCursor(dx: CGFloat, dy: CGFloat) {
        let current = currentCursorPosition()
        let newX = max(0, min(current.x + dx, (NSScreen.main?.frame.width ?? 1920)))
        let newY = max(0, min(current.y + dy, (NSScreen.main?.frame.height ?? 1080)))
        let targetPoint = CGPoint(x: newX, y: newY)
        
        let type: CGEventType = isLeftMouseDown ? .leftMouseDragged : .mouseMoved
        if let event = CGEvent(mouseEventSource: nil, mouseType: type, mouseCursorPosition: targetPoint, mouseButton: .left) {
            event.post(tap: .cghidEventTap)
        }
    }
    
    private func leftClick() {
        let current = currentCursorPosition()
        if let down = CGEvent(mouseEventSource: nil, mouseType: .leftMouseDown, mouseCursorPosition: current, mouseButton: .left),
           let up = CGEvent(mouseEventSource: nil, mouseType: .leftMouseUp, mouseCursorPosition: current, mouseButton: .left) {
            down.post(tap: .cghidEventTap)
            up.post(tap: .cghidEventTap)
        }
    }
    
    private func rightClick() {
        let current = currentCursorPosition()
        if let down = CGEvent(mouseEventSource: nil, mouseType: .rightMouseDown, mouseCursorPosition: current, mouseButton: .right),
           let up = CGEvent(mouseEventSource: nil, mouseType: .rightMouseUp, mouseCursorPosition: current, mouseButton: .right) {
            down.post(tap: .cghidEventTap)
            up.post(tap: .cghidEventTap)
        }
    }
    
    private func doubleClick() {
        let current = currentCursorPosition()
        if let down1 = CGEvent(mouseEventSource: nil, mouseType: .leftMouseDown, mouseCursorPosition: current, mouseButton: .left),
           let up1 = CGEvent(mouseEventSource: nil, mouseType: .leftMouseUp, mouseCursorPosition: current, mouseButton: .left),
           let down2 = CGEvent(mouseEventSource: nil, mouseType: .leftMouseDown, mouseCursorPosition: current, mouseButton: .left),
           let up2 = CGEvent(mouseEventSource: nil, mouseType: .leftMouseUp, mouseCursorPosition: current, mouseButton: .left) {
            
            down1.setIntegerValueField(.mouseEventClickState, value: 1)
            up1.setIntegerValueField(.mouseEventClickState, value: 1)
            down2.setIntegerValueField(.mouseEventClickState, value: 2)
            up2.setIntegerValueField(.mouseEventClickState, value: 2)
            
            down1.post(tap: .cghidEventTap)
            up1.post(tap: .cghidEventTap)
            down2.post(tap: .cghidEventTap)
            up2.post(tap: .cghidEventTap)
        }
    }
    
    private func scroll(dx: CGFloat, dy: CGFloat) {
        // Scroll wheel event
        if let scrollEvent = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 2, wheel1: Int32(dy), wheel2: Int32(-dx), wheel3: 0) {
            scrollEvent.post(tap: .cghidEventTap)
        }
    }
    
    private func typeString(_ text: String) {
        if text == "\n" || text == "\r" {
            // Return key: keycode 36
            postKey(keyCode: 36)
            return
        }
        if text == "\u{08}" || text == "Backspace" {
            // Delete / Backspace: keycode 51
            postKey(keyCode: 51)
            return
        }
        if text == "\t" {
            // Tab: keycode 48
            postKey(keyCode: 48)
            return
        }
        
        for char in text.utf16 {
            var uniChar = char
            if let down = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true),
               let up = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false) {
                down.keyboardSetUnicodeString(stringLength: 1, unicodeString: &uniChar)
                up.keyboardSetUnicodeString(stringLength: 1, unicodeString: &uniChar)
                down.post(tap: .cghidEventTap)
                up.post(tap: .cghidEventTap)
            }
        }
    }
    
    private func postKey(keyCode: CGKeyCode) {
        if let down = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: true),
           let up = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: false) {
            down.post(tap: .cghidEventTap)
            up.post(tap: .cghidEventTap)
        }
    }
}
