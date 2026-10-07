import AppKit
import SwiftUI

public enum ActiveScreen: String, CaseIterable, Equatable {
    case dashboard
    case continuity
    case remote
    case settings
    case pairing
    case deviceInfo
    
    public static var telemetry: ActiveScreen { .deviceInfo }
    
    public static let defaultWidth: CGFloat = 380
    public static let defaultHeight: CGFloat = 460
    
    @MainActor
    public var preferredHeight: CGFloat {
        return Self.defaultHeight
    }
    
    @MainActor
    public var preferredSize: NSSize {
        return NSSize(width: Self.defaultWidth, height: Self.defaultHeight)
    }
}

public typealias PopoverPage = ActiveScreen

@MainActor
public final class PopoverRouter: ObservableObject {
    public static let shared = PopoverRouter()
    public static let defaultWidth: CGFloat = ActiveScreen.defaultWidth
    public static let defaultHeight: CGFloat = ActiveScreen.defaultHeight
    
    @Published public var currentScreen: ActiveScreen = .dashboard {
        didSet {
            let newSize = currentScreen.preferredSize
            onScreenChange?(newSize)
        }
    }
    
    public var onScreenChange: ((NSSize) -> Void)?
    
    public init() {}
    
    public func navigateTo(_ screen: ActiveScreen) {
        withAnimation(.easeInOut(duration: 0.20)) {
            currentScreen = screen
        }
    }
    
    public func popToDashboard() {
        withAnimation(.easeInOut(duration: 0.20)) {
            currentScreen = .dashboard
        }
    }
}
