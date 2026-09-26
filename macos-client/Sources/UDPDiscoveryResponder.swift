import Foundation

public final class UDPDiscoveryResponder: @unchecked Sendable {
    public static let shared = UDPDiscoveryResponder()
    private var isRunning = false
    private let queue = DispatchQueue(label: "org.aetherlink.udp", qos: .userInitiated)
    
    public init() {}
    
    public func start() {
        guard !isRunning else { return }
        isRunning = true
        queue.async { [weak self] in
            self?.runLoop()
        }
    }
    
    private func runLoop() {
        let fd = socket(AF_INET, SOCK_DGRAM, 0)
        guard fd >= 0 else {
            print("[UDPDiscoveryResponder] Failed to create socket")
            return
        }
        
        var opt: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &opt, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &opt, socklen_t(MemoryLayout<Int32>.size))
        
        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = in_port_t(8444).bigEndian
        addr.sin_addr.s_addr = in_addr_t(0) // INADDR_ANY
        
        let bindRes = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        
        guard bindRes == 0 else {
            print("[UDPDiscoveryResponder] Failed to bind to UDP port 8444 (errno: \(errno))")
            close(fd)
            return
        }
        
        print("[UDPDiscoveryResponder] Successfully listening on UDP 8444 for local broadcasts")
        
        var buffer = [UInt8](repeating: 0, count: 2048)
        while isRunning {
            var clientAddr = sockaddr_in()
            var clientAddrLen = socklen_t(MemoryLayout<sockaddr_in>.size)
            let bytesRead = withUnsafeMutablePointer(to: &clientAddr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                    recvfrom(fd, &buffer, buffer.count, 0, $0, &clientAddrLen)
                }
            }
            
            if bytesRead > 0 {
                let msg = String(decoding: buffer[..<bytesRead], as: UTF8.self)
                if msg.contains("AETHER_DISCOVER_MAC") {
                    Task { @MainActor in
                        let localIP = NetworkManager.shared.localIPAddress
                        let macName = Host.current().localizedName ?? "MacBook Pro"
                        let code = PairingManager.shared.currentConfirmationCode
                        
                        let responseJson: [String: Any] = [
                            "action": "AETHER_ANNOUNCE_MAC",
                            "macName": macName,
                            "ip": localIP,
                            "port": 8443,
                            "confirmationCode": code
                        ]
                        
                        if let resData = try? JSONSerialization.data(withJSONObject: responseJson) {
                            _ = resData.withUnsafeBytes { rawPtr in
                                withUnsafePointer(to: &clientAddr) {
                                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                                        sendto(fd, rawPtr.baseAddress, resData.count, 0, $0, clientAddrLen)
                                    }
                                }
                            }
                            print("[UDPDiscoveryResponder] Responded to discovery packet from Android (IP: \(localIP), Code: \(code))")
                        }
                    }
                }
            }
        }
        close(fd)
    }
}
