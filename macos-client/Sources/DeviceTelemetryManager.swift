import Foundation
import Combine

@MainActor
public final class DeviceTelemetryManager: ObservableObject {
    public static let shared = DeviceTelemetryManager()
    
    @Published public var telemetry: DeviceTelemetryPayload? = nil
    @Published public var connectivity: ConnectivityReportPayload? = nil
    @Published public var lastPingMs: Double? = nil
    @Published public var isPinging: Bool = false
    @Published public var isShowingDetailSheet: Bool = false
    
    public init() {}
    
    public func handleIncomingTelemetry(_ payload: DeviceTelemetryPayload) {
        self.telemetry = payload
        print("[DeviceTelemetryManager] Received device specs: \(payload.manufacturer) \(payload.model), RAM: \(payload.ramUsedMB)/\(payload.ramTotalMB)MB, Battery: \(payload.batteryLevel)%, Temp: \(payload.effectiveTemp)°C")
    }
    
    public func handleConnectivityReport(_ payload: ConnectivityReportPayload) {
        self.connectivity = payload
        print("[DeviceTelemetryManager] Connectivity Report: \(payload.formattedSummary)")
    }
    
    public func sendPing() {
        guard !isPinging else { return }
        self.isPinging = true
        let ping = PingPayload(message: "Ping from Mac", clientTimestamp: Date().timeIntervalSince1970 * 1000)
        NetworkManager.shared.send(type: "PING", payload: ping)
        print("[DeviceTelemetryManager] Sent PING to Android")
    }
    
    public func handlePong(_ payload: PingPayload) {
        self.isPinging = false
        let now = Date().timeIntervalSince1970 * 1000
        let rtt = max(1.0, now - payload.clientTimestamp)
        self.lastPingMs = rtt
        print("[DeviceTelemetryManager] Received PONG! Round-trip latency: \(String(format: "%.1f", rtt)) ms")
    }
    
    public func handleIncomingPing(_ payload: PingPayload) {
        let pong = PingPayload(
            message: "PONG from Mac",
            clientTimestamp: payload.clientTimestamp,
            serverTimestamp: Date().timeIntervalSince1970 * 1000,
            rttMs: nil
        )
        NetworkManager.shared.send(type: "PONG", payload: pong)
    }
    
    public func requestTelemetryRefresh() {
        NetworkManager.shared.send(type: "DEVICE_TELEMETRY_REQUEST", payload: ["action": "refresh"])
        MacThermalService.shared.readHardwareTemperature(forceFresh: true)
        MacThermalService.shared.broadcastTelemetry()
    }
    
    public var formattedRam: String {
        guard let t = telemetry else { return "--" }
        let usedMB = t.ramUsedMB
        let totalMB = max(t.ramTotalMB, 1)
        let usedGB = Double(usedMB) / 1024.0
        let totalGB = Double(totalMB) / 1024.0
        let pct = Int((Double(usedMB) / Double(totalMB)) * 100)
        return String(format: "%.1f GB / %.0f GB (%%%d)", usedGB, totalGB, pct)
    }
    
    public var formattedStorage: String {
        guard let t = telemetry else { return "--" }
        let freeGB = t.storageFreeGB
        let totalGB = t.storageTotalGB
        return String(format: "%.1f GB Boş / %.0f GB", freeGB, totalGB)
    }
    
    public var formattedUptime: String {
        guard let t = telemetry else { return "--" }
        let uptime = t.uptimeHours
        let hours = Int(uptime)
        let mins = Int((uptime - Double(hours)) * 60)
        return "\(hours) sa \(mins) dk"
    }
    
    public var formattedNetwork: String {
        guard let t = telemetry else { return "--" }
        if let ssid = t.wifiSSID, !ssid.isEmpty, ssid != "<unknown ssid>" {
            return "\(ssid) (\(t.wifiLinkSpeedMbps) Mbps)"
        }
        return t.wifiIp ?? "Yerel Ağ"
    }
}
