import Foundation

/// Utility to dynamically resolve raw manufacturer model codes (e.g. "SM-S938B")
/// to clean, consumer-facing market names (e.g. "Samsung Galaxy S25 Ultra").
public enum DeviceMarketingNameResolver {
    
    private static let modelMap: [String: (brand: String, marketName: String)] = [
        // Samsung Galaxy S25 Series
        "SM-S938": ("Samsung", "Galaxy S25 Ultra"),
        "SM-S936": ("Samsung", "Galaxy S25+"),
        "SM-S931": ("Samsung", "Galaxy S25"),
        
        // Samsung Galaxy S24 Series
        "SM-S928": ("Samsung", "Galaxy S24 Ultra"),
        "SM-S926": ("Samsung", "Galaxy S24+"),
        "SM-S921": ("Samsung", "Galaxy S24"),
        
        // Samsung Galaxy S23 Series
        "SM-S918": ("Samsung", "Galaxy S23 Ultra"),
        "SM-S916": ("Samsung", "Galaxy S23+"),
        "SM-S911": ("Samsung", "Galaxy S23"),
        
        // Samsung Galaxy S22 Series
        "SM-S908": ("Samsung", "Galaxy S22 Ultra"),
        "SM-S906": ("Samsung", "Galaxy S22+"),
        "SM-S901": ("Samsung", "Galaxy S22"),
        
        // Samsung Galaxy S21 Series
        "SM-G998": ("Samsung", "Galaxy S21 Ultra"),
        "SM-G996": ("Samsung", "Galaxy S21+"),
        "SM-G991": ("Samsung", "Galaxy S21"),
        "SM-G990": ("Samsung", "Galaxy S21 FE"),
        
        // Samsung Galaxy Z Fold & Flip
        "SM-F956": ("Samsung", "Galaxy Z Fold 6"),
        "SM-F741": ("Samsung", "Galaxy Z Flip 6"),
        "SM-F946": ("Samsung", "Galaxy Z Fold 5"),
        "SM-F731": ("Samsung", "Galaxy Z Flip 5"),
        "SM-F936": ("Samsung", "Galaxy Z Fold 4"),
        "SM-F721": ("Samsung", "Galaxy Z Flip 4"),
        
        // Samsung Galaxy A Series
        "SM-A556": ("Samsung", "Galaxy A55"),
        "SM-A546": ("Samsung", "Galaxy A54"),
        "SM-A356": ("Samsung", "Galaxy A35"),
        "SM-A346": ("Samsung", "Galaxy A34"),
        "SM-A156": ("Samsung", "Galaxy A15"),
        "SM-A146": ("Samsung", "Galaxy A14"),
        
        // Google Pixel
        "Pixel 9 Pro XL": ("Google", "Pixel 9 Pro XL"),
        "Pixel 9 Pro Fold": ("Google", "Pixel 9 Pro Fold"),
        "Pixel 9 Pro": ("Google", "Pixel 9 Pro"),
        "Pixel 9": ("Google", "Pixel 9"),
        "Pixel 8 Pro": ("Google", "Pixel 8 Pro"),
        "Pixel 8": ("Google", "Pixel 8"),
        "Pixel 8a": ("Google", "Pixel 8a"),
        "Pixel 7 Pro": ("Google", "Pixel 7 Pro"),
        "Pixel 7": ("Google", "Pixel 7"),
        "Pixel 7a": ("Google", "Pixel 7a"),
        
        // Xiaomi
        "24030PN60G": ("Xiaomi", "14 Ultra"),
        "23127PN0CG": ("Xiaomi", "14"),
        "23049PCD8G": ("POCO", "F5 Pro"),
        "2311DRK48G": ("POCO", "X6 Pro"),
        
        // OnePlus
        "CPH2581": ("OnePlus", "12"),
        "CPH2609": ("OnePlus", "12R"),
        "CPH2449": ("OnePlus", "11")
    ]
    
    public static func resolve(_ rawInput: String) -> String {
        let trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "Bağlı Android Cihazı" }
        
        if trimmed == "Bağlı Cihaz Yok" || trimmed == "Cihaz Aranıyor..." || trimmed == "Bağlantı Kesildi" {
            return trimmed
        }
        
        // Check if rawInput starts with a known brand prefix like "Samsung "
        var searchKey = trimmed
        var detectedBrand: String? = nil
        
        for brand in ["Samsung", "Google", "Xiaomi", "OnePlus", "POCO", "Redmi", "Huawei", "Oppo", "Vivo", "Sony"] {
            if trimmed.lowercased().starts(with: brand.lowercased()) {
                detectedBrand = brand
                let remainder = trimmed.dropFirst(brand.count).trimmingCharacters(in: .whitespaces)
                searchKey = remainder
                break
            }
        }
        
        // 1. Direct match on searchKey or trimmed
        for (pattern, match) in modelMap {
            if searchKey.uppercased().starts(with: pattern.uppercased()) || trimmed.uppercased().starts(with: pattern.uppercased()) {
                return "\(match.brand) \(match.marketName)"
            }
        }
        
        // 2. If the string already has a friendly market name (e.g. contains "Galaxy", "Pixel", etc.)
        if trimmed.localizedCaseInsensitiveContains("Galaxy") ||
           trimmed.localizedCaseInsensitiveContains("Pixel") ||
           trimmed.localizedCaseInsensitiveContains("Ultra") ||
           trimmed.localizedCaseInsensitiveContains("Fold") ||
           trimmed.localizedCaseInsensitiveContains("Flip") {
            return trimmed
        }
        
        // 3. Fallback: return trimmed with brand if missing
        if let brand = detectedBrand, !searchKey.isEmpty {
            return "\(brand) \(searchKey)"
        }
        
        return trimmed
    }
}
