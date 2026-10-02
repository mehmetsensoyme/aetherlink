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
        let preferred = PopoverRouter.shared.currentScreen.preferredHeight
        var calculatedTargetHeight = preferred
        
        for window in NSApp.windows {
            if window.isVisible {
                window.isOpaque = false
                window.backgroundColor = .clear
                window.contentView?.wantsLayer = true
                window.contentView?.layer?.backgroundColor = .clear
                
                let fittingHeight = window.contentView?.fittingSize.height ?? 0
                let targetHeight: CGFloat
                if fittingHeight > 100 {
                    targetHeight = max(ceil(fittingHeight), preferred)
                } else {
                    targetHeight = preferred
                }
                calculatedTargetHeight = targetHeight
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
            let finalSize = NSSize(width: 365, height: calculatedTargetHeight)
            NSAnimationContext.runAnimationGroup { context in
                context.duration = 0.20
                context.allowsImplicitAnimation = true
                popover.contentSize = finalSize
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
