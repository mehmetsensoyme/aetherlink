import Foundation
import Combine

@MainActor
public final class PairingManager: ObservableObject {
    public static let shared = PairingManager()
    
    @Published public var currentConfirmationCode: String = ""
    @Published public var pairingPayloadUrl: String = ""
    @Published public var pendingPairingRequest: PairingRequestPayload? = nil
    @Published public var isPaired: Bool = false
    @Published public var pairedDeviceName: String = ""
    @Published public var isShowingQRModal: Bool = false
    
    private let pairedDeviceIdKey = "AetherLink_PairedDeviceId"
    private let pairedDeviceNameKey = "AetherLink_PairedDeviceName"
    
    public init() {
        loadPersistedPairing()
        generateNewConfirmationCode()
    }
    
    public func generateNewConfirmationCode() {
        let code = String(format: "%03d %03d", Int.random(in: 100...999), Int.random(in: 100...999))
        self.currentConfirmationCode = code
        
        let localIP = NetworkManager.shared.localIPAddress
        self.pairingPayloadUrl = "aetherlink://pair?ip=\(localIP)&port=8443&code=\(code.replacingOccurrences(of: " ", with: ""))&name=\(Host.current().localizedName ?? "MacBook")"
    }
    
    public func handleIncomingPairingRequest(_ payload: PairingRequestPayload) {
        // If already paired with this device id, automatically approve
        let savedId = UserDefaults.standard.string(forKey: pairedDeviceIdKey)
        if savedId == payload.deviceId {
            approvePairing(payload)
            return
        }
        
        // Otherwise, present approval prompt to user with confirmation code
        self.pendingPairingRequest = payload
    }
    
    public func approvePairing(_ request: PairingRequestPayload) {
        UserDefaults.standard.set(request.deviceId, forKey: pairedDeviceIdKey)
        UserDefaults.standard.set(request.deviceName, forKey: pairedDeviceNameKey)
        
        self.isPaired = true
        self.pairedDeviceName = request.deviceName
        self.pendingPairingRequest = nil
        
        let token = UUID().uuidString
        let response = PairingResponsePayload(
            approved: true,
            token: token,
            macName: Host.current().localizedName ?? "MacBook Pro",
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "PAIRING_RESPONSE", payload: response)
        print("[PairingManager] Pairing approved for \(request.deviceName) with code \(request.confirmationCode)")
    }
    
    public func rejectPairing(_ request: PairingRequestPayload) {
        self.pendingPairingRequest = nil
        let response = PairingResponsePayload(
            approved: false,
            token: nil,
            macName: Host.current().localizedName ?? "MacBook Pro",
            timestamp: Date().timeIntervalSince1970 * 1000
        )
        NetworkManager.shared.send(type: "PAIRING_RESPONSE", payload: response)
        print("[PairingManager] Pairing rejected for \(request.deviceName)")
    }
    
    public func unpair() {
        UserDefaults.standard.removeObject(forKey: pairedDeviceIdKey)
        UserDefaults.standard.removeObject(forKey: pairedDeviceNameKey)
        self.isPaired = false
        self.pairedDeviceName = ""
        generateNewConfirmationCode()
    }
    
    private func loadPersistedPairing() {
        if let deviceId = UserDefaults.standard.string(forKey: pairedDeviceIdKey),
           let name = UserDefaults.standard.string(forKey: pairedDeviceNameKey) {
            self.isPaired = true
            self.pairedDeviceName = name
            print("[PairingManager] Loaded persisted paired device: \(name) (\(deviceId))")
        }
    }
}
