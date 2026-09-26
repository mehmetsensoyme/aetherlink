import AppKit
import SwiftUI
import Combine

@MainActor
public final class ScreenMirrorManager: NSObject, ObservableObject {
    public static let shared = ScreenMirrorManager()
    
    @Published public var isScrcpyRunning: Bool = false
    @Published public var scrcpyPID: pid_t? = nil
    @Published public var lastErrorMessage: String? = nil
    
    private var scrcpyProcess: Process?
    
    public var embeddedScrcpyPath: String? {
        // Look inside App Bundle Resources
        if let resURL = Bundle.main.resourceURL {
            let embeddedBin = resURL.appendingPathComponent("bin/scrcpy").path
            if FileManager.default.isExecutableFile(atPath: embeddedBin) {
                return embeddedBin
            }
        }
        // Fallback relative to executable
        let execURL = Bundle.main.executableURL?.deletingLastPathComponent()
        if let bin = execURL?.appendingPathComponent("../Resources/bin/scrcpy").path,
           FileManager.default.isExecutableFile(atPath: bin) {
            return bin
        }
        return nil
    }
    
    public var embeddedAdbPath: String? {
        if let resURL = Bundle.main.resourceURL {
            let embeddedBin = resURL.appendingPathComponent("bin/adb").path
            if FileManager.default.isExecutableFile(atPath: embeddedBin) {
                return embeddedBin
            }
        }
        let execURL = Bundle.main.executableURL?.deletingLastPathComponent()
        if let bin = execURL?.appendingPathComponent("../Resources/bin/adb").path,
           FileManager.default.isExecutableFile(atPath: bin) {
            return bin
        }
        return nil
    }
    
    public var hasScrcpyInstalled: Bool {
        if embeddedScrcpyPath != nil { return true }
        let paths = ["/opt/homebrew/bin/scrcpy", "/usr/local/bin/scrcpy", "/usr/bin/scrcpy"]
        return paths.contains { FileManager.default.isExecutableFile(atPath: $0) }
    }
    
    public override init() {
        super.init()
    }
    
    // MARK: - Direct Scrcpy Mirroring Process Launch
    public func startMirroring(wirelessIp: String? = nil) {
        if isScrcpyRunning {
            terminateScrcpy()
            return
        }
        
        let paths = [
            embeddedScrcpyPath,
            "/opt/homebrew/bin/scrcpy",
            "/usr/local/bin/scrcpy",
            "/usr/bin/scrcpy"
        ].compactMap { $0 }
        
        guard let binPath = paths.first(where: { FileManager.default.isExecutableFile(atPath: $0) }) else {
            print("[ScreenMirrorManager] Error: scrcpy binary not found in system or bundle!")
            self.lastErrorMessage = "scrcpy ikili dosyası bulunamadı."
            return
        }
        
        // Determine target device IP or active adb wireless connection
        let targetIP = wirelessIp ?? NetworkManager.shared.connectedDeviceIP ?? detectWirelessOrAdbIP()
        
        let p = Process()
        p.executableURL = URL(fileURLWithPath: binPath)
        
        // Setup bundled environment for dynamic libraries and server
        var env = ProcessInfo.processInfo.environment
        if let resURL = Bundle.main.resourceURL {
            let bundledServer = resURL.appendingPathComponent("share/scrcpy/scrcpy-server").path
            if FileManager.default.fileExists(atPath: bundledServer) {
                env["SCRCPY_SERVER_PATH"] = bundledServer
            }
            let bundledBinDir = resURL.appendingPathComponent("bin").path
            let bundledLibDir = resURL.appendingPathComponent("lib").path
            let currentPath = env["PATH"] ?? ""
            env["PATH"] = "\(bundledBinDir):/opt/homebrew/bin:/usr/local/bin:\(currentPath)"
            env["DYLD_FALLBACK_LIBRARY_PATH"] = bundledLibDir
        }
        p.environment = env
        
        let deviceTitle = NetworkManager.shared.connectedDeviceName.isEmpty || NetworkManager.shared.connectedDeviceName == "Bağlantı Kesildi"
            ? "Android Cihazı"
            : NetworkManager.shared.connectedDeviceName
            
        // Scrcpy arguments optimized for wireless low-latency, 60fps, Opus audio & device playback suppression:
        // - Video bit rate: 8M
        // - Frame rate: 60 fps
        // - Video codec: h264
        // - Audio codec: opus
        // - Audio buffer: 50 ms
        // - Audio playback suppression on device: --audio-source=output (and --no-audio-playback-on-device if supported)
        var args = [
            "--video-bit-rate=8M",
            "--max-fps=60",
            "--video-codec=h264",
            "--audio-codec=opus",
            "--audio-buffer=50",
            "--audio-source=output",
            "--always-on-top",
            "--window-title=\(deviceTitle) (AetherLink)"
        ]
        
        // Check if scrcpy binary supports the explicit --no-audio-playback-on-device flag
        if checkFlagSupport("--no-audio-playback-on-device", binPath: binPath) {
            args.append("--no-audio-playback-on-device")
        }
        
        if let ip = targetIP, !ip.isEmpty, ip != "127.0.0.1" {
            args.append("--tcpip=\(ip):5555")
        }
        
        p.arguments = args
        
        p.terminationHandler = { [weak self] proc in
            Task { @MainActor in
                print("[ScreenMirrorManager] scrcpy process (PID: \(proc.processIdentifier)) terminated with status: \(proc.terminationStatus)")
                self?.isScrcpyRunning = false
                self?.scrcpyProcess = nil
                self?.scrcpyPID = nil
            }
        }
        
        do {
            try p.run()
            self.scrcpyProcess = p
            self.scrcpyPID = p.processIdentifier
            self.isScrcpyRunning = true
            self.lastErrorMessage = nil
            print("[ScreenMirrorManager] scrcpy mirroring started successfully with PID: \(p.processIdentifier) (Device: \(targetIP ?? "default"))")
        } catch {
            print("[ScreenMirrorManager] Failed to launch scrcpy: \(error)")
            self.lastErrorMessage = error.localizedDescription
            self.isScrcpyRunning = false
            self.scrcpyProcess = nil
            self.scrcpyPID = nil
        }
    }
    
    // MARK: - Lifecycle Management & Process Termination
    public func stopMirroring() {
        terminateScrcpy()
    }
    
    public func terminateScrcpy() {
        guard let p = scrcpyProcess else {
            // Cleanup any orphaned scrcpy processes associated with AetherLink
            cleanupOrphanedProcesses()
            self.isScrcpyRunning = false
            self.scrcpyPID = nil
            return
        }
        
        let pid = p.processIdentifier
        print("[ScreenMirrorManager] Terminating scrcpy process (PID: \(pid))...")
        p.terminate()
        
        // Verify and force kill if still running after brief grace period
        DispatchQueue.global(qos: .userInitiated).asyncAfter(deadline: .now() + 0.5) {
            if kill(pid, 0) == 0 {
                print("[ScreenMirrorManager] Force killing scrcpy process (PID: \(pid)) with SIGKILL...")
                kill(pid, SIGKILL)
            }
        }
        
        self.scrcpyProcess = nil
        self.scrcpyPID = nil
        self.isScrcpyRunning = false
        
        cleanupOrphanedProcesses()
    }
    
    public nonisolated func terminateScrcpySync() {
        // Synchronous kill on app exit
        let pkill = Process()
        pkill.executableURL = URL(fileURLWithPath: "/usr/bin/pkill")
        pkill.arguments = ["-9", "-f", "scrcpy.*AetherLink"]
        try? pkill.run()
        pkill.waitUntilExit()
    }
    
    private func cleanupOrphanedProcesses() {
        let pkill = Process()
        pkill.executableURL = URL(fileURLWithPath: "/usr/bin/pkill")
        pkill.arguments = ["-f", "scrcpy.*AetherLink"]
        try? pkill.run()
    }
    
    private func checkFlagSupport(_ flag: String, binPath: String) -> Bool {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: binPath)
        p.arguments = ["--help"]
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = pipe
        do {
            try p.run()
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            let out = String(data: data, encoding: .utf8) ?? ""
            return out.contains(flag)
        } catch {
            return false
        }
    }
    
    private func detectWirelessOrAdbIP() -> String? {
        let adbPaths = [
            embeddedAdbPath,
            "/opt/homebrew/bin/adb",
            "/usr/local/bin/adb",
            "/usr/bin/adb"
        ].compactMap { $0 }
        
        guard let adb = adbPaths.first(where: { FileManager.default.isExecutableFile(atPath: $0) }) else {
            return nil
        }
        
        let p = Process()
        p.executableURL = URL(fileURLWithPath: adb)
        p.arguments = ["devices"]
        let pipe = Pipe()
        p.standardOutput = pipe
        do {
            try p.run()
            p.waitUntilExit()
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            let output = String(data: data, encoding: .utf8) ?? ""
            for line in output.components(separatedBy: .newlines) {
                let parts = line.split(separator: "\t")
                if parts.count >= 2 && parts[1] == "device" {
                    let serial = String(parts[0])
                    if serial.contains(":") {
                        return serial.components(separatedBy: ":").first
                    }
                }
            }
        } catch {
            print("[ScreenMirrorManager] Could not query adb devices: \(error)")
        }
        return nil
    }
}
