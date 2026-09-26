import Foundation

public enum CallAppType: String, Codable, Sendable {
    case cellular
    case whatsapp
    case telegram
    case signal
    case slack
    case instagram
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

public struct GenericEnvelope: Codable, Sendable {
    public let type: String
}
