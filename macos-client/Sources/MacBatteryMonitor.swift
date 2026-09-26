import Foundation
import Combine

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
        timer = Timer.scheduledTimer(withTimeInterval: 10.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.updateBatteryState()
                self?.broadcastBatteryState()
            }
        }
    }
    
    public func updateBatteryState() {
        let task = Process()
        task.launchPath = "/usr/bin/pmset"
        task.arguments = ["-g", "batt"]
        let pipe = Pipe()
        task.standardOutput = pipe
        
        do {
            try task.run()
            task.waitUntilExit()
            
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            let out = String(data: data, encoding: .utf8) ?? ""
            
            var level = 100
            let isPlugged = out.contains("AC Power")
            let isDischarging = out.contains("discharging")
            let isCharging = (out.contains("charging") && !isDischarging) || (isPlugged && !out.contains("discharging"))
            
            if let match = out.range(of: "\\d+%", options: .regularExpression) {
                let numStr = out[match].replacingOccurrences(of: "%", with: "")
                level = Int(numStr) ?? 100
            }
            
            self.currentLevel = level
            self.isCharging = isCharging
            self.isPluggedIn = isPlugged
            
            if isCharging {
                self.statusDescription = "Şarj Oluyor (%\(level))"
            } else if isPlugged {
                self.statusDescription = "Prize Takılı (%\(level))"
            } else {
                self.statusDescription = "Pilde (%\(level))"
            }
        } catch {
            print("[MacBatteryMonitor] Error querying battery: \(error)")
        }
    }
    
    public func broadcastBatteryState() {
        guard NetworkManager.shared.isConnected else { return }
        
        let payload = MacBatteryPayload(
            batteryLevel: currentLevel,
            isCharging: isCharging,
            isPluggedIn: isPluggedIn,
            statusDescription: statusDescription,
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        
        NetworkManager.shared.send(type: "MAC_BATTERY_UPDATE", payload: payload)
        print("[MacBatteryMonitor] Broadcasted Mac battery to Android: \(currentLevel)%, Charging: \(isCharging)")
    }
}
