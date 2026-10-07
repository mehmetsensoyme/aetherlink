import AppKit
import AVFoundation
import Foundation

// MARK: - AetherAudio Stream Modes
public enum AudioStreamMode: String, CaseIterable, Identifiable {
    case hybrid = "hybrid"
    case media = "media"
    case call = "call"
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .hybrid: return "Otomatik Hibrit"
        case .media: return "Yalnızca Medya"
        case .call: return "Yalnızca Arama"
        }
    }
    
    public var subtitle: String {
        switch self {
        case .hybrid: return "Medya çalınca hoparlör, arama gelince çift yönlü konuşma"
        case .media: return "Android medya ve müzik seslerini Mac'e aktar"
        case .call: return "Telefon aramalarını Mac mikrofonu ve hoparlörüyle yönet"
        }
    }
}

// MARK: - UDP Network Worker (Thread-safe Nonisolated Engine)
private final class AetherAudioNetworkWorker: @unchecked Sendable {
    private var udpSocketFd: Int32 = -1
    private var isSocketListening = false
    private let networkQueue = DispatchQueue(label: "org.aetherlink.audio.network", qos: .userInteractive)
    
    private static let magicBytes: [UInt8] = [0x41, 0x45, 0x41, 0x55] // "AEAU"
    
    func startListening(port: UInt16, onPacket: @escaping @Sendable (UInt8, Data) -> Void) {
        stopListening()
        networkQueue.async { [weak self] in
            guard let self = self else { return }
            
            let fd = socket(AF_INET, SOCK_DGRAM, 0)
            guard fd >= 0 else {
                print("[AetherAudio] Failed to create UDP socket.")
                return
            }
            self.udpSocketFd = fd
            
            var reuse: Int32 = 1
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, socklen_t(MemoryLayout<Int32>.size))
            setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &reuse, socklen_t(MemoryLayout<Int32>.size))
            
            var addr = sockaddr_in()
            addr.sin_family = sa_family_t(AF_INET)
            addr.sin_port = port.bigEndian
            addr.sin_addr.s_addr = INADDR_ANY.bigEndian
            
            let bindRes = withUnsafePointer(to: &addr) { ptr in
                ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sa in
                    bind(fd, sa, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }
            
            guard bindRes >= 0 else {
                print("[AetherAudio] Failed to bind UDP socket to port \(port)")
                close(fd)
                self.udpSocketFd = -1
                return
            }
            
            self.isSocketListening = true
            print("[AetherAudio] Listening for AetherAudio packets on UDP port \(port)")
            
            var rxBuffer = [UInt8](repeating: 0, count: 4096)
            var senderAddr = sockaddr_in()
            var addrLen = socklen_t(MemoryLayout<sockaddr_in>.size)
            
            while self.isSocketListening && self.udpSocketFd >= 0 {
                let bytesRead = withUnsafeMutablePointer(to: &senderAddr) { ptr in
                    ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sa in
                        recvfrom(self.udpSocketFd, &rxBuffer, rxBuffer.count, 0, sa, &addrLen)
                    }
                }
                
                if bytesRead >= 8 {
                    if rxBuffer[0] == Self.magicBytes[0] &&
                       rxBuffer[1] == Self.magicBytes[1] &&
                       rxBuffer[2] == Self.magicBytes[2] &&
                       rxBuffer[3] == Self.magicBytes[3] {
                        
                        let type = rxBuffer[4]
                        let payloadLen = (Int(rxBuffer[6]) << 8) | Int(rxBuffer[7])
                        let safeLen = min(payloadLen, bytesRead - 8)
                        
                        if safeLen > 0 {
                            let pcmData = Data(rxBuffer[8..<(8 + safeLen)])
                            onPacket(type, pcmData)
                        }
                    }
                }
            }
        }
    }
    
    func sendPacket(packetData: Data, toHost host: String, port: UInt16) {
        networkQueue.async { [weak self] in
            guard let self = self, self.udpSocketFd >= 0 else { return }
            var targetAddr = sockaddr_in()
            targetAddr.sin_family = sa_family_t(AF_INET)
            targetAddr.sin_port = port.bigEndian
            inet_pton(AF_INET, host, &targetAddr.sin_addr)
            
            packetData.withUnsafeBytes { raw in
                if let ptr = raw.baseAddress {
                    withUnsafePointer(to: &targetAddr) { saPtr in
                        saPtr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sa in
                            _ = sendto(self.udpSocketFd, ptr, packetData.count, 0, sa, socklen_t(MemoryLayout<sockaddr_in>.size))
                        }
                    }
                }
            }
        }
    }
    
    func stopListening() {
        isSocketListening = false
        if udpSocketFd >= 0 {
            close(udpSocketFd)
            udpSocketFd = -1
        }
    }
}

// MARK: - AetherAudioManager (Zero-ADB Wireless Audio & Wi-Fi Call Engine)
@MainActor
public final class AetherAudioManager: ObservableObject {
    public static let shared = AetherAudioManager()
    public static let audioPort: UInt16 = 8446
    
    private static let magicBytes: [UInt8] = [0x41, 0x45, 0x41, 0x55] // "AEAU"
    private static let pktTypeMedia: UInt8 = 0x01
    private static let pktTypeCallDownlink: UInt8 = 0x02
    private static let pktTypeCallUplink: UInt8 = 0x03
    
    @Published public var isStreaming: Bool = false
    @Published public var currentMode: AudioStreamMode = .hybrid
    @Published public var isMicUplinkActive: Bool = false
    @Published public var volume: Double = 1.0 {
        didSet {
            playerNode.volume = Float(volume)
        }
    }
    @Published public var packetsReceived: Int = 0
    
    // CoreAudio Engine & Nodes
    private let audioEngine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private var mediaFormat: AVAudioFormat?
    private var callFormat: AVAudioFormat?
    
    // Background UDP Network Worker
    private let networkWorker = AetherAudioNetworkWorker()
    
    public init() {
        setupAudioFormats()
    }
    
    private func setupAudioFormats() {
        mediaFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 48000, channels: 2, interleaved: true)
        callFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true)
        
        audioEngine.attach(playerNode)
        
        if let outputFormat = mediaFormat {
            audioEngine.connect(playerNode, to: audioEngine.mainMixerNode, format: outputFormat)
        }
    }
    
    // MARK: - Stream Controls
    public func startAudioStream(mode: AudioStreamMode = .hybrid) {
        guard !isStreaming else {
            setAudioMode(mode)
            return
        }
        
        self.currentMode = mode
        self.isStreaming = true
        
        startAudioEngine()
        networkWorker.startListening(port: Self.audioPort) { [weak self] type, pcmData in
            Task { @MainActor [weak self] in
                self?.renderIncomingAudio(type: type, pcmData: pcmData)
            }
        }
        
        // Notify Android via WebSocket
        NetworkManager.shared.send(type: "AUDIO_STREAM_START", payload: ["mode": mode.rawValue])
        print("[AetherAudioManager] AetherAudio stream started in mode: \(mode.title)")
    }
    
    public func stopAudioStream() {
        guard isStreaming else { return }
        
        self.isStreaming = false
        self.isMicUplinkActive = false
        
        networkWorker.stopListening()
        stopAudioEngine()
        
        NetworkManager.shared.send(type: "AUDIO_STREAM_STOP", payload: [:])
        print("[AetherAudioManager] AetherAudio stream stopped.")
    }
    
    public func setAudioMode(_ mode: AudioStreamMode) {
        self.currentMode = mode
        NetworkManager.shared.send(type: "AUDIO_STREAM_MODE", payload: ["mode": mode.rawValue])
        print("[AetherAudioManager] AetherAudio mode switched to: \(mode.title)")
    }
    
    // MARK: - Audio Engine Management
    private func startAudioEngine() {
        do {
            if !audioEngine.isRunning {
                try audioEngine.start()
            }
            playerNode.play()
        } catch {
            print("[AetherAudioManager] Failed to start AVAudioEngine: \(error)")
        }
    }
    
    private func stopAudioEngine() {
        playerNode.stop()
        if isMicUplinkActive {
            audioEngine.inputNode.removeTap(onBus: 0)
            isMicUplinkActive = false
        }
        audioEngine.stop()
    }
    
    // MARK: - Full Duplex Mac Microphone Uplink (During Calls)
    public func activateMicrophoneUplink() {
        guard isStreaming, !isMicUplinkActive else { return }
        
        let inputNode = audioEngine.inputNode
        let inputFormat = inputNode.inputFormat(forBus: 0)
        
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] buffer, time in
            guard let self = self else { return }
            self.sendMicrophonePacket(buffer: buffer)
        }
        
        self.isMicUplinkActive = true
        print("[AetherAudioManager] Microphone tap installed for full-duplex call uplink.")
    }
    
    public func deactivateMicrophoneUplink() {
        guard isMicUplinkActive else { return }
        audioEngine.inputNode.removeTap(onBus: 0)
        self.isMicUplinkActive = false
        print("[AetherAudioManager] Microphone tap removed.")
    }
    
    private func sendMicrophonePacket(buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.int16ChannelData else { return }
        let frameCount = Int(buffer.frameLength)
        guard frameCount > 0 else { return }
        
        let byteCount = frameCount * 2
        var packetData = Data(count: 8 + byteCount)
        
        packetData[0] = Self.magicBytes[0]
        packetData[1] = Self.magicBytes[1]
        packetData[2] = Self.magicBytes[2]
        packetData[3] = Self.magicBytes[3]
        packetData[4] = Self.pktTypeCallUplink
        packetData[5] = 0x00 // Mono 16-bit
        packetData[6] = UInt8((byteCount >> 8) & 0xFF)
        packetData[7] = UInt8(byteCount & 0xFF)
        
        packetData.replaceSubrange(8..<(8 + byteCount), with: Data(bytes: channelData[0], count: byteCount))
        
        // Transmit UDP to connected Android device IP
        guard let androidIP = NetworkManager.shared.connectedDeviceIP, !androidIP.isEmpty, androidIP != "127.0.0.1" else { return }
        networkWorker.sendPacket(packetData: packetData, toHost: androidIP, port: Self.audioPort)
    }
    
    private func renderIncomingAudio(type: UInt8, pcmData: Data) {
        self.packetsReceived += 1
        
        let format: AVAudioFormat?
        let channelCount: AVAudioChannelCount
        
        if type == Self.pktTypeCallDownlink {
            format = self.callFormat
            channelCount = 1
        } else {
            format = self.mediaFormat
            channelCount = 2
        }
        
        guard let activeFormat = format else { return }
        let bytesPerFrame = Int(channelCount) * 2
        let frameCount = AVAudioFrameCount(pcmData.count / bytesPerFrame)
        guard frameCount > 0 else { return }
        
        guard let pcmBuffer = AVAudioPCMBuffer(pcmFormat: activeFormat, frameCapacity: frameCount) else { return }
        pcmBuffer.frameLength = frameCount
        
        pcmData.withUnsafeBytes { raw in
            if let ptr = raw.baseAddress, let channelPtr = pcmBuffer.int16ChannelData {
                channelPtr[0].update(from: ptr.assumingMemoryBound(to: Int16.self), count: Int(frameCount * channelCount))
            }
        }
        
        if !self.playerNode.isPlaying {
            self.playerNode.play()
        }
        self.playerNode.scheduleBuffer(pcmBuffer, completionHandler: nil)
    }
    
    // Auto-switch mode on call start/end
    public func handleCallStateChanged(isActive: Bool) {
        if isActive {
            if isStreaming && (currentMode == .hybrid || currentMode == .call) {
                activateMicrophoneUplink()
            }
        } else {
            deactivateMicrophoneUplink()
        }
    }
}
