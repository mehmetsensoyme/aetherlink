import Foundation
#if canImport(WidgetKit)
import WidgetKit
#endif

public struct AetherWidgetPayload: Codable, Sendable {
    public let level: Int
    public let isCharging: Bool
    public let deviceName: String
    public let isConnected: Bool
    public let lastUpdated: Double
    public let powerSave: Bool
    public let temperature: Double?

    public init(
        level: Int,
        isCharging: Bool,
        deviceName: String,
        isConnected: Bool,
        lastUpdated: Double,
        powerSave: Bool = false,
        temperature: Double? = nil
    ) {
        self.level = level
        self.isCharging = isCharging
        self.deviceName = deviceName
        self.isConnected = isConnected
        self.lastUpdated = lastUpdated
        self.powerSave = powerSave
        self.temperature = temperature
    }
}

public final class AetherWidgetDataManager: @unchecked Sendable {
    public static let shared = AetherWidgetDataManager()
    public static let appGroupID = "group.org.aetherlink"
    
    public static let keyBatteryLevel = "aether_battery_level"
    public static let keyIsCharging = "aether_is_charging"
    public static let keyIsConnected = "aether_is_connected"
    public static let keyDeviceName = "aether_device_name"
    public static let keyLastUpdated = "aether_last_updated"
    public static let keyPowerSaveMode = "aether_power_save"
    public static let keyTemperature = "aether_temperature"

    private let userDefaults: UserDefaults?

    private var sharedFileURL: URL {
        // In sandboxed widget: ~/Library/Containers/org.aetherlink.mac.widget/Data/Documents/battery.json
        // In host app: direct path to the widget's container documents folder
        let home = FileManager.default.homeDirectoryForCurrentUser
        let widgetDocs = home.appendingPathComponent("Library/Containers/org.aetherlink.mac.widget/Data/Documents")
        try? FileManager.default.createDirectory(at: widgetDocs, withIntermediateDirectories: true)
        return widgetDocs.appendingPathComponent("battery.json")
    }

    public init() {
        self.userDefaults = UserDefaults(suiteName: AetherWidgetDataManager.appGroupID) ?? UserDefaults.standard
    }

    public func updateBattery(
        level: Int,
        isCharging: Bool,
        deviceName: String,
        isConnected: Bool,
        powerSave: Bool = false,
        temp: Double? = nil
    ) {
        let now = Date().timeIntervalSince1970
        let payload = AetherWidgetPayload(
            level: level,
            isCharging: isCharging,
            deviceName: deviceName,
            isConnected: isConnected,
            lastUpdated: now,
            powerSave: powerSave,
            temperature: temp
        )

        // Write directly to file
        if let data = try? JSONEncoder().encode(payload) {
            try? data.write(to: sharedFileURL, options: .atomic)
        }

        // Also update userDefaults if available
        if let defaults = userDefaults {
            defaults.set(level, forKey: Self.keyBatteryLevel)
            defaults.set(isCharging, forKey: Self.keyIsCharging)
            defaults.set(deviceName, forKey: Self.keyDeviceName)
            defaults.set(isConnected, forKey: Self.keyIsConnected)
            defaults.set(powerSave, forKey: Self.keyPowerSaveMode)
            if let temp = temp {
                defaults.set(temp, forKey: Self.keyTemperature)
            }
            defaults.set(now, forKey: Self.keyLastUpdated)
            defaults.synchronize()
        }

        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    public func clear() {
        let now = Date().timeIntervalSince1970
        let payload = AetherWidgetPayload(
            level: 0,
            isCharging: false,
            deviceName: "Android Cihaz",
            isConnected: false,
            lastUpdated: now,
            powerSave: false,
            temperature: nil
        )
        if let data = try? JSONEncoder().encode(payload) {
            try? data.write(to: sharedFileURL, options: .atomic)
        }

        if let defaults = userDefaults {
            defaults.set(false, forKey: Self.keyIsConnected)
            defaults.set(now, forKey: Self.keyLastUpdated)
            defaults.synchronize()
        }

        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    public func loadData() -> (level: Int, isCharging: Bool, deviceName: String, isConnected: Bool, lastUpdated: Date) {
        // Try file first
        if let data = try? Data(contentsOf: sharedFileURL),
           let payload = try? JSONDecoder().decode(AetherWidgetPayload.self, from: data) {
            let date = payload.lastUpdated > 0 ? Date(timeIntervalSince1970: payload.lastUpdated) : Date()
            return (payload.level, payload.isCharging, payload.deviceName, payload.isConnected, date)
        }

        // Fallback to UserDefaults
        guard let defaults = userDefaults else {
            return (100, false, "Android Cihaz", false, Date())
        }
        let level = defaults.integer(forKey: Self.keyBatteryLevel)
        let isCharging = defaults.bool(forKey: Self.keyIsCharging)
        let deviceName = defaults.string(forKey: Self.keyDeviceName) ?? "Android Cihaz"
        let isConnected = defaults.bool(forKey: Self.keyIsConnected)
        let timestamp = defaults.double(forKey: Self.keyLastUpdated)
        let date = timestamp > 0 ? Date(timeIntervalSince1970: timestamp) : Date()
        return (level, isCharging, deviceName, isConnected, date)
    }
}
