import Foundation
import AVFoundation
import AppKit

/// CallAudioStreamEngine manages ultra-low latency real-time voice streaming
/// between Android cellular phone calls and MacBook Pro speakers / microphone.
public final class CallAudioStreamEngine: ObservableObject {
    public static let shared = CallAudioStreamEngine()
    
    @Published public var isStreaming: Bool = false
    @Published public var isMuted: Bool = false
    @Published public var volume: Float = 1.0 {
        didSet {
            playerNode.volume = volume
        }
    }
    
    private let engine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private var audioFormat: AVAudioFormat?
    
    // UDP Socket for incoming phone call audio (Port 8444)
    private var rxSocketFd: Int32 = -1
    private var rxThread: Thread?
    private var isRxRunning: BooleanLiteralType = false
    
    // UDP Socket for outgoing Mac microphone audio (Port 8445)
    private var txSocketFd: Int32 = -1
    private var targetPhoneIp: String?
    private var targetPhonePort: UInt16 = 8445
    private var micSequenceNumber: UInt32 = 0
    
    private let sampleRate: Double = 16000.0
    private let packetMagic: UInt16 = 0xAECA
    
    private init() {
        setupAudioEngine()
    }
    
    private func setupAudioEngine() {
        audioFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: sampleRate, channels: 1, interleaved: false)
        guard let format = audioFormat else { return }
        
        engine.attach(playerNode)
        engine.connect(playerNode, to: engine.mainMixerNode, format: format)
        playerNode.volume = 1.0
    }
    
    // MARK: - Start Streaming
    public func start(phoneIp: String, incomingPort: UInt16 = 8444, outgoingMicPort: UInt16 = 8445) {
        // Disabled to prevent double audio playback, distortion, and acoustic feedback loops.
        // Cellular call audio is handled natively by the phone's hardware hands-free speakerphone or Bluetooth headset.
        print("[CallAudioStreamEngine] Audio relay disabled to preserve native hardware call audio quality.")
    }
    
    // MARK: - Stop Streaming
    public func stop() {
        guard isStreaming else { return }
        print("[CallAudioStreamEngine] Stopping call audio relay...")
        
        isRxRunning = false
        
        // Close RX socket
        if rxSocketFd >= 0 {
            close(rxSocketFd)
            rxSocketFd = -1
        }
        
        // Close TX socket
        if txSocketFd >= 0 {
            close(txSocketFd)
            txSocketFd = -1
        }
        
        // Stop Mic Tap
        engine.inputNode.removeTap(onBus: 0)
        
        // Stop Audio Engine
        playerNode.stop()
        if engine.isRunning {
            engine.stop()
        }
        
        DispatchQueue.main.async { [weak self] in
            self?.isStreaming = false
        }
        print("[CallAudioStreamEngine] Audio relay stopped cleanly.")
    }
    
    // MARK: - UDP RX (Phone -> Mac Speaker)
    private func startUdpReceiver(port: UInt16) {
        rxSocketFd = socket(AF_INET, SOCK_DGRAM, 0)
        guard rxSocketFd >= 0 else {
            print("[CallAudioStreamEngine] Failed to create RX UDP socket")
            return
        }
        
        // Enable SO_REUSEADDR
        var reuse: Int32 = 1
        setsockopt(rxSocketFd, SOL_SOCKET, SO_REUSEADDR, &reuse, socklen_t(MemoryLayout<Int32>.size))
        
        var serverAddr = sockaddr_in()
        serverAddr.sin_family = sa_family_t(AF_INET)
        serverAddr.sin_port = port.bigEndian
        serverAddr.sin_addr.s_addr = in_addr_t(0) // INADDR_ANY
        
        let bindResult = withUnsafePointer(to: &serverAddr) { ptr in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saPtr in
                bind(rxSocketFd, saPtr, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        
        guard bindResult == 0 else {
            print("[CallAudioStreamEngine] Failed to bind RX UDP socket to port \(port), errno: \(errno)")
            close(rxSocketFd)
            rxSocketFd = -1
            return
        }
        
        isRxRunning = true
        let thread = Thread { [weak self] in
            self?.runRxLoop()
        }
        thread.name = "org.aetherlink.callaudio.rx"
        thread.qualityOfService = .userInteractive
        thread.start()
        self.rxThread = thread
        print("[CallAudioStreamEngine] RX UDP Listener active on port \(port)")
    }
    
    private func runRxLoop() {
        var buffer = [UInt8](repeating: 0, count: 2048)
        while isRxRunning && rxSocketFd >= 0 {
            var clientAddr = sockaddr_in()
            var clientAddrLen = socklen_t(MemoryLayout<sockaddr_in>.size)
            
            let bytesRead = withUnsafeMutablePointer(to: &clientAddr) { ptr in
                ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saPtr in
                    recvfrom(rxSocketFd, &buffer, buffer.count, 0, saPtr, &clientAddrLen)
                }
            }
            
            guard bytesRead > 0 else {
                if !isRxRunning { break }
                continue
            }
            
            processIncomingPcmPacket(data: buffer, length: bytesRead)
        }
        print("[CallAudioStreamEngine] RX Loop exited")
    }
    
    private func processIncomingPcmPacket(data: [UInt8], length: Int) {
        guard length >= 8 else { return }
        
        // Header: [0, 1] Magic (0xAECA), [2..5] Sequence (UInt32), [6, 7] PayloadLen (UInt16)
        let magic = (UInt16(data[0]) << 8) | UInt16(data[1])
        guard magic == packetMagic else { return }
        
        let pcmByteCount = length - 8
        guard pcmByteCount > 0, pcmByteCount % 2 == 0 else { return }
        
        let sampleCount = pcmByteCount / 2
        guard let format = self.audioFormat,
              let pcmBuffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(sampleCount)) else {
            return
        }
        
        pcmBuffer.frameLength = AVAudioFrameCount(sampleCount)
        guard let channelData = pcmBuffer.floatChannelData?[0] else { return }
        
        // Convert 16-bit signed PCM to Float32 (-1.0 ... 1.0)
        data.withUnsafeBytes { rawBuffer in
            guard let baseAddress = rawBuffer.baseAddress else { return }
            let int16Ptr = baseAddress.advanced(by: 8).bindMemory(to: Int16.self, capacity: sampleCount)
            for i in 0..<sampleCount {
                let sample = Int16(littleEndian: int16Ptr[i])
                channelData[i] = Float(sample) / 32768.0
            }
        }
        
        // Schedule buffer immediately with no delay
        playerNode.scheduleBuffer(pcmBuffer, completionHandler: nil)
    }
    
    // MARK: - Mac Microphone -> Phone (Port 8445)
    private func startMicrophoneCapture() {
        guard let phoneIp = targetPhoneIp, !phoneIp.isEmpty else { return }
        
        txSocketFd = socket(AF_INET, SOCK_DGRAM, 0)
        guard txSocketFd >= 0 else {
            print("[CallAudioStreamEngine] Failed to create TX UDP socket")
            return
        }
        
        let inputNode = engine.inputNode
        let nativeFormat = inputNode.outputFormat(forBus: 0)
        guard nativeFormat.sampleRate > 0 else { return }
        
        print("[CallAudioStreamEngine] Installing tap on microphone bus 0 (\(nativeFormat.sampleRate) Hz)...")
        
        micSequenceNumber = 0
        let targetRate = self.sampleRate // 16000 Hz
        
        // 20ms buffer size request
        let bufferSize = AVAudioFrameCount(nativeFormat.sampleRate * 0.02)
        
        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: bufferSize, format: nativeFormat) { [weak self] (buffer, when) in
            guard let self = self, self.isStreaming, !self.isMuted, self.txSocketFd >= 0 else { return }
            self.sendMicBuffer(buffer: buffer, nativeRate: nativeFormat.sampleRate, targetRate: targetRate)
        }
    }
    
    private func sendMicBuffer(buffer: AVAudioPCMBuffer, nativeRate: Double, targetRate: Double) {
        guard let channelData = buffer.floatChannelData?[0] else { return }
        let frameCount = Int(buffer.frameLength)
        guard frameCount > 0 else { return }
        
        // Simple downsampler / resampler to 16kHz
        let step = nativeRate / targetRate
        let outSampleCount = Int(Double(frameCount) / step)
        guard outSampleCount > 0 else { return }
        
        let pcmByteCount = outSampleCount * 2
        var packet = [UInt8](repeating: 0, count: 8 + pcmByteCount)
        
        // Header
        packet[0] = UInt8(packetMagic >> 8)
        packet[1] = UInt8(packetMagic & 0xFF)
        
        let seq = micSequenceNumber
        micSequenceNumber &+= 1
        packet[2] = UInt8((seq >> 24) & 0xFF)
        packet[3] = UInt8((seq >> 16) & 0xFF)
        packet[4] = UInt8((seq >> 8) & 0xFF)
        packet[5] = UInt8(seq & 0xFF)
        
        packet[6] = UInt8((UInt16(pcmByteCount) >> 8) & 0xFF)
        packet[7] = UInt8(UInt16(pcmByteCount) & 0xFF)
        
        // Float32 -> Int16 PCM
        for i in 0..<outSampleCount {
            let inIndex = min(Int(Double(i) * step), frameCount - 1)
            let floatSample = max(-1.0, min(1.0, channelData[inIndex]))
            let int16Sample = Int16(floatSample * 32767.0)
            
            let byteIndex = 8 + (i * 2)
            packet[byteIndex] = UInt8(int16Sample & 0xFF)
            packet[byteIndex + 1] = UInt8((int16Sample >> 8) & 0xFF)
        }
        
        // Send UDP packet to Phone
        guard let phoneIp = self.targetPhoneIp else { return }
        var targetAddr = sockaddr_in()
        targetAddr.sin_family = sa_family_t(AF_INET)
        targetAddr.sin_port = targetPhonePort.bigEndian
        inet_pton(AF_INET, phoneIp, &targetAddr.sin_addr)
        
        withUnsafePointer(to: &targetAddr) { ptr in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saPtr in
                _ = sendto(txSocketFd, packet, packet.count, 0, saPtr, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
    }
}
