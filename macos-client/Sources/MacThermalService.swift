import Foundation
import Combine
import IOKit

@MainActor
public final class MacThermalService: ObservableObject {
    public static let shared = MacThermalService()
    
    @Published public var currentTemperature: Double = 41.0
    @Published public var currentThermalStatus: String = "NORMAL"
    @Published public var sensorCount: Int = 0
    @Published public var lastUpdated: Date = Date()
    
    private var timer: Timer?
    
    // Dynamic function pointer types for IOHIDEventSystem private symbols
    private typealias IOHIDEventSystemClientCreateType = @convention(c) (CFAllocator?) -> Unmanaged<AnyObject>?
    private typealias IOHIDEventSystemClientSetMatchingType = @convention(c) (AnyObject, CFDictionary) -> Void
    private typealias IOHIDEventSystemClientCopyServicesType = @convention(c) (AnyObject) -> Unmanaged<CFArray>?
    private typealias IOHIDServiceClientCopyEventType = @convention(c) (AnyObject, Int64, Int32, Int64) -> Unmanaged<AnyObject>?
    private typealias IOHIDEventGetFloatValueType = @convention(c) (AnyObject, Int32) -> Double
    
    private var clientCreate: IOHIDEventSystemClientCreateType?
    private var setMatching: IOHIDEventSystemClientSetMatchingType?
    private var copyServices: IOHIDEventSystemClientCopyServicesType?
    private var copyEvent: IOHIDServiceClientCopyEventType?
    private var getFloat: IOHIDEventGetFloatValueType?
    private var hidClient: AnyObject?
    
    public init() {
        setupHIDSymbols()
        _ = readHardwareTemperature(forceFresh: true)
    }
    
    private func setupHIDSymbols() {
        let handle = dlopen(nil, RTLD_NOW)
        if let symClientCreate = dlsym(handle, "IOHIDEventSystemClientCreate"),
           let symSetMatching = dlsym(handle, "IOHIDEventSystemClientSetMatching"),
           let symCopyServices = dlsym(handle, "IOHIDEventSystemClientCopyServices"),
           let symCopyEvent = dlsym(handle, "IOHIDServiceClientCopyEvent"),
           let symGetFloat = dlsym(handle, "IOHIDEventGetFloatValue") {
            
            self.clientCreate = unsafeBitCast(symClientCreate, to: IOHIDEventSystemClientCreateType.self)
            self.setMatching = unsafeBitCast(symSetMatching, to: IOHIDEventSystemClientSetMatchingType.self)
            self.copyServices = unsafeBitCast(symCopyServices, to: IOHIDEventSystemClientCopyServicesType.self)
            self.copyEvent = unsafeBitCast(symCopyEvent, to: IOHIDServiceClientCopyEventType.self)
            self.getFloat = unsafeBitCast(symGetFloat, to: IOHIDEventGetFloatValueType.self)
            
            if let client = self.clientCreate?(kCFAllocatorDefault)?.takeRetainedValue() {
                self.hidClient = client
                let matching: [String: Any] = [
                    "PrimaryUsagePage": 0xff00,
                    "PrimaryUsage": 5
                ]
                self.setMatching?(client, matching as CFDictionary)
            }
        }
    }
    
    public func startMonitoring() {
        _ = readHardwareTemperature(forceFresh: true)
        timer?.invalidate()
        // Poll every 5 seconds for thermal sync
        timer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            Task { @MainActor in
                _ = self.readHardwareTemperature(forceFresh: true)
                if NetworkManager.shared.isConnected {
                    self.broadcastTelemetry()
                }
            }
        }
    }
    
    @discardableResult
    public func readHardwareTemperature(forceFresh: Bool = false) -> Double {
        var temps: [Double] = []
        
        if let client = self.hidClient,
           let copyServices = self.copyServices,
           let copyEvent = self.copyEvent,
           let getFloat = self.getFloat {
            
            if let services = copyServices(client)?.takeRetainedValue() as? [AnyObject] {
                for service in services {
                    // IOHIDEventType 15 is temperature event: (15 << 16)
                    if let event = copyEvent(service, 15, 0, 0)?.takeRetainedValue() {
                        let val = getFloat(event, 15 << 16)
                        if val > 15.0 && val < 115.0 {
                            temps.append(val)
                        }
                    }
                }
            }
        }
        
        let temp: Double
        if !temps.isEmpty {
            self.sensorCount = temps.count
            // Average of all hardware thermal sensors
            temp = temps.reduce(0, +) / Double(temps.count)
        } else {
            // Fallback: estimate based on ProcessInfo.processInfo.thermalState
            switch ProcessInfo.processInfo.thermalState {
            case .nominal:
                temp = 41.5
            case .fair:
                temp = 54.0
            case .serious:
                temp = 72.0
            case .critical:
                temp = 85.0
            @unknown default:
                temp = 41.5
            }
        }
        
        let roundedTemp = (temp * 10.0).rounded() / 10.0
        self.currentTemperature = roundedTemp
        
        // Compute Thermal Status
        if roundedTemp < 50.0 {
            self.currentThermalStatus = "NORMAL"
        } else if roundedTemp < 75.0 {
            self.currentThermalStatus = "MODERATE"
        } else {
            self.currentThermalStatus = "SEVERE"
        }
        
        self.lastUpdated = Date()
        return roundedTemp
    }
    
    public func broadcastTelemetry() {
        let battLevel = MacBatteryMonitor.shared.currentLevel
        let isCharging = MacBatteryMonitor.shared.isCharging
        
        let payload = MacTelemetryPayload(
            mac_temp: currentTemperature,
            mac_battery: battLevel,
            is_charging: isCharging,
            thermal_status: currentThermalStatus,
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        
        NetworkManager.shared.send(type: "MAC_TELEMETRY", payload: payload)
        print("[MacThermalService] Broadcasted Mac telemetry: \(currentTemperature)°C, status: \(currentThermalStatus), battery: \(battLevel)%")
    }
}
