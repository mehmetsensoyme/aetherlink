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
        for window in NSApp.windows {
            if window.isVisible {
                window.isOpaque = false
                window.backgroundColor = .clear
                window.contentView?.wantsLayer = true
                window.contentView?.layer?.backgroundColor = .clear
                
                let targetHeight: CGFloat
                if let fitting = window.contentView?.fittingSize.height, fitting > 100 {
                    targetHeight = ceil(fitting)
                } else {
                    targetHeight = PopoverRouter.shared.currentScreen.preferredHeight
                }
                let targetSize = NSSize(width: 365, height: targetHeight)
                
                let currentFrame = window.frame
                if abs(currentFrame.width - targetSize.width) > 1 || abs(currentFrame.height - targetSize.height) > 1 {
                    let deltaHeight = targetSize.height - currentFrame.height
                    let newOrigin = NSPoint(x: currentFrame.origin.x, y: currentFrame.origin.y - deltaHeight)
                    let newFrame = NSRect(origin: newOrigin, size: targetSize)
                    
                    NSAnimationContext.runAnimationGroup { context in
                        context.duration = 0.20
                        context.allowsImplicitAnimation = true
                        window.setFrame(newFrame, display: true, animate: true)
                    }
                }
            }
        }
        
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
        syncPopoverToFittingSize()
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
