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
        }
    }
    
    public func syncPopoverToFittingSize() {
        if let popover = popover, let hostingController = popover.contentViewController {
            let fittingHeight = hostingController.view.fittingSize.height
            let newSize = NSSize(width: 365, height: max(ceil(fittingHeight), 220))
            NSAnimationContext.runAnimationGroup { context in
                context.duration = 0.20
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
                
                if let contentView = window.contentView {
                    let fittingHeight = contentView.fittingSize.height
                    if fittingHeight > 0 {
                        let targetSize = NSSize(width: 365, height: max(ceil(fittingHeight), 220))
                        if abs(window.frame.width - targetSize.width) > 1 || abs(window.frame.height - targetSize.height) > 1 {
                            NSAnimationContext.runAnimationGroup { context in
                                context.duration = 0.20
                                context.allowsImplicitAnimation = true
                                window.setContentSize(targetSize)
                            }
                        }
                    }
                }
            }
        }
    }
    
    public func updatePopoverSize(_ newSize: NSSize) {
        syncPopoverToFittingSize()
    }
    
    public func configurePopover(_ popover: NSPopover) {
        self.popover = popover
        popover.behavior = .transient
        popover.animates = true
        
        if let hostingController = popover.contentViewController {
            let fittingHeight = hostingController.view.fittingSize.height
            popover.contentSize = NSSize(width: 365, height: max(ceil(fittingHeight), 220))
        } else {
            popover.contentSize = NSSize(width: 365, height: 220)
        }
        
        if let window = popover.contentViewController?.view.window {
            window.isOpaque = false
            window.backgroundColor = .clear
            window.contentView?.wantsLayer = true
            window.contentView?.layer?.backgroundColor = .clear
        }
    }
}
