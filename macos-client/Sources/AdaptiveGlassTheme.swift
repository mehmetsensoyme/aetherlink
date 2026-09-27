import AppKit
import SwiftUI

// MARK: - Popover & UI State Manager (Avoids @State Macro Dependencies)
@MainActor
public final class PopoverStateManager: ObservableObject {
    public static let shared = PopoverStateManager()
    
    @Published public var currentPage: PopoverPage = .dashboard
    @Published public var isAudioRoutingActive: Bool = false
    @Published public var isPulsing: Bool = false
    @Published public var hoveredTile: String? = nil
    
    public init() {}
}

// MARK: - Adaptive Liquid Glass Theme & Visual Hierarchy
public struct AdaptiveGlassTheme {
    public static var osVersion: OperatingSystemVersion {
        ProcessInfo.processInfo.operatingSystemVersion
    }
    
    public static var isModernMacOS: Bool {
        osVersion.majorVersion >= 14
    }
    
    public static var isSequoiaOrLater: Bool {
        osVersion.majorVersion >= 15
    }
    
    public static func dynamicAccent(for isConnected: Bool, customColor: Color? = nil) -> Color {
        if let custom = customColor { return custom }
        return isConnected ? Color(nsColor: .controlAccentColor) : Color.secondary
    }
    
    public static func glassBorder(for colorScheme: ColorScheme) -> LinearGradient {
        LinearGradient(
            colors: [
                Color.white.opacity(colorScheme == .dark ? 0.15 : 0.40),
                Color.white.opacity(colorScheme == .dark ? 0.03 : 0.10)
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
    
    public static func cardShadow(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? Color.black.opacity(0.32) : Color.black.opacity(0.07)
    }
}

// MARK: - View Modifiers for Adaptive Liquid Glass
public struct GlassCardModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let cornerRadius: CGFloat
    let padding: CGFloat
    let isHighlighted: Bool
    let highlightColor: Color
    
    public func body(content: Content) -> some View {
        content
            .padding(padding)
            .background {
                if isHighlighted {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(highlightColor.opacity(colorScheme == .dark ? 0.16 : 0.10))
                } else {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(.ultraThinMaterial)
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(
                        isHighlighted
                            ? LinearGradient(
                                colors: [highlightColor.opacity(0.6), highlightColor.opacity(0.2)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                            : AdaptiveGlassTheme.glassBorder(for: colorScheme),
                        lineWidth: 1
                    )
            }
            .shadow(
                color: isHighlighted ? highlightColor.opacity(0.2) : AdaptiveGlassTheme.cardShadow(for: colorScheme),
                radius: isHighlighted ? 12 : 8,
                x: 0,
                y: isHighlighted ? 4 : 2
            )
    }
}

public extension View {
    func glassCard(
        cornerRadius: CGFloat = 16,
        padding: CGFloat = 12,
        isHighlighted: Bool = false,
        highlightColor: Color = Color(nsColor: .controlAccentColor)
    ) -> some View {
        self.modifier(
            GlassCardModifier(
                cornerRadius: cornerRadius,
                padding: padding,
                isHighlighted: isHighlighted,
                highlightColor: highlightColor
            )
        )
    }
    
    func glassTile(
        id: String,
        isActive: Bool = false,
        activeTint: Color = Color(nsColor: .controlAccentColor)
    ) -> some View {
        self.modifier(
            GlassTileModifier(
                isActive: isActive,
                activeTint: activeTint,
                tileId: id
            )
        )
    }
}

// MARK: - Hover Interactive Tile Modifier (No @State Macro)
public struct GlassTileModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let isActive: Bool
    let activeTint: Color
    let tileId: String
    @ObservedObject var state = PopoverStateManager.shared
    
    public func body(content: Content) -> some View {
        let isHovered = (state.hoveredTile == tileId)
        content
            .background {
                if isActive {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(activeTint.opacity(colorScheme == .dark ? 0.22 : 0.14))
                } else if isHovered {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color.primary.opacity(colorScheme == .dark ? 0.08 : 0.05))
                } else {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color.primary.opacity(0.02))
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(
                        isActive
                            ? LinearGradient(
                                colors: [activeTint.opacity(0.8), activeTint.opacity(0.3)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                            : (isHovered
                                ? LinearGradient(
                                    colors: [Color.white.opacity(0.35), Color.white.opacity(0.12)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                : AdaptiveGlassTheme.glassBorder(for: colorScheme)),
                        lineWidth: 1
                    )
            }
            .scaleEffect(isHovered ? 1.02 : 1.0)
            .animation(.spring(response: 0.35, dampingFraction: 0.8), value: isHovered)
            .onHover { hovering in
                if hovering {
                    state.hoveredTile = tileId
                } else if state.hoveredTile == tileId {
                    state.hoveredTile = nil
                }
            }
    }
}

// MARK: - Apple Health / Activity Style Progress Ring
public struct ActivityRingView: View {
    let progress: Double // 0.0 ... 1.0
    let ringColor: Color
    let ringWidth: CGFloat
    let diameter: CGFloat
    let icon: String?
    let title: String
    let valueText: String
    let subtitle: String?
    
    public init(
        progress: Double,
        ringColor: Color,
        ringWidth: CGFloat = 5.5,
        diameter: CGFloat = 40,
        icon: String? = nil,
        title: String,
        valueText: String,
        subtitle: String? = nil
    ) {
        self.progress = min(max(progress, 0.0), 1.0)
        self.ringColor = ringColor
        self.ringWidth = ringWidth
        self.diameter = diameter
        self.icon = icon
        self.title = title
        self.valueText = valueText
        self.subtitle = subtitle
    }
    
    public var body: some View {
        VStack(spacing: 4) {
            ZStack {
                // Background Track
                Circle()
                    .stroke(ringColor.opacity(0.18), lineWidth: ringWidth)
                
                // Active Stroke
                Circle()
                    .trim(from: 0.0, to: CGFloat(progress))
                    .stroke(
                        AngularGradient(
                            gradient: Gradient(colors: [ringColor.opacity(0.7), ringColor]),
                            center: .center,
                            startAngle: .degrees(-90),
                            endAngle: .degrees(270)
                        ),
                        style: StrokeStyle(lineWidth: ringWidth, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .animation(.spring(response: 0.6, dampingFraction: 0.75), value: progress)
                
                // Center Icon or Percentage
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: ringWidth * 1.8, weight: .bold))
                        .foregroundColor(ringColor)
                } else {
                    Text("\(Int(progress * 100))%")
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                }
            }
            .frame(width: diameter, height: diameter)
            
            VStack(spacing: 1) {
                Text(title)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                
                Text(valueText)
                    .font(.system(size: 10, weight: .semibold, design: .rounded))
                    .foregroundColor(.primary)
                    .lineLimit(1)
                
                if let sub = subtitle {
                    Text(sub)
                        .font(.system(size: 8))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
        }
    }
}
