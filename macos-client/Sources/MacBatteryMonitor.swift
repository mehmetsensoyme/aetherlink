import Foundation
import Combine
import IOKit.ps

@MainActor
public final class MacBatteryMonitor: ObservableObject {
    public static let shared = MacBatteryMonitor()
    
    @Published public var currentLevel: Int = 100
    @Published public var isCharging: Bool = false
    @Published public var isPluggedIn: Bool = false
    @Published public var statusDescription: String = "Pilde"
    
    private var timer: Timer?
    
    public init() {
        updateBatteryState()
    }
    
    public func startMonitoring() {
        updateBatteryState()
        timer?.invalidate()
        // Poll every 3 seconds for instant power plug/unplug reactivity
        timer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { [weak self] _ in
            guard let self else { return }
            Task { @MainActor in
                self.updateBatteryState()
                self.broadcastBatteryState()
            }
        }
    }
    
    public func updateBatteryState() {
        guard let snapshot = IOPSCopyPowerSourcesInfo()?.takeRetainedValue(),
              let sources = IOPSCopyPowerSourcesList(snapshot)?.takeRetainedValue() as? [CFTypeRef] else {
            return
        }
        
        for ps in sources {
            guard let desc = IOPSGetPowerSourceDescription(snapshot, ps)?.takeUnretainedValue() as? [String: Any] else {
                continue
            }
            
            let cur = desc[kIOPSCurrentCapacityKey as String] as? Int ?? 100
            let max = desc[kIOPSMaxCapacityKey as String] as? Int ?? 100
            let charging = desc[kIOPSIsChargingKey as String] as? Bool ?? false
            let pState = desc[kIOPSPowerSourceStateKey as String] as? String ?? ""
            let isPlugged = (pState == (kIOPSACPowerValue as String))
            let level = max > 0 ? Int((Double(cur) / Double(max)) * 100) : cur
            
            self.currentLevel = level
            self.isCharging = charging
            self.isPluggedIn = isPlugged
            
            if charging {
                self.statusDescription = "Şarj Oluyor (%\(level))"
            } else if isPlugged {
                self.statusDescription = "Prize Takılı (%\(level))"
            } else {
                self.statusDescription = "Pilde (%\(level))"
            }
            return
        }
    }
    
    public func broadcastBatteryState() {
        let payload = MacBatteryPayload(
            batteryLevel: currentLevel,
            isCharging: isCharging,
            isPluggedIn: isPluggedIn,
            statusDescription: statusDescription,
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        
        NetworkManager.shared.send(type: "MAC_BATTERY_UPDATE", payload: payload)
        print("[MacBatteryMonitor] Broadcasted real IOKit Mac battery: \(currentLevel)%, Charging: \(isCharging), Plugged: \(isPluggedIn)")
    }
}
