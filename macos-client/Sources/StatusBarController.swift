import AppKit
import SwiftUI

@MainActor
public final class StatusBarController: NSObject {
    public static let shared = StatusBarController()
    
    public var popover: NSPopover?
    
    public override init() {
        super.init()
        setupRouterListener()
    }
    
    private func setupRouterListener() {
        PopoverRouter.shared.onScreenChange = { [weak self] newSize in
            self?.updatePopoverSize(newSize)
        }
    }
    
    public func updatePopoverSize(_ newSize: NSSize) {
        if let popover = popover {
            NSAnimationContext.runAnimationGroup { context in
                context.duration = 0.22
                context.allowsImplicitAnimation = true
                popover.contentSize = newSize
            }
        }
        
        // Also ensure any hosting window in MenuBarExtra is seamlessly animated
        for window in NSApp.windows {
            if window.isVisible && (window.className.contains("MenuBar") || window.className.contains("Popover") || window.className.contains("Panel")) {
                window.isOpaque = false
                window.backgroundColor = .clear
                window.contentView?.wantsLayer = true
                window.contentView?.layer?.backgroundColor = .clear
                
                NSAnimationContext.runAnimationGroup { context in
                    context.duration = 0.22
                    context.allowsImplicitAnimation = true
                    window.setContentSize(newSize)
                }
            }
        }
    }
    
    public func configurePopover(_ popover: NSPopover) {
        self.popover = popover
        popover.behavior = .transient
        popover.animates = true
        popover.contentSize = PopoverRouter.shared.currentScreen.preferredSize
        
        if let window = popover.contentViewController?.view.window {
            window.isOpaque = false
            window.backgroundColor = .clear
            window.contentView?.wantsLayer = true
            window.contentView?.layer?.backgroundColor = .clear
        }
    }
}
