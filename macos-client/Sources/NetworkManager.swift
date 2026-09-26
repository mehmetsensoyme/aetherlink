import Foundation
import Network

@MainActor
public final class NetworkManager: ObservableObject {
    public static let shared = NetworkManager()
    
    @Published public var isConnected = false
    @Published public var connectedDeviceName: String = "Bağlı Cihaz Yok"
    @Published public var batteryState: BatteryPayload? = nil
    @Published public var mediaState: MediaSessionPayload? = nil
    
    public var localIPAddress: String {
        var address: String = "127.0.0.1"
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0 else { return address }
        guard let firstAddr = ifaddr else { return address }
        
        for ptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let interface = ptr.pointee
            let addrFamily = interface.ifa_addr.pointee.sa_family
            if addrFamily == UInt8(AF_INET) {
                let name = String(cString: interface.ifa_name)
                if name == "en0" || name == "en1" {
                    var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                                &hostname, socklen_t(hostname.count),
                                nil, socklen_t(0), NI_NUMERICHOST)
                    address = String(cString: hostname)
                    break
                }
            }
        }
        freeifaddrs(ifaddr)
        return address
    }
    
    private var listener: NWListener?
    private var activeConnections: [NWConnection] = []
    private var connectionBuffers: [ObjectIdentifier: Data] = [:]
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
                        print("[NetworkManager] AetherLink mDNS & WebSocket listener ready on port \(self.port) (IP: \(self.localIPAddress))")
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
            
            // Start UDP Discovery Responder as well
            UDPDiscoveryResponder.shared.start()
        } catch {
            print("[NetworkManager] Could not initialize NWListener: \(error)")
        }
    }
    
    private func handleNewConnection(_ connection: NWConnection) {
        self.activeConnections.append(connection)
        connection.stateUpdateHandler = { [weak self, weak connection] state in
            Task { @MainActor in
                guard let self = self, let connection = connection else { return }
                switch state {
                case .ready:
                    self.isConnected = true
                    self.connectedDeviceName = "Galaxy S25 Ultra"
                    print("[NetworkManager] Android client connected! (Active: \(self.activeConnections.count))")
                    MacBatteryMonitor.shared.broadcastBatteryState()
                    self.receiveNextMessage(from: connection)
                case .cancelled, .failed:
                    self.connectionBuffers.removeValue(forKey: ObjectIdentifier(connection))
                    self.activeConnections.removeAll { $0 === connection }
                    if self.activeConnections.isEmpty {
                        self.isConnected = false
                        self.connectedDeviceName = "Bağlantı Kesildi"
                    }
                    print("[NetworkManager] Client disconnected (Remaining: \(self.activeConnections.count))")
                default:
                    break
                }
            }
        }
        connection.start(queue: .main)
    }
    
    private func receiveNextMessage(from connection: NWConnection) {
        connection.receiveMessage { [weak self, weak connection] (data, context, isComplete, error) in
            Task { @MainActor in
                guard let self = self, let connection = connection else { return }
                let id = ObjectIdentifier(connection)
                if let data = data, !data.isEmpty {
                    var current = self.connectionBuffers[id] ?? Data()
                    current.append(data)
                    if isComplete {
                        self.connectionBuffers.removeValue(forKey: id)
                        self.parseIncomingData(current)
                    } else {
                        self.connectionBuffers[id] = current
                    }
                }
                if error == nil {
                    self.receiveNextMessage(from: connection)
                } else {
                    self.connectionBuffers.removeValue(forKey: id)
                }
            }
        }
    }
    
    private func parseIncomingData(_ data: Data) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else {
            print("[NetworkManager] Failed to parse JSON or missing type")
            return
        }
        print("[NetworkManager] Received message type: \(type)")
        
        switch type {
        case "PAIRING_REQUEST":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let request = try? JSONDecoder().decode(PairingRequestPayload.self, from: payloadData) {
                PairingManager.shared.handleIncomingPairingRequest(request)
            }
            
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
            
        case "CALL_ACTION":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let action = try? JSONDecoder().decode(CallActionPayload.self, from: payloadData) {
                if action.action == "hangup" || action.action == "decline" {
                    CallManager.shared.dismissCallBanner()
                }
            }
            
        case "DEVICE_TELEMETRY":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let tele = try? JSONDecoder().decode(DeviceTelemetryPayload.self, from: payloadData) {
                DeviceTelemetryManager.shared.handleIncomingTelemetry(tele)
            }
            
        case "SCREEN_STREAM_FRAME":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let frame = try? JSONDecoder().decode(ScreenStreamFramePayload.self, from: payloadData) {
                ScreenMirrorManager.shared.handleIncomingFrame(frame)
            }
            
        case "DISCONNECT":
            self.isConnected = false
            self.connectedDeviceName = "Bağlantı Kesildi"
            self.batteryState = nil
            self.mediaState = nil
            ScreenMirrorManager.shared.stopStreamRequest()
            print("[NetworkManager] Device disconnected from remote side.")
            
        case "MAC_BATTERY_REQUEST":
            MacBatteryMonitor.shared.broadcastBatteryState()
            
        case "HEARTBEAT_PING":
            self.send(type: "HEARTBEAT_PONG", payload: ["timestamp": Date().timeIntervalSince1970 * 1000])
            
        default:
            print("[NetworkManager] Unhandled message type: \(type)")
        }
    }
    
    public func disconnectDevice(forget: Bool = false) {
        if forget {
            PairingManager.shared.unpair()
        }
        let payload = DisconnectPayload(
            reason: forget ? "unpair" : "user_requested",
            shouldForget: forget,
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        self.send(type: "DISCONNECT", payload: payload)
        for conn in self.activeConnections {
            conn.cancel()
        }
        self.activeConnections.removeAll()
        self.isConnected = false
        self.connectedDeviceName = "Bağlantı Kesildi"
        self.batteryState = nil
        self.mediaState = nil
        ScreenMirrorManager.shared.stopStreamRequest()
        print("[NetworkManager] Disconnected active device (forget: \(forget))")
    }
    
    public func send<T: Encodable>(type: String, payload: T) {
        guard !activeConnections.isEmpty else { return }
        let envelope: [String: Any] = [
            "type": type,
            "payload": (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(payload))) ?? [:]
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: envelope) else { return }
        
        let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
        let context = NWConnection.ContentContext(identifier: "wsText", metadata: [metadata])
        
        print("[NetworkManager] Sending type: \(type) to \(activeConnections.count) connections")
        for conn in activeConnections {
            conn.send(content: data, contentContext: context, isComplete: true, completion: .contentProcessed({ error in
                if let error = error {
                    print("[NetworkManager] Send error to connection: \(error)")
                }
            }))
        }
    }
}
