import Foundation
import Network

@MainActor
public final class NetworkManager: ObservableObject {
    public static let shared = NetworkManager()
    
    @Published public var isConnected = false
    @Published public var connectedDeviceName: String = "Bağlı Cihaz Yok"
    @Published public var batteryState: BatteryPayload? = nil
    @Published public var mediaState: MediaSessionPayload? = nil
    
    private var listener: NWListener?
    private var activeConnection: NWConnection?
    private let port: NWEndpoint.Port = 8443
    
    public init() {}
    
    public func startServer() {
        do {
            let parameters = NWParameters.tcp
            let wsOptions = NWProtocolWebSocket.Options()
            wsOptions.autoReplyPing = true
            parameters.defaultProtocolStack.applicationProtocols.insert(wsOptions, at: 0)
            
            listener = try NWListener(using: parameters, on: port)
            
            // Bonjour mDNS Advertisement
            listener?.service = NWListener.Service(type: "_aetherlink._tcp")
            
            listener?.stateUpdateHandler = { state in
                Task { @MainActor in
                    switch state {
                    case .ready:
                        print("[NetworkManager] AetherLink mDNS & WebSocket listener ready on port \(self.port)")
                    case .failed(let error):
                        print("[NetworkManager] Listener failed: \(error)")
                    default:
                        break
                    }
                }
            }
            
            listener?.newConnectionHandler = { [weak self] connection in
                Task { @MainActor in
                    self?.handleNewConnection(connection)
                }
            }
            
            listener?.start(queue: .main)
        } catch {
            print("[NetworkManager] Could not initialize NWListener: \(error)")
        }
    }
    
    private func handleNewConnection(_ connection: NWConnection) {
        self.activeConnection = connection
        connection.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                switch state {
                case .ready:
                    self?.isConnected = true
                    self?.connectedDeviceName = "Galaxy S25 Ultra"
                    print("[NetworkManager] Android device connected successfully!")
                    self?.receiveNextMessage(from: connection)
                case .cancelled, .failed:
                    self?.isConnected = false
                    self?.connectedDeviceName = "Bağlantı Kesildi"
                    print("[NetworkManager] Connection dropped.")
                default:
                    break
                }
            }
        }
        connection.start(queue: .main)
    }
    
    private func receiveNextMessage(from connection: NWConnection) {
        connection.receiveMessage { [weak self] (data, context, isComplete, error) in
            Task { @MainActor in
                if let data = data, !data.isEmpty {
                    self?.parseIncomingData(data)
                }
                if error == nil {
                    self?.receiveNextMessage(from: connection)
                }
            }
        }
    }
    
    private func parseIncomingData(_ data: Data) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else { return }
        
        switch type {
        case "BATTERY_UPDATE":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let battery = try? JSONDecoder().decode(BatteryPayload.self, from: payloadData) {
                self.batteryState = battery
            }
            
        case "MEDIA_UPDATE":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let media = try? JSONDecoder().decode(MediaSessionPayload.self, from: payloadData) {
                self.mediaState = media
            }
            
        case "CLIPBOARD_SYNC":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let clip = try? JSONDecoder().decode(ClipboardPayload.self, from: payloadData) {
                ClipboardManager.shared.handleRemoteClipboard(clip)
            }
            
        case "NOTIFICATION_POSTED":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let notif = try? JSONDecoder().decode(NotificationPayload.self, from: payloadData) {
                NotificationManager.shared.displayNotification(notif)
            }
            
        case "CALL_INCOMING":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let call = try? JSONDecoder().decode(CallIncomingPayload.self, from: payloadData) {
                CallManager.shared.handleIncomingCall(call)
            }
            
        default:
            print("[NetworkManager] Unhandled message type: \(type)")
        }
    }
    
    public func send<T: Encodable>(type: String, payload: T) {
        guard let connection = activeConnection, isConnected else { return }
        let envelope: [String: Any] = [
            "type": type,
            "payload": (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(payload))) ?? [:]
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: envelope) else { return }
        
        let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
        let context = NWConnection.ContentContext(identifier: "wsText", metadata: [metadata])
        
        connection.send(content: data, contentContext: context, isComplete: true, completion: .contentProcessed({ error in
            if let error = error {
                print("[NetworkManager] Send error: \(error)")
            }
        }))
    }
}
