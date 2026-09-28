import AppKit
import Foundation

@MainActor
public final class RemoteLockManager: ObservableObject {
    public static let shared = RemoteLockManager()
    
    @Published public var isMacLocked: Bool = false
    @Published public var lastLockStatusMessage: String? = nil
    
    public init() {}
    
    // MARK: - Lock Local Mac
    
    public func lockMac() {
        print("[RemoteLockManager] Locking Mac screen immediately...")
        
        typealias SACLockScreenImmediateType = @convention(c) () -> Int32
        var lockedViaFramework = false
        
        if let handle = dlopen("/System/Library/PrivateFrameworks/login.framework/Versions/Current/login", RTLD_LAZY) {
            if let sym = dlsym(handle, "SACLockScreenImmediate") {
                let lockFunc = unsafeBitCast(sym, to: SACLockScreenImmediateType.self)
                let result = lockFunc()
                lockedViaFramework = (result == 0)
                print("[RemoteLockManager] SACLockScreenImmediate result: \(result)")
            }
            dlclose(handle)
        }
        
        if !lockedViaFramework {
            print("[RemoteLockManager] Fallback to pmset displaysleepnow...")
            let proc = Process()
            proc.executableURL = URL(fileURLWithPath: "/usr/bin/pmset")
            proc.arguments = ["displaysleepnow"]
            try? proc.run()
        }
        
        self.isMacLocked = true
        let response = ["status": "success", "message": "Mac ekranı kilitlendi", "timestamp": "\(Date().timeIntervalSince1970 * 1000)"]
        NetworkManager.shared.send(type: "LOCK_MAC_RESULT", payload: response)
    }
    
    // MARK: - Lock Remote Phone (Mac -> Android)
    
    public func lockPhone() {
        print("[RemoteLockManager] Requesting phone lock...")
        let payload = ["action": "lock", "sourceDevice": "macos", "timestamp": "\(Date().timeIntervalSince1970 * 1000)"]
        NetworkManager.shared.send(type: "LOCK_PHONE", payload: payload)
    }
    
    public func handleIncomingLockMacRequest() {
        lockMac()
    }
    
    public func handleIncomingLockPhoneResult(_ dict: [String: Any]) {
        let msg = dict["message"] as? String ?? "Telefon ekranı kilitlendi"
        print("[RemoteLockManager] Lock Phone Result: \(msg)")
        self.lastLockStatusMessage = msg
    }
}
