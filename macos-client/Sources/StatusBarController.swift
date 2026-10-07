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
        PopoverRouter.shared.onScreenChange = { [weak self] _ in
            DispatchQueue.main.async {
                self?.syncPopoverToFittingSize()
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
                self?.syncPopoverToFittingSize()
            }
        }
    }
    
    public func syncPopoverToFittingSize() {
        if let popover = popover {
            let targetSize = PopoverRouter.shared.currentScreen.preferredSize
            NSAnimationContext.runAnimationGroup { context in
                context.duration = 0.20
                context.allowsImplicitAnimation = true
                popover.contentSize = targetSize
            }
        }
    }
    
    public func updatePopoverSize(_ newSize: NSSize) {
        if let popover = popover {
            popover.contentSize = newSize
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
