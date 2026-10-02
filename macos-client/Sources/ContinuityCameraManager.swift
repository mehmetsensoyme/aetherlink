import Foundation
import AppKit
import SwiftUI
import Network
import Combine
import AVFoundation

@MainActor
public final class ContinuityCameraManager: NSObject, ObservableObject {
    public static let shared = ContinuityCameraManager()
    
    // Live Stream States
    @Published public var isStreaming: Bool = false
    @Published public var isConnecting: Bool = false
    @Published public var currentFrame: NSImage? = nil
    @Published public var currentLens: String = "back" // "back" or "front"
    @Published public var isTorchOn: Bool = false
    @Published public var isMicActive: Bool = true
    @Published public var currentResolution: String = "1080p" // "1080p" or "720p"
    
    // Performance & Telemetry Metrics
    @Published public var currentFps: Int = 0
    @Published public var latencyMs: Double = 0.0
    @Published public var bitrateMbps: Double = 0.0
    @Published public var lastErrorMessage: String? = nil
    @Published public var lastSnapshotPath: String? = nil
    @Published public var showSnapshotToast: Bool = false
    @Published public var toastMessage: String = ""
    
    public func triggerSnapshotToast(_ message: String) {
        self.toastMessage = message
        withAnimation { self.showSnapshotToast = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { self.showSnapshotToast = false }
        }
    }
    
    // Audio Player for Studio Microphone Streaming
    private var audioEngine: AVAudioEngine?
    private var audioPlayerNode: AVAudioPlayerNode?
    private var audioFormat: AVAudioFormat?
    
    // Networking
    private var streamReceiver: ContinuityCameraStreamReceiver?
    private let streamQueue = DispatchQueue(label: "org.aetherlink.camera.stream", qos: .userInteractive)
    private let decodeQueue = DispatchQueue(label: "org.aetherlink.camera.decode", qos: .userInteractive)
    
    // Metrics calculation
    private var frameCounter = 0
    private var lastFpsCalculationTime: TimeInterval = 0
    private var bytesReceivedCounter: Int64 = 0
    private var lastBitrateCalculationTime: TimeInterval = 0
    
    public override init() {
        super.init()
        setupAudioEngine()
    }
    
    // MARK: - Audio Engine Setup (48kHz 16-bit PCM playback)
    private func setupAudioEngine() {
        let engine = AVAudioEngine()
        let playerNode = AVAudioPlayerNode()
        let format = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 48000, channels: 1, interleaved: false)
        
        engine.attach(playerNode)
        if let format = format {
            engine.connect(playerNode, to: engine.mainMixerNode, format: format)
        }
        
        self.audioEngine = engine
        self.audioPlayerNode = playerNode
        self.audioFormat = format
    }
    
    // MARK: - Start & Stop Streaming
    public func startStream(lens: String = "back", resolution: String = "1080p", torch: Bool = false, mic: Bool = true) {
        guard let ip = NetworkManager.shared.connectedDeviceIP, !ip.isEmpty else {
            self.lastErrorMessage = "Bağlı bir Android cihaz bulunamadı."
            return
        }
        
        guard !isStreaming && !isConnecting else { return }
        
        self.isConnecting = true
        self.lastErrorMessage = nil
        self.currentLens = lens
        self.currentResolution = resolution
        self.isTorchOn = torch
        self.isMicActive = mic
        
        // 1. Notify Android device to start hardware Camera2 & Audio pipeline
        let payload: [String: Any] = [
            "lens": lens,
            "resolution": resolution,
            "torch": torch,
            "mic": mic
        ]
        NetworkManager.shared.send(type: "CONTINUITY_CAMERA_START", payload: payload)
        
        // 2. Connect to Android TCP port 8445
        connectTcpStream(targetIP: ip, port: 8445)
        
        // 3. Start local audio engine if microphone enabled
        if mic {
            startAudioPlayback()
        }
        
        // 4. Open Floating HUD Window
        AetherWindowManager.shared.showContinuityCameraWindow()
    }
    
    public func stopStream() {
        guard isStreaming || isConnecting else { return }
        
        // Notify Android device to stop camera hardware & service
        NetworkManager.shared.send(type: "CONTINUITY_CAMERA_STOP", payload: [:])
        
        disconnectTcpStream()
        stopAudioPlayback()
        
        self.isStreaming = false
        self.isConnecting = false
        self.currentFrame = nil
        self.currentFps = 0
        self.latencyMs = 0.0
        self.bitrateMbps = 0.0
        
        AetherWindowManager.shared.closeContinuityCameraWindow()
    }
    
    // MARK: - Incoming Status from Android (Phone -> Mac)
    public func handleRemoteStatus(isStreaming: Bool, lens: String, resolution: String, torch: Bool, mic: Bool, port: Int) {
        if isStreaming {
            guard !self.isStreaming && !self.isConnecting else { return }
            guard let ip = NetworkManager.shared.connectedDeviceIP, !ip.isEmpty else { return }
            self.isConnecting = true
            self.lastErrorMessage = nil
            self.currentLens = lens
            self.currentResolution = resolution
            self.isTorchOn = torch
            self.isMicActive = mic
            connectTcpStream(targetIP: ip, port: port > 0 ? UInt16(port) : 8445)
            if mic {
                startAudioPlayback()
            }
            AetherWindowManager.shared.showContinuityCameraWindow()
        } else {
            if self.isStreaming || self.isConnecting {
                disconnectTcpStream()
                stopAudioPlayback()
                self.isStreaming = false
                self.isConnecting = false
                self.currentFrame = nil
                self.currentFps = 0
                self.latencyMs = 0.0
                self.bitrateMbps = 0.0
                AetherWindowManager.shared.closeContinuityCameraWindow()
            }
        }
    }
    
    // MARK: - Remote Control Operations
    public func switchCamera() {
        NetworkManager.shared.send(type: "CONTINUITY_CAMERA_SWITCH", payload: [:])
        self.currentLens = (self.currentLens == "back") ? "front" : "back"
        if self.currentLens == "front" {
            self.isTorchOn = false
        }
    }
    
    public func toggleTorch() {
        guard currentLens == "back" else { return }
        NetworkManager.shared.send(type: "CONTINUITY_CAMERA_TORCH", payload: [:])
        self.isTorchOn.toggle()
    }
    
    public func toggleMic() {
        NetworkManager.shared.send(type: "CONTINUITY_CAMERA_MIC", payload: [:])
        self.isMicActive.toggle()
        if isMicActive {
            startAudioPlayback()
        } else {
            stopAudioPlayback()
        }
    }
    
    public func setResolution(_ resolution: String) {
        guard resolution != currentResolution else { return }
        self.currentResolution = resolution
        let payload: [String: Any] = [
            "lens": currentLens,
            "resolution": resolution,
            "torch": isTorchOn,
            "mic": isMicActive
        ]
        NetworkManager.shared.send(type: "CONTINUITY_CAMERA_START", payload: payload)
    }
    
    // MARK: - High-Resolution Snapshot Capture
    public func takeSnapshot() {
        guard let frame = currentFrame else { return }
        
        decodeQueue.async { [weak self] in
            guard let self = self else { return }
            guard let tiffData = frame.tiffRepresentation,
                  let bitmap = NSBitmapImageRep(data: tiffData),
                  let jpegData = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.95]) else {
                return
            }
            
            let picturesDir = FileManager.default.urls(for: .picturesDirectory, in: .userDomainMask).first
                ?? FileManager.default.homeDirectoryForCurrentUser
            let folder = picturesDir.appendingPathComponent("AetherLink_Snapshots")
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
            let timestamp = formatter.string(from: Date())
            let fileURL = folder.appendingPathComponent("AetherLink_Snapshot_\(timestamp).jpg")
            
            do {
                try jpegData.write(to: fileURL)
                Task { @MainActor in
                    self.lastSnapshotPath = fileURL.path
                    NSSound(named: "camera_shutter")?.play()
                }
            } catch {
                print("[ContinuityCameraManager] Snapshot write error: \(error)")
            }
        }
    }
    
    // MARK: - TCP Stream Client (Port 8445)
    private func connectTcpStream(targetIP: String, port: UInt16) {
        disconnectTcpStream()
        
        let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(targetIP),
            port: NWEndpoint.Port(integerLiteral: port)
        )
        let params = NWParameters.tcp
        params.allowFastOpen = true
        params.prohibitedInterfaceTypes = [.cellular]
        
        let connection = NWConnection(to: endpoint, using: params)
        let receiver = ContinuityCameraStreamReceiver(queue: streamQueue)
        self.streamReceiver = receiver
        
        receiver.onPacketReceived = { [weak self] type, payload, timestampMs in
            Task { @MainActor [weak self] in
                self?.handlePacket(type: type, payload: payload, timestampMs: timestampMs)
            }
        }
        
        receiver.onError = { [weak self] error in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                if let error = error {
                    self.lastErrorMessage = "Kamera akışı koptu: \(error.localizedDescription)"
                }
                self.stopStream()
            }
        }
        
        connection.stateUpdateHandler = { [weak self] state in
            guard let self = self else { return }
            Task { @MainActor in
                switch state {
                case .ready:
                    print("[ContinuityCameraManager] TCP Connection established to \(targetIP):\(port)")
                    self.isConnecting = false
                    self.isStreaming = true
                    self.lastFpsCalculationTime = ProcessInfo.processInfo.systemUptime
                    self.lastBitrateCalculationTime = ProcessInfo.processInfo.systemUptime
                    receiver.start(connection: connection)
                case .failed(let error):
                    print("[ContinuityCameraManager] TCP Connection failed: \(error)")
                    self.lastErrorMessage = "Kamera akışına bağlanılamadı: \(error.localizedDescription)"
                    self.stopStream()
                case .cancelled:
                    print("[ContinuityCameraManager] TCP Connection cancelled.")
                default:
                    break
                }
            }
        }
        
        connection.start(queue: streamQueue)
    }
    
    private func disconnectTcpStream() {
        streamReceiver?.stop()
        streamReceiver = nil
    }
    
    private func handlePacket(type: UInt8, payload: Data, timestampMs: Int64) {
        bytesReceivedCounter += Int64(payload.count + 17)
        
        switch type {
        case 0x01: // Video Frame (JPEG)
            handleVideoFrame(payload, timestampMs: timestampMs)
        case 0x02: // Audio Chunk (PCM 16-bit 48kHz)
            handleAudioChunk(payload)
        case 0x03: // Status JSON
            handleStatusJson(payload)
        default:
            break
        }
    }
    
    private func handleVideoFrame(_ data: Data, timestampMs: Int64) {
        decodeQueue.async { [weak self] in
            guard let self = self else { return }
            guard let image = NSImage(data: data) else { return }
            
            let nowUptime = ProcessInfo.processInfo.systemUptime
            let nowEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
            let computedLatency = max(4.0, Double(nowEpochMs - timestampMs))
            
            Task { @MainActor [weak self] in
                guard let self = self, self.isStreaming else { return }
                self.currentFrame = image
                self.latencyMs = computedLatency
                self.frameCounter += 1
                
                // Update FPS calculation every second
                if nowUptime - self.lastFpsCalculationTime >= 1.0 {
                    let elapsed = nowUptime - self.lastFpsCalculationTime
                    self.currentFps = Int(Double(self.frameCounter) / elapsed)
                    self.frameCounter = 0
                    self.lastFpsCalculationTime = nowUptime
                }
                
                // Update Bitrate calculation every second
                if nowUptime - self.lastBitrateCalculationTime >= 1.0 {
                    let elapsed = nowUptime - self.lastBitrateCalculationTime
                    let bits = Double(self.bytesReceivedCounter * 8)
                    self.bitrateMbps = (bits / elapsed) / 1_000_000.0
                    self.bytesReceivedCounter = 0
                    self.lastBitrateCalculationTime = nowUptime
                }
            }
        }
    }
    
    private func handleAudioChunk(_ data: Data) {
        guard isMicActive, let engine = audioEngine, let player = audioPlayerNode, let format = audioFormat else { return }
        
        let frameCount = UInt32(data.count / 2) // 16-bit mono = 2 bytes per sample
        guard let pcmBuffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount) else { return }
        pcmBuffer.frameLength = frameCount
        
        data.withUnsafeBytes { rawBuffer in
            if let src = rawBuffer.baseAddress, let dst = pcmBuffer.int16ChannelData?[0] {
                memcpy(dst, src, data.count)
            }
        }
        
        if !engine.isRunning {
            try? engine.start()
        }
        if !player.isPlaying {
            player.play()
        }
        
        player.scheduleBuffer(pcmBuffer, completionHandler: nil)
    }
    
    private func handleStatusJson(_ data: Data) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            if let lens = json["lens"] as? String { self.currentLens = lens }
            if let res = json["resolution"] as? String { self.currentResolution = res }
            if let torch = json["isTorchOn"] as? Bool { self.isTorchOn = torch }
            if let mic = json["isMicActive"] as? Bool { self.isMicActive = mic }
        }
    }
    
    private func startAudioPlayback() {
        guard let engine = audioEngine, let player = audioPlayerNode else { return }
        do {
            if !engine.isRunning {
                try engine.start()
            }
            if !player.isPlaying {
                player.play()
            }
        } catch {
            print("[ContinuityCameraManager] Audio engine start error: \(error)")
        }
    }
    
    private func stopAudioPlayback() {
        audioPlayerNode?.stop()
        audioEngine?.stop()
    }
}

// MARK: - Safe Exact Byte Accumulator
// Ensures TCP stream fragmentation never desynchronizes the protocol framing
final class ContinuityCameraStreamReceiver: @unchecked Sendable {
    private var connection: NWConnection?
    private var isRunning: Bool = false
    private let streamQueue: DispatchQueue
    
    var onPacketReceived: (@Sendable (UInt8, Data, Int64) -> Void)?
    var onError: (@Sendable (Error?) -> Void)?
    
    init(queue: DispatchQueue) {
        self.streamQueue = queue
    }
    
    func start(connection: NWConnection) {
        self.connection = connection
        self.isRunning = true
        readHeader()
    }
    
    func stop() {
        self.isRunning = false
        connection?.cancel()
        connection = nil
    }
    
    private func readExactBytes(count: Int, accumulated: Data = Data(), completion: @escaping @Sendable (Data?) -> Void) {
        guard let conn = connection, isRunning else {
            completion(nil)
            return
        }
        let needed = count - accumulated.count
        if needed <= 0 {
            completion(accumulated)
            return
        }
        
        let batchSize = min(needed, 65536)
        conn.receive(minimumIncompleteLength: 1, maximumLength: batchSize) { [weak self] content, _, isComplete, error in
            guard let self = self, self.isRunning else {
                completion(nil)
                return
            }
            if let error = error {
                print("[ContinuityCameraStreamReceiver] Read error: \(error)")
                self.onError?(error)
                completion(nil)
                return
            }
            guard let chunk = content, !chunk.isEmpty else {
                if isComplete {
                    self.onError?(nil)
                    completion(nil)
                }
                return
            }
            
            var buffer = accumulated
            buffer.append(chunk)
            if buffer.count == count {
                completion(buffer)
            } else if buffer.count < count {
                self.readExactBytes(count: count, accumulated: buffer, completion: completion)
            } else {
                completion(buffer.prefix(count))
            }
        }
    }
    
    private func readHeader() {
        readExactBytes(count: 17) { [weak self] headerData in
            guard let self = self, self.isRunning else { return }
            guard let data = headerData else { return }
            
            let magic = data.subdata(in: 0..<4)
            guard magic == Data([0x41, 0x45, 0x54, 0x48]) else {
                print("[ContinuityCameraStreamReceiver] Invalid packet magic header; skipping...")
                self.readHeader()
                return
            }
            
            let packetType = data[4]
            let payloadLength = Int(data.subdata(in: 5..<9).withUnsafeBytes { $0.load(as: UInt32.self).bigEndian })
            let timestampMs = Int64(data.subdata(in: 9..<17).withUnsafeBytes { $0.load(as: UInt64.self).bigEndian })
            
            guard payloadLength > 0 && payloadLength < 25_000_000 else {
                self.readHeader()
                return
            }
            
            self.readExactBytes(count: payloadLength) { [weak self] payloadData in
                guard let self = self, self.isRunning else { return }
                guard let payload = payloadData else { return }
                
                self.onPacketReceived?(packetType, payload, timestampMs)
                self.readHeader()
            }
        }
    }
}

