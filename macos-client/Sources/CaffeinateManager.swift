import AppKit
import Foundation
import IOKit.pwr_mgt

@MainActor
public final class CaffeinateManager: ObservableObject {
    public static let shared = CaffeinateManager()
    
    @Published public var isCaffeinateActive: Bool = false
    private var assertionID: IOPMAssertionID = 0
    
    public init() {}
    
    public func toggleCaffeinate() {
        if isCaffeinateActive {
            deactivate()
        } else {
            activate()
        }
    }
    
    public func activate() {
        guard !isCaffeinateActive else { return }
        let reason = "AetherLink Caffeinate: Keeping display and system awake" as CFString
        let success = IOPMAssertionCreateWithName(
            kIOPMAssertionTypePreventUserIdleDisplaySleep as CFString,
            IOPMAssertionLevel(kIOPMAssertionLevelOn),
            reason,
            &assertionID
        )
        if success == kIOReturnSuccess {
            isCaffeinateActive = true
            print("[CaffeinateManager] Caffeinate active: Screen and system sleep inhibited.")
            broadcastStatus()
        } else {
            print("[CaffeinateManager] Failed to create power assertion: \(success)")
        }
    }
    
    public func deactivate() {
        guard isCaffeinateActive else { return }
        if assertionID != 0 {
            IOPMAssertionRelease(assertionID)
            assertionID = 0
        }
        isCaffeinateActive = false
        print("[CaffeinateManager] Caffeinate deactivated.")
        broadcastStatus()
    }
    
    public func setCaffeinate(active: Bool) {
        if active {
            activate()
        } else {
            deactivate()
        }
    }
    
    public func broadcastStatus() {
        struct CaffeinateStatusPayload: Codable {
            let isActive: Bool
            let timestamp: Double
        }
        NetworkManager.shared.send(
            type: "CAFFEINATE_STATUS",
            payload: CaffeinateStatusPayload(
                isActive: isCaffeinateActive,
                timestamp: Date().timeIntervalSince1970 * 1000
            )
        )
    }
}
