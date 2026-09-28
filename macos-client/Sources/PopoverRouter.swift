import AppKit
import SwiftUI

public enum ActiveScreen: String, CaseIterable, Equatable {
    case dashboard
    case pairing
    case deviceInfo
    case settings
    
    public static var telemetry: ActiveScreen { .deviceInfo }
    
    public static let defaultWidth: CGFloat = 365
    
    @MainActor
    public var preferredHeight: CGFloat {
        switch self {
        case .dashboard:
            return NetworkManager.shared.mediaState?.isPlaying == true ? 295 : 240
        case .pairing:
            return 305
        case .deviceInfo:
            return NetworkManager.shared.isConnected ? 350 : 210
        case .settings:
            return 250
        }
    }
    
    @MainActor
    public var preferredSize: NSSize {
        return NSSize(width: Self.defaultWidth, height: preferredHeight)
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
