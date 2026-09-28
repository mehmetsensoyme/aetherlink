import AppKit
import Foundation
import SwiftUI

public struct NotchCapsuleShape: Shape {
    public var hasNotch: Bool
    public var bottomRadius: CGFloat = 18
    public var topRadius: CGFloat = 0
    
    public init(hasNotch: Bool, bottomRadius: CGFloat = 18, topRadius: CGFloat = 0) {
        self.hasNotch = hasNotch
        self.bottomRadius = bottomRadius
        self.topRadius = topRadius
    }
    
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        let tr = hasNotch ? topRadius : bottomRadius
        let tl = hasNotch ? topRadius : bottomRadius
        let bl = bottomRadius
        let br = bottomRadius
        
        path.move(to: CGPoint(x: rect.minX + tl, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - tr, y: rect.minY))
        if tr > 0 {
            path.addArc(center: CGPoint(x: rect.maxX - tr, y: rect.minY + tr), radius: tr, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        }
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - br))
        path.addArc(center: CGPoint(x: rect.maxX - br, y: rect.maxY - br), radius: br, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        path.addLine(to: CGPoint(x: rect.minX + bl, y: rect.maxY))
        path.addArc(center: CGPoint(x: rect.minX + bl, y: rect.maxY - bl), radius: bl, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + tl))
        if tl > 0 {
            path.addArc(center: CGPoint(x: rect.minX + tl, y: rect.minY + tl), radius: tl, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        }
        path.closeSubpath()
        return path
    }
}

public struct NotchCallView: View {
    @ObservedObject var manager = NotchCallManager.shared
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: 0) {
            ZStack {
                // Background Liquid Glass Capsule
                let shape = NotchCapsuleShape(
                    hasNotch: manager.isAttachedToNotch,
                    bottomRadius: 20,
                    topRadius: manager.isAttachedToNotch ? 0 : 20
                )
                
                ZStack {
                    Rectangle().fill(.ultraThinMaterial)
                    Color.black.opacity(0.92)
                }
                .clipShape(shape)
                .overlay(
                    shape.stroke(Color.white.opacity(0.14), lineWidth: 0.75)
                )
                .shadow(color: Color.black.opacity(0.45), radius: 12, x: 0, y: 6)
                
                // Content Layer with Notch Clearance
                VStack(spacing: 0) {
                    if manager.isAttachedToNotch {
                        Spacer()
                            .frame(height: manager.notchClearance)
                    }
                    
                    HStack(spacing: 12) {
                        // Left Side: Avatar / Pulsing Wave
                        leftAvatarSection
                        
                        // Center: Caller Info
                        centerInfoSection
                        
                        Spacer(minLength: 6)
                        
                        // Right Side: Interactive Buttons
                        rightActionSection
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, manager.isAttachedToNotch ? 8 : 0)
                    .frame(maxHeight: .infinity)
                }
                .opacity(manager.isExpanded ? 1.0 : 0.0)
            }
            .frame(
                width: manager.isExpanded ? 420 : (manager.isAttachedToNotch ? 200 : 160),
                height: manager.isExpanded
                    ? (manager.isAttachedToNotch ? (manager.notchClearance + 58) : 64)
                    : (manager.isAttachedToNotch ? manager.notchClearance : 28)
            )
            .animation(.spring(response: 0.45, dampingFraction: 0.75), value: manager.isExpanded)
            
            Spacer(minLength: 0)
        }
        .frame(width: 440, height: 104, alignment: .top)
    }
    
    // MARK: - Left Section (Wave / Avatar)
    private var leftAvatarSection: some View {
        ZStack {
            if !manager.isCallActive {
                // Pulsing wave ring for ringing state
                Circle()
                    .stroke(Color.green.opacity(manager.waveAnimation ? 0.0 : 0.65), lineWidth: 1.75)
                    .scaleEffect(manager.waveAnimation ? 1.35 : 0.9)
            }
            
            if let b64 = manager.avatarBase64,
               let data = Data(base64Encoded: b64),
               let nsImage = NSImage(data: data) {
                Image(nsImage: nsImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 36, height: 36)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
            } else {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: manager.isCallActive
                                ? [Color.green.opacity(0.8), Color.mint]
                                : [Color.green, Color.teal],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 34, height: 34)
                    .shadow(color: Color.green.opacity(0.35), radius: 4)
                
                Image(systemName: manager.isCallActive ? "waveform" : "phone.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
            }
        }
        .frame(width: 40, height: 40)
    }
    
    // MARK: - Center Section (Caller Name & Status)
    private var centerInfoSection: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(manager.callerName.isEmpty ? "Bilinmeyen Numara" : manager.callerName)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)
                .lineLimit(1)
                .truncationMode(.tail)
            
            if manager.isCallActive {
                HStack(spacing: 5) {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 6, height: 6)
                        .opacity(manager.waveAnimation ? 0.5 : 1.0)
                    Text(manager.formattedDuration)
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                        .foregroundColor(.green.opacity(0.95))
                    Text("• Görüşme Sürüyor")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                }
            } else {
                HStack(spacing: 4) {
                    let statusText = manager.isOutgoing ? "Aranıyor..." : (manager.phoneNumber.isEmpty ? "Gelen Arama..." : manager.phoneNumber)
                    Text(statusText)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.white.opacity(0.75))
                        .lineLimit(1)
                        .truncationMode(.tail)
                    
                    let subText = manager.isOutgoing ? "• Giden Arama" : "• \(manager.appType == .cellular ? "Hücresel" : manager.appType.rawValue.capitalized)"
                    Text(subText)
                        .font(.system(size: 10, weight: .regular))
                        .foregroundColor(.white.opacity(0.5))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    
    // MARK: - Right Section (Accept / Decline Buttons)
    private var rightActionSection: some View {
        HStack(spacing: 9) {
            if !manager.isCallActive {
                // Red Reject / Cancel Button
                Button(action: {
                    manager.rejectCall()
                }) {
                    ZStack {
                        Circle()
                            .fill(Color(nsColor: .systemRed).opacity(0.9))
                            .frame(width: 32, height: 32)
                            .shadow(color: Color.red.opacity(0.4), radius: 4)
                        Image(systemName: "phone.down.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                .buttonStyle(.plain)
                .help(manager.isOutgoing ? "Aramayı İptal Et" : "Aramayı Reddet")
                
                // Green Accept Button (Show ONLY for incoming calls!)
                if !manager.isOutgoing {
                    Button(action: {
                        manager.acceptCall()
                    }) {
                        ZStack {
                            Circle()
                                .fill(Color(nsColor: .systemGreen).opacity(0.9))
                                .frame(width: 32, height: 32)
                                .shadow(color: Color.green.opacity(0.4), radius: 4)
                            Image(systemName: "phone.fill")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                        }
                    }
                    .buttonStyle(.plain)
                    .help("Aramayı Yanıtla")
                }
            } else {
                // Red End Call Button
                Button(action: {
                    manager.endCall()
                }) {
                    ZStack {
                        Circle()
                            .fill(Color(nsColor: .systemRed).opacity(0.95))
                            .frame(width: 32, height: 32)
                            .shadow(color: Color.red.opacity(0.45), radius: 4)
                        Image(systemName: "phone.down.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                .buttonStyle(.plain)
                .help("Görüşmeyi Sonlandır")
            }
        }
    }
}
