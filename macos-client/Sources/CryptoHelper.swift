import Foundation
import CryptoKit

public enum CryptoHelper {
    public static func sha256(_ string: String) -> String {
        guard let data = string.data(using: .utf8) else { return "" }
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02hhx", $0) }.joined()
    }
    
    public static func sha256(data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02hhx", $0) }.joined()
    }
}
