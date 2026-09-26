/**
 * AetherLink Core Protocol Schema & Message Validators
 * Peer-to-Peer Event contracts between macOS (Swift) & Android (Kotlin)
 */

export type CallAppType = 'cellular' | 'whatsapp' | 'telegram' | 'signal' | 'slack' | 'instagram';

export interface PairingRequestPayload {
  deviceId: string;
  deviceName: string;
  confirmationCode: string; // 6-digit PIN (e.g. "482 195")
  timestamp: number;
}

export interface PairingResponsePayload {
  approved: boolean;
  token?: string;
  macName: string;
  timestamp: number;
}

export interface CallIncomingPayload {
  callId: string;
  appType: CallAppType;
  callerName: string;
  phoneNumber?: string;
  avatarBase64?: string;
  timestamp: number;
  hasVideo: boolean;
}

export interface CallActionPayload {
  callId: string;
  action: 'answer' | 'decline' | 'mute' | 'hold' | 'hangup';
  timestamp: number;
}

export interface NotificationPayload {
  id: string;
  key: string;
  packageName: string;
  appName: string;
  title: string;
  text: string;
  subText?: string;
  timestamp: number;
  canReply: boolean;
  replyPlaceholder?: string;
  appIconBase64?: string;
}

export interface NotificationReplyPayload {
  notificationKey: string;
  replyText: string;
  timestamp: number;
}

export interface ClipboardPayload {
  contentType: 'text/plain' | 'text/html' | 'image/png';
  data: string; // Plain string or Base64 encoded media
  sha256Hash: string; // Used for loop prevention
  timestamp: number;
  sourceDevice: 'macos' | 'android';
}

export interface BatteryPayload {
  batteryLevel: number; // 0 - 100
  isCharging: boolean;
  powerSaveMode: boolean;
  temperatureCelsius?: number;
}

export interface MediaSessionPayload {
  packageName: string;
  trackTitle: string;
  artist: string;
  album: string;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  artworkBase64?: string;
}

export type AetherMessage = 
  | { type: 'PAIRING_REQUEST'; payload: PairingRequestPayload }
  | { type: 'PAIRING_RESPONSE'; payload: PairingResponsePayload }
  | { type: 'CALL_INCOMING'; payload: CallIncomingPayload }
  | { type: 'CALL_ACTION'; payload: CallActionPayload }
  | { type: 'NOTIFICATION_POSTED'; payload: NotificationPayload }
  | { type: 'NOTIFICATION_REPLY'; payload: NotificationReplyPayload }
  | { type: 'CLIPBOARD_SYNC'; payload: ClipboardPayload }
  | { type: 'BATTERY_UPDATE'; payload: BatteryPayload }
  | { type: 'MEDIA_UPDATE'; payload: MediaSessionPayload }
  | { type: 'HEARTBEAT_PING'; timestamp: number }
  | { type: 'HEARTBEAT_PONG'; timestamp: number };

export class ProtocolValidator {
  public static isValidMessage(msg: unknown): msg is AetherMessage {
    if (!msg || typeof msg !== 'object') return false;
    const m = msg as Record<string, unknown>;
    if (typeof m.type !== 'string') return false;

    switch (m.type) {
      case 'PAIRING_REQUEST': {
        const p = m.payload as Partial<PairingRequestPayload>;
        return !!p && typeof p.deviceId === 'string' && typeof p.confirmationCode === 'string';
      }
      case 'PAIRING_RESPONSE': {
        const p = m.payload as Partial<PairingResponsePayload>;
        return !!p && typeof p.approved === 'boolean';
      }
      case 'CALL_INCOMING': {
        const p = m.payload as Partial<CallIncomingPayload>;
        return !!p && typeof p.callId === 'string' && typeof p.callerName === 'string';
      }
      case 'CALL_ACTION': {
        const p = m.payload as Partial<CallActionPayload>;
        return !!p && typeof p.callId === 'string' && ['answer', 'decline', 'mute', 'hold', 'hangup'].includes(p.action as string);
      }
      case 'NOTIFICATION_POSTED': {
        const p = m.payload as Partial<NotificationPayload>;
        return !!p && typeof p.key === 'string' && typeof p.title === 'string';
      }
      case 'NOTIFICATION_REPLY': {
        const p = m.payload as Partial<NotificationReplyPayload>;
        return !!p && typeof p.notificationKey === 'string' && typeof p.replyText === 'string';
      }
      case 'CLIPBOARD_SYNC': {
        const p = m.payload as Partial<ClipboardPayload>;
        return !!p && typeof p.data === 'string' && typeof p.sha256Hash === 'string';
      }
      case 'BATTERY_UPDATE': {
        const p = m.payload as Partial<BatteryPayload>;
        return !!p && typeof p.batteryLevel === 'number' && typeof p.isCharging === 'boolean';
      }
      case 'MEDIA_UPDATE': {
        const p = m.payload as Partial<MediaSessionPayload>;
        return !!p && typeof p.trackTitle === 'string' && typeof p.isPlaying === 'boolean';
      }
      case 'HEARTBEAT_PING':
      case 'HEARTBEAT_PONG':
        return typeof m.timestamp === 'number';
      default:
        return false;
    }
  }
}
