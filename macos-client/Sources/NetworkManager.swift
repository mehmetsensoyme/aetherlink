import Foundation
import Network
import AetherShared

@MainActor
public final class NetworkManager: ObservableObject {
    public static let shared = NetworkManager()
    
    @Published public var isConnected = false
    @Published public var connectedDeviceName: String = "Bağlı Cihaz Yok"
    @Published public var connectedDeviceIP: String? = nil
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
    private var primaryConnection: NWConnection?
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
                guard let self else { return }
                Task { @MainActor in
                    self.handleNewConnection(connection)
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
        connection.stateUpdateHandler = { [weak self, weak connection] state in
            guard let self, let connection else { return }
            Task { @MainActor in
                switch state {
                case .ready:
                    if self.isConnected && self.primaryConnection != nil && self.primaryConnection !== connection {
                        print("[NetworkManager] Secondary connection established while primary device '\(self.connectedDeviceName)' is active. Awaiting handshake to reject with DEVICE_BUSY...")
                        self.receiveNextMessage(from: connection)
                        return
                    }
                    
                    self.primaryConnection = connection
                    self.activeConnections = [connection]
                    self.isConnected = true
                    if self.connectedDeviceName == "Bağlantı Kesildi" || self.connectedDeviceName.isEmpty {
                        self.connectedDeviceName = "Bağlanıyor..."
                    }
                    if case .hostPort(let host, _) = connection.endpoint {
                        let hostStr = "\(host)".components(separatedBy: "%").first ?? "\(host)"
                        self.connectedDeviceIP = hostStr
                        print("[NetworkManager] Remote client endpoint IP: \(hostStr)")
                    }
                    print("[NetworkManager] Primary client connected: \(self.connectedDeviceIP ?? "unknown")")
                    
                    // Auto-dismiss pairing modal and return to dashboard
                    PopoverStateManager.shared.currentPage = .dashboard
                    AetherWindowManager.shared.closePairingQRWindow()
                    
                    let macName = Host.current().localizedName ?? "MacBook Pro"
                    let helloPayload = MacHelloPayload(
                        macName: macName,
                        timestamp: Date().timeIntervalSince1970 * 1000
                    )
                    self.send(type: "MAC_HELLO", payload: helloPayload)
                    MacBatteryMonitor.shared.broadcastBatteryState()
                    self.receiveNextMessage(from: connection)
                    
                case .cancelled, .failed:
                    let wasConnected = self.isConnected
                    self.connectionBuffers.removeValue(forKey: ObjectIdentifier(connection))
                    self.activeConnections.removeAll { $0 === connection }
                    if self.primaryConnection === connection {
                        self.primaryConnection = nil
                        self.resetSessionState(showNotification: wasConnected, reasonText: "Telefon bağlantısı kesildi")
                    }
                    print("[NetworkManager] Connection closed (Active: \(self.activeConnections.count))")
                default:
                    break
                }
            }
        }
        connection.start(queue: .main)
    }
    
    private func receiveNextMessage(from connection: NWConnection) {
        connection.receiveMessage { [weak self, weak connection] (data, context, isComplete, error) in
            guard let self, let connection else { return }
            Task { @MainActor in
                let id = ObjectIdentifier(connection)
                if let data = data, !data.isEmpty {
                    var current = self.connectionBuffers[id] ?? Data()
                    current.append(data)
                    if isComplete {
                        self.connectionBuffers.removeValue(forKey: id)
                        self.parseIncomingData(current, from: connection)
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
    
    private func sendDeviceBusyAndReject(to connection: NWConnection) {
        let busyEnvelope: [String: Any] = [
            "type": "DEVICE_BUSY",
            "payload": [
                "error": "DEVICE_BUSY",
                "message": "Mac başka bir cihaza bağlı",
                "activeDevice": self.connectedDeviceName,
                "timestamp": Date().timeIntervalSince1970 * 1000
            ]
        ]
        if let data = try? JSONSerialization.data(withJSONObject: busyEnvelope) {
            let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
            let context = NWConnection.ContentContext(identifier: "wsText", metadata: [metadata])
            connection.send(content: data, contentContext: context, isComplete: true, completion: .contentProcessed({ [weak connection] _ in
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    connection?.cancel()
                }
            }))
        } else {
            connection.cancel()
        }
        print("[NetworkManager] Successfully dispatched DEVICE_BUSY and cancelled competing connection.")
    }
    
    private func parseIncomingData(_ data: Data, from connection: NWConnection) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else {
            print("[NetworkManager] Failed to parse JSON or missing type")
            return
        }
        print("[NetworkManager] Received message type: \(type)")
        
        // Single Active Device Lock: Reject if another device is active
        if self.isConnected && self.primaryConnection != nil && connection !== self.primaryConnection {
            print("[NetworkManager] Competing connection sent type '\(type)' while connected to '\(self.connectedDeviceName)'. Rejecting...")
            self.sendDeviceBusyAndReject(to: connection)
            return
        }
        
        switch type {
        case "DEVICE_INFO", "CLIENT_HANDSHAKE":
            if let payload = json["payload"] as? [String: Any],
               let name = payload["deviceName"] as? String, !name.isEmpty {
                self.connectedDeviceName = name
                print("[NetworkManager] Set connectedDeviceName from DEVICE_INFO: \(name)")
                if let battery = self.batteryState {
                    AetherWidgetDataManager.shared.updateBattery(
                        level: battery.batteryLevel,
                        isCharging: battery.isCharging,
                        deviceName: name,
                        isConnected: true,
                        powerSave: battery.powerSaveMode,
                        temp: battery.temperatureCelsius
                    )
                }
                NotificationManager.shared.broadcastSuppressedPackagesToAndroid()
            }

        case "PAIRING_REQUEST":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let request = try? JSONDecoder().decode(PairingRequestPayload.self, from: payloadData) {
                if !request.deviceName.isEmpty {
                    self.connectedDeviceName = request.deviceName
                }
                PairingManager.shared.handleIncomingPairingRequest(request)
            }
            
        case "BATTERY_UPDATE":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let battery = try? JSONDecoder().decode(BatteryPayload.self, from: payloadData) {
                self.batteryState = battery
                // Sync to App Group UserDefaults for Widget Extension & trigger WidgetCenter
                AetherWidgetDataManager.shared.updateBattery(
                    level: battery.batteryLevel,
                    isCharging: battery.isCharging,
                    deviceName: self.connectedDeviceName,
                    isConnected: true,
                    powerSave: battery.powerSaveMode,
                    temp: battery.temperatureCelsius
                )
            }
            
        case "MEDIA_UPDATE":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let media = try? JSONDecoder().decode(MediaSessionPayload.self, from: payloadData) {
                self.mediaState = media
                MediaContinuityManager.shared.handleIncomingMedia(media, autoOpen: true)
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
            
        case "CALL_INCOMING", "INCOMING_CALL":
            let payloadDict = json["payload"] as? [String: Any] ?? [:]
            let callerName = (payloadDict["contact_name"] as? String)
                ?? (payloadDict["callerName"] as? String)
                ?? (payloadDict["caller"] as? String)
                ?? "Bilinmeyen Numara"
            let phoneNumber = (payloadDict["phoneNumber"] as? String)
                ?? (payloadDict["number"] as? String)
                ?? ""
            let callId = (payloadDict["callId"] as? String) ?? UUID().uuidString
            let appTypeStr = (payloadDict["appType"] as? String) ?? "cellular"
            let appType = CallAppType(rawValue: appTypeStr) ?? .cellular
            let avatar = payloadDict["avatarBase64"] as? String
            
            print("[NetworkManager] Received INCOMING_CALL / CALL_INCOMING: \(callerName) (\(phoneNumber))")
            
            DispatchQueue.main.async {
                if let payloadData = try? JSONSerialization.data(withJSONObject: payloadDict),
                   let call = try? JSONDecoder().decode(CallIncomingPayload.self, from: payloadData) {
                    CallManager.shared.handleIncomingCall(call)
                } else {
                    NotchCallManager.shared.show(
                        caller: callerName,
                        number: phoneNumber,
                        callId: callId,
                        appType: appType,
                        avatarBase64: avatar
                    )
                }
            }
            
        case "CALL_ACTION":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let action = try? JSONDecoder().decode(CallActionPayload.self, from: payloadData) {
                print("[NetworkManager] Received CALL_ACTION: \(action.action) (\(action.callId))")
                DispatchQueue.main.async {
                    if action.action == "hangup" || action.action == "decline" || action.action == "rejected" {
                        CallManager.shared.dismissCallBanner()
                        NotchCallManager.shared.dismiss()
                        CallAudioStreamEngine.shared.stop()
                    } else if action.action == "answered" || action.action == "accept" || action.action == "active" {
                        CallManager.shared.handleCallAnswered()
                        NotchCallManager.shared.handleCallAnswered()
                    }
                }
            }
            
        case "REJECT_CALL", "END_CALL":
            DispatchQueue.main.async {
                CallManager.shared.dismissCallBanner()
                NotchCallManager.shared.dismiss()
                CallAudioStreamEngine.shared.stop()
            }
            
        case "ACCEPT_CALL":
            DispatchQueue.main.async {
                CallManager.shared.handleCallAnswered()
                NotchCallManager.shared.handleCallAnswered()
            }
            
        case "DEVICE_TELEMETRY":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let tele = try? JSONDecoder().decode(DeviceTelemetryPayload.self, from: payloadData) {
                DeviceTelemetryManager.shared.handleIncomingTelemetry(tele)
                let name = "\(tele.manufacturer) \(tele.model)".trimmingCharacters(in: .whitespaces)
                if !name.isEmpty && (self.connectedDeviceName == "Bağlanıyor..." || self.connectedDeviceName.isEmpty || self.connectedDeviceName == "Bağlantı Kesildi") {
                    self.connectedDeviceName = name
                }
                // Sync battery from telemetry to App Group UserDefaults
                AetherWidgetDataManager.shared.updateBattery(
                    level: tele.batteryLevel,
                    isCharging: tele.isCharging,
                    deviceName: self.connectedDeviceName,
                    isConnected: true,
                    temp: tele.effectiveTemp
                )
                // Respond with live Mac telemetry
                MacThermalService.shared.broadcastTelemetry()
            }
            
        case "SCREEN_STREAM_FRAME":
            // Deprecated: screen mirroring is now handled directly via scrcpy
            break
            
        case "DISCONNECT":
            let payload = json["payload"] as? [String: Any]
            let source = payload?["source"] as? String ?? "android"
            print("[NetworkManager] Received DISCONNECT from source: \(source)")
            let shouldShowAlert = (source == "android")
            self.resetSessionState(showNotification: shouldShowAlert, reasonText: "Telefon bağlantısı kesildi")
            
        case "MAC_BATTERY_REQUEST":
            MacBatteryMonitor.shared.broadcastBatteryState()
            MacThermalService.shared.broadcastTelemetry()
            
        case "MAC_TELEMETRY_REQUEST":
            MacThermalService.shared.readHardwareTemperature(forceFresh: true)
            MacThermalService.shared.broadcastTelemetry()
            
        case "HEARTBEAT_PING":
            self.send(type: "HEARTBEAT_PONG", payload: ["timestamp": Date().timeIntervalSince1970 * 1000])
            
        // MARK: - KDE Connect Ported Modules
        case "FIND_MY_MAC_REQUEST":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let payload = try? JSONDecoder().decode(FindMyDevicePayload.self, from: payloadData) {
                FindMyDeviceManager.shared.handleIncomingRingMacRequest(payload)
            } else {
                FindMyDeviceManager.shared.handleIncomingRingMacRequest(FindMyDevicePayload(action: "ring"))
            }
            
        case "FIND_MY_PHONE_STATUS":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let payload = try? JSONDecoder().decode(FindMyDevicePayload.self, from: payloadData) {
                FindMyDeviceManager.shared.handlePhoneStatusUpdate(payload)
            }
            
        case "SET_MAC_VOLUME":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let payload = try? JSONDecoder().decode(SystemVolumePayload.self, from: payloadData) {
                RemoteVolumeManager.shared.handleRemoteSetMacVolume(payload)
            }
            
        case "PHONE_VOLUME_UPDATE":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let payload = try? JSONDecoder().decode(SystemVolumePayload.self, from: payloadData) {
                RemoteVolumeManager.shared.handleIncomingPhoneVolumeUpdate(payload)
            }
            
        case "LOCK_MAC":
            RemoteLockManager.shared.handleIncomingLockMacRequest()
            
        case "LOCK_PHONE_RESULT":
            if let payloadDict = json["payload"] as? [String: Any] {
                RemoteLockManager.shared.handleIncomingLockPhoneResult(payloadDict)
            }
            
        case "CONNECTIVITY_REPORT":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let payload = try? JSONDecoder().decode(ConnectivityReportPayload.self, from: payloadData) {
                DeviceTelemetryManager.shared.handleConnectivityReport(payload)
            }
            
        case "PING":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let payload = try? JSONDecoder().decode(PingPayload.self, from: payloadData) {
                DeviceTelemetryManager.shared.handleIncomingPing(payload)
            }
            
        case "PONG":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let payload = try? JSONDecoder().decode(PingPayload.self, from: payloadData) {
                DeviceTelemetryManager.shared.handlePong(payload)
            }
            
        case "REMOTE_INPUT":
            if let payloadData = try? JSONSerialization.data(withJSONObject: json["payload"] ?? [:]),
               let payload = try? JSONDecoder().decode(RemoteInputPayload.self, from: payloadData) {
                RemoteInputManager.shared.handleRemoteInput(payload)
            }
            
        case "CAFFEINATE_REQUEST":
            if let payload = json["payload"] as? [String: Any],
               let active = payload["isActive"] as? Bool {
                CaffeinateManager.shared.setCaffeinate(active: active)
            }
            
        case "PRESENTER_COMMAND":
            if let payload = json["payload"] as? [String: Any],
               let action = payload["action"] as? String {
                PresenterManager.shared.handleCommand(action)
            }
            
        case "REMOTE_COMMAND":
            if let payload = json["payload"] as? [String: Any],
               let key = payload["key"] as? String {
                RemoteCommandManager.shared.executeCommand(key)
            }
            
        case "SHARE_URL":
            if let payload = json["payload"] as? [String: Any],
               let url = payload["url"] as? String {
                AetherShareManager.shared.handleIncomingUrl(url)
            }
            
        case "SHARE_TEXT":
            if let payload = json["payload"] as? [String: Any],
               let text = payload["text"] as? String {
                AetherShareManager.shared.handleIncomingText(text)
            }
            
        case "SHARE_FILE":
            if let payload = json["payload"] as? [String: Any],
               let fileName = payload["fileName"] as? String,
               let base64 = payload["base64Data"] as? String {
                AetherShareManager.shared.handleIncomingFile(fileName: fileName, base64Data: base64)
            }
            
        case "CALL_AUDIO_START":
            // Ignored to avoid acoustic feedback and audio mixing
            break
            
        case "CALL_AUDIO_STOP":
            CallAudioStreamEngine.shared.stop()
            
        default:
            print("[NetworkManager] Unhandled message type: \(type)")
        }
    }
    
    @MainActor
    public func resetSessionState(showNotification: Bool = false, reasonText: String = "Telefon bağlantısı kesildi") {
        self.isConnected = false
        self.primaryConnection = nil
        self.connectedDeviceName = "Bağlantı Kesildi"
        self.connectedDeviceIP = nil
        self.batteryState = nil
        self.mediaState = nil
        
        // Single Active Device Policy: Ensure Popover returns to dashboard where pairing hero is visible
        PopoverStateManager.shared.currentPage = .dashboard
        
        // 1. Terminate scrcpy process and clean up mirroring lifecycle
        ScreenMirrorManager.shared.terminateScrcpy()
        
        // 2. Reset device telemetry and specs
        DeviceTelemetryManager.shared.telemetry = nil
        
        // 3. Clear Widget Extension state via App Group
        AetherWidgetDataManager.shared.clear()
        
        // 4. Post native system alert on Mac if disconnected from phone
        if showNotification {
            NotificationManager.shared.displayNotification(NotificationPayload(
                id: UUID().uuidString,
                key: "disconnect_alert",
                packageName: "system",
                appName: "AetherLink",
                title: "AetherLink",
                text: reasonText,
                subText: nil,
                timestamp: Date().timeIntervalSince1970 * 1000,
                canReply: false,
                replyPlaceholder: nil,
                appIconBase64: nil
            ))
        }
        print("[NetworkManager] Reset session state complete (Notification: \(showNotification))")
    }
    
    public func disconnectDevice(forget: Bool = false) {
        ScreenMirrorManager.shared.terminateScrcpy()
        if forget {
            PairingManager.shared.unpair()
        }
        let payload = DisconnectPayload(
            reason: forget ? "unpair" : "user_requested",
            source: "macos",
            shouldForget: forget,
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        self.send(type: "DISCONNECT", payload: payload)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.primaryConnection?.cancel()
            self.primaryConnection = nil
            for conn in self.activeConnections {
                conn.cancel()
            }
            self.activeConnections.removeAll()
            self.resetSessionState(showNotification: false)
            print("[NetworkManager] Disconnected active device from Mac (forget: \(forget))")
        }
    }
    
    public func send<T: Encodable>(type: String, payload: T) {
        guard let target = primaryConnection ?? activeConnections.first else { return }
        let envelope: [String: Any] = [
            "type": type,
            "payload": (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(payload))) ?? [:]
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: envelope) else { return }
        
        let metadata = NWProtocolWebSocket.Metadata(opcode: .text)
        let context = NWConnection.ContentContext(identifier: "wsText", metadata: [metadata])
        
        print("[NetworkManager] Sending type: \(type) to primary connection")
        target.send(content: data, contentContext: context, isComplete: true, completion: .contentProcessed({ error in
            if let error = error {
                print("[NetworkManager] Send error to connection: \(error)")
            }
        }))
    }
}
