import AppKit
import Foundation
import SwiftUI

@MainActor
public final class RemoteVolumeManager: ObservableObject {
    public static let shared = RemoteVolumeManager()
    
    @Published public var macVolume: Int = 50
    @Published public var phoneVolume: Int = 50
    @Published public var isMacMuted: Bool = false
    @Published public var isPhoneMuted: Bool = false
    
    private var pollTimer: Timer?
    
    public init() {
        refreshLocalMacVolume()
    }
    
    // MARK: - Mac Volume Control
    
    public func refreshLocalMacVolume() {
        if let script = NSAppleScript(source: "output volume of (get volume settings)") {
            var error: NSDictionary?
            let descriptor = script.executeAndReturnError(&error)
            if error == nil {
                let vol = Int(descriptor.int32Value)
                self.macVolume = vol
            }
        }
        
        if let scriptMuted = NSAppleScript(source: "output muted of (get volume settings)") {
            var error: NSDictionary?
            let descMuted = scriptMuted.executeAndReturnError(&error)
            if error == nil {
                self.isMacMuted = descMuted.booleanValue
            }
        }
    }
    
    public func setMacVolume(_ volume: Int) {
        let clamped = max(0, min(100, volume))
        self.macVolume = clamped
        
        let src = "set volume output volume \(clamped)"
        if let script = NSAppleScript(source: src) {
            var error: NSDictionary?
            script.executeAndReturnError(&error)
        }
        
        // Broadcast to phone
        broadcastMacVolume()
    }
    
    public func toggleMacMute() {
        let newMute = !isMacMuted
        self.isMacMuted = newMute
        let src = "set volume output muted \(newMute)"
        if let script = NSAppleScript(source: src) {
            var error: NSDictionary?
            script.executeAndReturnError(&error)
        }
        broadcastMacVolume()
    }
    
    public func broadcastMacVolume() {
        let payload = SystemVolumePayload(volume: self.macVolume, isMuted: self.isMacMuted, stream: "master")
        NetworkManager.shared.send(type: "MAC_VOLUME_UPDATE", payload: payload)
    }
    
    public func handleRemoteSetMacVolume(_ payload: SystemVolumePayload) {
        setMacVolume(payload.volume)
        if let muted = payload.isMuted {
            if muted != self.isMacMuted {
                toggleMacMute()
            }
        }
    }
    
    // MARK: - Phone Volume Control (Mac -> Android)
    
    public func setPhoneVolume(_ volume: Int) {
        let clamped = max(0, min(100, volume))
        self.phoneVolume = clamped
        
        let payload = SystemVolumePayload(volume: clamped, isMuted: self.isPhoneMuted, stream: "media")
        NetworkManager.shared.send(type: "SET_PHONE_VOLUME", payload: payload)
        print("[RemoteVolumeManager] Sent SET_PHONE_VOLUME: \(clamped)%")
    }
    
    public func handleIncomingPhoneVolumeUpdate(_ payload: SystemVolumePayload) {
        self.phoneVolume = payload.volume
        if let muted = payload.isMuted {
            self.isPhoneMuted = muted
        }
        print("[RemoteVolumeManager] Updated phone volume: \(payload.volume)%")
    }
}
