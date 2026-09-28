import Foundation

public enum CallAppType: String, Codable, Sendable {
    case cellular
    case whatsapp
    case telegram
    case signal
    case slack
    case instagram
}

public enum CallBannerPosition: String, CaseIterable, Identifiable, Codable, Sendable {
    case notch = "notch"
    case floating = "floating"
    case topRight = "topRight"
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .notch: return "Dinamik Çentik"
        case .floating: return "Çentik Altı Yüzen"
        case .topRight: return "Bildirim Köşesi"
        }
    }
    
    public var icon: String {
        switch self {
        case .notch: return "macbook"
        case .floating: return "capsule"
        case .topRight: return "bell.badge"
        }
    }
    
    public var description: String {
        switch self {
        case .notch: return "Çentikten aşağıya doğru açılır, içeriği çentiğin altında gösterir"
        case .floating: return "Çentiğin altında bağımsız yüzen sıvı cam ada"
        case .topRight: return "macOS bildirim alanından (sağ üst) açılır"
        }
    }
}

public struct PairingRequestPayload: Codable, Sendable {
    public let deviceId: String
    public let deviceName: String
    public let confirmationCode: String
    public let timestamp: Double
}

public struct PairingResponsePayload: Codable, Sendable {
    public let approved: Bool
    public let token: String?
    public let macName: String
    public let timestamp: Double
}

public struct MacHelloPayload: Codable, Sendable {
    public let macName: String
    public let timestamp: Double
}

public struct MacSleepPayload: Codable, Sendable {
    public let reason: String
    public let timestamp: Double
}

public struct MacWakePayload: Codable, Sendable {
    public let macName: String
    public let ip: String
    public let port: Int
    public let timestamp: Double
}

public struct HeartbeatPayload: Codable, Sendable {
    public let timestamp: Double
}


public struct CallIncomingPayload: Codable, Sendable {
    public let callId: String
    public let appType: CallAppType
    public let callerName: String
    public let contact_name: String?
    public let phoneNumber: String?
    public let avatarBase64: String?
    public let timestamp: Double
    public let hasVideo: Bool
    public let direction: String? // "incoming" or "outgoing"

    public var displayName: String {
        if let contact = contact_name, !contact.isEmpty, contact != "Bilinmeyen Numara", contact != "Numara Çevriliyor" {
            return contact
        }
        if !callerName.isEmpty && callerName != "Bilinmeyen Numara" && callerName != "Numara Çevriliyor" {
            return callerName
        }
        if let phone = phoneNumber, !phone.isEmpty, phone != "Bilinmeyen Numara", phone != "Numara Çevriliyor" {
            return phone
        }
        return (direction == "outgoing") ? "Giden Arama" : "Bilinmeyen Numara"
    }
}

public struct CallActionPayload: Codable, Sendable {
    public let callId: String
    public let action: String // answer, decline, mute, hold, hangup
    public let timestamp: Double
}

public struct NotificationPayload: Codable, Sendable {
    public let id: String
    public let key: String
    public let packageName: String
    public let appName: String
    public let title: String
    public let text: String
    public let subText: String?
    public let timestamp: Double
    public let canReply: Bool
    public let replyPlaceholder: String?
    public let appIconBase64: String?
}

public struct NotificationReplyPayload: Codable, Sendable {
    public let notificationKey: String
    public let replyText: String
    public let timestamp: Double
}

public struct NotificationDismissPayload: Codable, Sendable {
    public let notificationKey: String
    public let timestamp: Double
}

public struct ClipboardPayload: Codable, Sendable {
    public let contentType: String
    public let data: String
    public let sha256Hash: String
    public let timestamp: Double
    public let sourceDevice: String
}

public struct BatteryPayload: Codable, Sendable {
    public let batteryLevel: Int
    public let isCharging: Bool
    public let powerSaveMode: Bool
    public let temperatureCelsius: Double?
}

public struct MacBatteryPayload: Codable, Sendable {
    public let batteryLevel: Int
    public let isCharging: Bool
    public let isPluggedIn: Bool
    public let statusDescription: String
    public let timestamp: Double
}

public struct MacTelemetryPayload: Codable, Sendable {
    public let mac_temp: Double
    public let mac_battery: Int
    public let is_charging: Bool
    public let thermal_status: String
    public let timestamp: Double
    
    public init(mac_temp: Double, mac_battery: Int, is_charging: Bool, thermal_status: String, timestamp: Double = Date().timeIntervalSince1970 * 1000) {
        self.mac_temp = mac_temp
        self.mac_battery = mac_battery
        self.is_charging = is_charging
        self.thermal_status = thermal_status
        self.timestamp = timestamp
    }
}

public struct MediaSessionPayload: Codable, Sendable {
    public let packageName: String
    public let trackTitle: String
    public let artist: String
    public let album: String
    public let isPlaying: Bool
    public let positionMs: Double
    public let durationMs: Double
    public let artworkBase64: String?
}

public struct DeviceTelemetryPayload: Codable, Sendable {
    public let model: String
    public let manufacturer: String
    public let androidVersion: String
    public let sdkLevel: Int
    public let batteryLevel: Int
    public let isCharging: Bool
    public let batteryTempCelsius: Double?
    public let battery_temp: Double?
    public let thermalStatus: String?
    public let thermal_status: String?
    public let batteryHealth: String
    public let ramTotalMB: Int
    public let ramUsedMB: Int
    public let ramFreeMB: Int
    public let storageTotalGB: Double
    public let storageUsedGB: Double
    public let storageFreeGB: Double
    public let wifiSSID: String?
    public let wifiIp: String?
    public let wifiLinkSpeedMbps: Int
    public let cellularOperator: String?
    public let uptimeHours: Double
    public let timestamp: Double
    
    public var effectiveTemp: Double {
        return batteryTempCelsius ?? battery_temp ?? 28.0
    }
    
    public var effectiveThermalStatus: String {
        return thermalStatus ?? thermal_status ?? "NORMAL"
    }
}

public struct ScreenStreamControlPayload: Codable, Sendable {
    public let action: String // "start", "stop", "pause"
    public let quality: String // "high", "medium", "low"
    public let fps: Int
    public let timestamp: Double
}

public struct ScreenStreamFramePayload: Codable, Sendable {
    public let frameIndex: Int
    public let format: String // "jpeg"
    public let base64Data: String
    public let width: Int
    public let height: Int
    public let timestamp: Double
}

public struct DisconnectPayload: Codable, Sendable {
    public let reason: String
    public let source: String?
    public let shouldForget: Bool
    public let timestamp: Double
}

public struct GenericEnvelope: Codable, Sendable {
    public let type: String
}

// MARK: - KDE Connect Feature Extension Models

public struct FindMyDevicePayload: Codable, Sendable {
    public let action: String // "ring" or "stop"
    public let sourceDevice: String?
    public let isRinging: Bool?
    public let timestamp: Double
    
    public init(action: String, sourceDevice: String? = "macos", isRinging: Bool? = nil, timestamp: Double = Date().timeIntervalSince1970 * 1000) {
        self.action = action
        self.sourceDevice = sourceDevice
        self.isRinging = isRinging
        self.timestamp = timestamp
    }
}

public struct SystemVolumePayload: Codable, Sendable {
    public let volume: Int // 0 to 100 percentage
    public let isMuted: Bool?
    public let stream: String?
    public let timestamp: Double
    
    public init(volume: Int, isMuted: Bool? = false, stream: String? = "media", timestamp: Double = Date().timeIntervalSince1970 * 1000) {
        self.volume = volume
        self.isMuted = isMuted
        self.stream = stream
        self.timestamp = timestamp
    }
}

public struct ConnectivityReportPayload: Codable, Sendable {
    public let operatorName: String
    public let networkType: String // "5G", "LTE", "Wi-Fi"
    public let signalStrength: Int // 0 to 4 bars
    public let isRoaming: Bool
    public let timestamp: Double
    
    public var signalBarsText: String {
        return "\(signalStrength)/4 Diş"
    }
    
    public var formattedSummary: String {
        return "\(operatorName) • \(networkType) (\(signalBarsText))"
    }
}

public struct PingPayload: Codable, Sendable {
    public let message: String?
    public let clientTimestamp: Double
    public let serverTimestamp: Double?
    public let rttMs: Double?
    
    public init(message: String? = "Ping", clientTimestamp: Double = Date().timeIntervalSince1970 * 1000, serverTimestamp: Double? = nil, rttMs: Double? = nil) {
        self.message = message
        self.clientTimestamp = clientTimestamp
        self.serverTimestamp = serverTimestamp
        self.rttMs = rttMs
    }
}

public struct RemoteInputPayload: Codable, Sendable {
    public let action: String // "move", "click", "rightClick", "doubleClick", "scroll", "key"
    public let dx: Float?
    public let dy: Float?
    public let key: String?
    public let timestamp: Double
    
    public init(action: String, dx: Float? = nil, dy: Float? = nil, key: String? = nil, timestamp: Double = Date().timeIntervalSince1970 * 1000) {
        self.action = action
        self.dx = dx
        self.dy = dy
        self.key = key
        self.timestamp = timestamp
    }
}

