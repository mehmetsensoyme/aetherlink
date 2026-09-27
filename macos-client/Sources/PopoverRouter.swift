import AppKit
import SwiftUI

public enum ActiveScreen: String, CaseIterable, Equatable {
    case dashboard
    case pairing
    case deviceInfo
    case settings
    
    public static var telemetry: ActiveScreen { .deviceInfo }
    
    public var preferredSize: NSSize {
        switch self {
        case .dashboard:
            return NSSize(width: 340, height: 350)
        case .pairing:
            return NSSize(width: 340, height: 380)
        case .deviceInfo:
            return NSSize(width: 340, height: 430)
        case .settings:
            return NSSize(width: 340, height: 340)
        }
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
