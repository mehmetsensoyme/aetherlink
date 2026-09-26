import Foundation

public enum CallAppType: String, Codable, Sendable {
    case cellular
    case whatsapp
    case telegram
    case signal
    case slack
    case instagram
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

public struct CallIncomingPayload: Codable, Sendable {
    public let callId: String
    public let appType: CallAppType
    public let callerName: String
    public let phoneNumber: String?
    public let avatarBase64: String?
    public let timestamp: Double
    public let hasVideo: Bool
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
    public let batteryTempCelsius: Double
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
    public let shouldForget: Bool
    public let timestamp: Double
}

public struct GenericEnvelope: Codable, Sendable {
    public let type: String
}

