import Foundation
#if canImport(WidgetKit)
import WidgetKit
#endif

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
        guard let defaults = userDefaults else { return }
        defaults.set(level, forKey: Self.keyBatteryLevel)
        defaults.set(isCharging, forKey: Self.keyIsCharging)
        defaults.set(deviceName, forKey: Self.keyDeviceName)
        defaults.set(isConnected, forKey: Self.keyIsConnected)
        defaults.set(powerSave, forKey: Self.keyPowerSaveMode)
        if let temp = temp {
            defaults.set(temp, forKey: Self.keyTemperature)
        }
        defaults.set(Date().timeIntervalSince1970, forKey: Self.keyLastUpdated)
        defaults.synchronize()

        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    public func clear() {
        guard let defaults = userDefaults else { return }
        defaults.set(false, forKey: Self.keyIsConnected)
        defaults.set(Date().timeIntervalSince1970, forKey: Self.keyLastUpdated)
        defaults.synchronize()

        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    public func loadData() -> (level: Int, isCharging: Bool, deviceName: String, isConnected: Bool, lastUpdated: Date) {
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
