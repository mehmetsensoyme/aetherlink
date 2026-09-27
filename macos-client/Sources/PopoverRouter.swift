import AppKit
import SwiftUI

public enum ActiveScreen: String, CaseIterable, Equatable {
    case dashboard
    case pairing
    case deviceInfo
    case settings
    
    public static var telemetry: ActiveScreen { .deviceInfo }
    
    public static let defaultWidth: CGFloat = 365
    public static let minHeight: CGFloat = 220
    
    public var preferredSize: NSSize {
        return NSSize(width: Self.defaultWidth, height: Self.minHeight)
    }
}

public typealias PopoverPage = ActiveScreen

@MainActor
public final class PopoverRouter: ObservableObject {
    public static let shared = PopoverRouter()
    
    @Published public var currentScreen: ActiveScreen = .dashboard {
        didSet {
            let newSize = currentScreen.preferredSize
            onScreenChange?(newSize)
        }
    }
    
    public var onScreenChange: ((NSSize) -> Void)?
    
    public init() {}
    
    public func navigateTo(_ screen: ActiveScreen) {
        withAnimation(.easeInOut(duration: 0.22)) {
            currentScreen = screen
        }
    }
    
    public func popToDashboard() {
        withAnimation(.easeInOut(duration: 0.22)) {
            currentScreen = .dashboard
        }
    }
}
