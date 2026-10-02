import AppKit
import AVFoundation
import SwiftUI
@preconcurrency import UserNotifications

@MainActor
public final class OnboardingViewModel: ObservableObject {
    @Published public var hasNotificationPermission: Bool = false
    @Published public var hasMicrophonePermission: Bool = false
    
    public init() {
        checkPermissions()
    }
    
    public func checkPermissions() {
        if Bundle.main.bundleIdentifier != nil {
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                let isAuth = (settings.authorizationStatus == .authorized)
                DispatchQueue.main.async {
                    self.hasNotificationPermission = isAuth
                }
            }
        }
        let micStatus = AVCaptureDevice.authorizationStatus(for: .audio)
        self.hasMicrophonePermission = (micStatus == .authorized)
    }
    
    public func requestNotificationAccess() {
        guard Bundle.main.bundleIdentifier != nil else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            DispatchQueue.main.async {
                self.hasNotificationPermission = granted
            }
        }
    }
    
    public func requestMicrophoneAccess() {
        AVCaptureDevice.requestAccess(for: .audio) { granted in
            DispatchQueue.main.async {
                self.hasMicrophonePermission = granted
            }
        }
    }
}

public struct OnboardingView: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var vm = OnboardingViewModel()
    
    var onComplete: () -> Void
    
    public init(onComplete: @escaping () -> Void = {}) {
        self.onComplete = onComplete
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // Header Hero
            VStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Color(nsColor: .controlAccentColor).opacity(0.35), Color.purple.opacity(0.2)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 72, height: 72)
                        .shadow(color: Color(nsColor: .controlAccentColor).opacity(0.3), radius: 10)
                    
                    Image(systemName: "bolt.horizontal.circle.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [Color.white, Color(nsColor: .controlAccentColor)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                }
                .padding(.top, 24)
                
                Text("AetherLink'e Hoş Geldiniz")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundColor(.primary)
                
                Text("Android telefonunuz ile Mac'iniz arasındaki sıfır-gecikmeli süreklilik köprüsü.")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
            .padding(.bottom, 20)
            
            Divider()
                .opacity(0.4)
            
            // Permissions & Features Checklist
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 12) {
                    // 1. Yerel Ağ (Local Network)
                    permissionRow(
                        icon: "network",
                        iconColor: .blue,
                        title: "Yerel Ağ Keşfi (mDNS & UDP)",
                        description: "Telefonunuzun aynı Wi-Fi ağında sıfır konfigürasyon ile otomatik bulunmasını sağlar.",
                        isGranted: true,
                        actionTitle: "Otomatik Aktif",
                        action: nil
                    )
                    
                    // 2. Bildirimler (Notifications)
                    permissionRow(
                        icon: "bell.badge.fill",
                        iconColor: .red,
                        title: "Sistem Bildirimleri",
                        description: "Gelen arama ve mesaj bildirimlerini Mac ekranında anında görebilmeniz için.",
                        isGranted: vm.hasNotificationPermission,
                        actionTitle: vm.hasNotificationPermission ? "Yetkilendirildi" : "İzin Ver",
                        action: {
                            vm.requestNotificationAccess()
                        }
                    )
                    
                    // 3. Mikrofon (Microphone)
                    permissionRow(
                        icon: "mic.fill",
                        iconColor: .purple,
                        title: "Mikrofon Erişimi",
                        description: "Gelen ve giden aramalarda Mac mikrofonunuz üzerinden konuşabilmeniz için gereklidir.",
                        isGranted: vm.hasMicrophonePermission,
                        actionTitle: vm.hasMicrophonePermission ? "Yetkilendirildi" : "İzin Ver",
                        action: {
                            vm.requestMicrophoneAccess()
                        }
                    )
                    
                    // 4. Evrensel Pano (Universal Clipboard)
                    permissionRow(
                        icon: "doc.on.clipboard.fill",
                        iconColor: .green,
                        title: "Evrensel Pano Senkronizasyonu",
                        description: "Mac veya telefonda kopyaladığınız metinleri anında diğer cihaza taşır.",
                        isGranted: true,
                        actionTitle: "Dahili Aktif",
                        action: nil
                    )
                    
                    // 5. Bildirim Merkezi Widget'ı
                    permissionRow(
                        icon: "square.text.square.fill",
                        iconColor: .cyan,
                        title: "macOS Bildirim Merkezi Widget'ı",
                        description: "Telefon pil durumunu Bildirim Merkezi'nde canlı Widget olarak görüntüleyebilirsiniz.",
                        isGranted: true,
                        actionTitle: "Widget Eklendi",
                        action: nil
                    )
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .frame(maxHeight: 280)
            
            Divider()
                .opacity(0.4)
            
            // Footer Action Bar
            HStack {
                Button(action: {
                    if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility") {
                        NSWorkspace.shared.open(url)
                    }
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "gearshape")
                            .font(.system(size: 11))
                        Text("Sistem Ayarları")
                            .font(.system(size: 12))
                    }
                    .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
                
                Spacer()
                
                Button(action: {
                    UserDefaults.standard.set(true, forKey: "aetherlink_has_completed_onboarding")
                    onComplete()
                }) {
                    Text("Kullanmaya Başla")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color(nsColor: .controlAccentColor))
                        )
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.defaultAction)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .background(Color.primary.opacity(0.02))
        }
        .frame(width: 540, height: 480)
        .background(.ultraThinMaterial)
        .onAppear {
            vm.checkPermissions()
        }
    }
    
    private func permissionRow(
        icon: String,
        iconColor: Color,
        title: String,
        description: String,
        isGranted: Bool,
        actionTitle: String,
        action: (() -> Void)?
    ) -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(iconColor.opacity(colorScheme == .dark ? 0.22 : 0.12))
                    .frame(width: 34, height: 34)
                
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(iconColor)
            }
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundColor(.primary)
                
                Text(description)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
            
            Spacer(minLength: 8)
            
            if isGranted {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                        .font(.system(size: 14))
                    Text(actionTitle)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.secondary)
                }
            } else if let act = action {
                Button(action: act) {
                    Text(actionTitle)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(Color(nsColor: .controlAccentColor))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(
                            Capsule()
                                .fill(Color(nsColor: .controlAccentColor).opacity(0.12))
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(colorScheme == .dark ? 0.05 : 0.40))
        )
    }
}
