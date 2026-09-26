import WidgetKit
import SwiftUI
import AppKit
import AetherShared

struct BatteryEntry: TimelineEntry {
    let date: Date
    let batteryLevel: Int
    let isCharging: Bool
    let deviceName: String
    let isConnected: Bool
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> BatteryEntry {
        BatteryEntry(
            date: Date(),
            batteryLevel: 85,
            isCharging: true,
            deviceName: "Galaxy S25 Ultra",
            isConnected: true
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (BatteryEntry) -> Void) {
        let (level, isCharging, deviceName, isConnected, date) = AetherWidgetDataManager.shared.loadData()
        let entry = BatteryEntry(
            date: date,
            batteryLevel: isConnected ? level : 0,
            isCharging: isCharging,
            deviceName: deviceName,
            isConnected: isConnected
        )
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<BatteryEntry>) -> Void) {
        let (level, isCharging, deviceName, isConnected, date) = AetherWidgetDataManager.shared.loadData()
        let entry = BatteryEntry(
            date: date,
            batteryLevel: isConnected ? level : 0,
            isCharging: isCharging,
            deviceName: deviceName,
            isConnected: isConnected
        )
        // Refresh immediately on App Group updates, periodic check every 15 mins
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date().addingTimeInterval(900)
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }
}

struct SmallBatteryWidgetView: View {
    var entry: Provider.Entry

    var batteryColor: Color {
        if !entry.isConnected {
            return .secondary
        }
        if entry.isCharging {
            return .green
        }
        if entry.batteryLevel <= 20 {
            return .red
        }
        return .green
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header: Phone icon + Device Name + Status Dot
            HStack(spacing: 5) {
                Image(systemName: "iphone")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(entry.isConnected ? .blue : .secondary)

                Text(entry.deviceName)
                    .font(.system(size: 11, weight: .semibold))
                    .lineLimit(1)
                    .foregroundColor(.primary)

                Spacer(minLength: 0)

                Circle()
                    .fill(entry.isConnected ? Color.green : Color.gray.opacity(0.6))
                    .frame(width: 6, height: 6)
            }

            Spacer()

            // Center Circular Battery Ring
            HStack {
                Spacer()
                ZStack {
                    Circle()
                        .stroke(batteryColor.opacity(0.18), lineWidth: 8)
                        .frame(width: 66, height: 66)

                    Circle()
                        .trim(from: 0.0, to: entry.isConnected ? CGFloat(min(max(entry.batteryLevel, 0), 100)) / 100.0 : 0.0)
                        .stroke(
                            batteryColor,
                            style: StrokeStyle(lineWidth: 8, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                        .frame(width: 66, height: 66)

                    VStack(spacing: 1) {
                        if entry.isConnected {
                            HStack(spacing: 2) {
                                Text("\(entry.batteryLevel)%")
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                                    .foregroundColor(.primary)

                                if entry.isCharging {
                                    Image(systemName: "bolt.fill")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(.yellow)
                                }
                            }

                            Text(entry.isCharging ? "Şarjda" : "Pil")
                                .font(.system(size: 9, weight: .medium))
                                .foregroundColor(.secondary)
                        } else {
                            Image(systemName: "wifi.slash")
                                .font(.system(size: 15))
                                .foregroundColor(.secondary)
                            Text("Bağlantı Yok")
                                .font(.system(size: 8, weight: .medium))
                                .foregroundColor(.secondary)
                        }
                    }
                }
                Spacer()
            }

            Spacer()

            // Footer
            HStack {
                Text("AetherLink")
                    .font(.system(size: 9, weight: .medium))
                    .foregroundColor(.secondary)
                Spacer()
                if entry.isConnected {
                    Text(entry.isCharging ? "Şarj Oluyor" : "Dolu")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(batteryColor)
                }
            }
        }
        .padding(12)
    }
}

struct MediumBatteryWidgetView: View {
    var entry: Provider.Entry

    var batteryColor: Color {
        if !entry.isConnected {
            return .secondary
        }
        if entry.isCharging {
            return .green
        }
        if entry.batteryLevel <= 20 {
            return .red
        }
        return .green
    }

    var body: some View {
        HStack(spacing: 16) {
            // Left: Circular Ring
            ZStack {
                Circle()
                    .stroke(batteryColor.opacity(0.18), lineWidth: 9)
                    .frame(width: 76, height: 76)

                Circle()
                    .trim(from: 0.0, to: entry.isConnected ? CGFloat(min(max(entry.batteryLevel, 0), 100)) / 100.0 : 0.0)
                    .stroke(
                        batteryColor,
                        style: StrokeStyle(lineWidth: 9, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .frame(width: 76, height: 76)

                VStack(spacing: 2) {
                    if entry.isConnected {
                        Text("\(entry.batteryLevel)%")
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundColor(.primary)

                        if entry.isCharging {
                            HStack(spacing: 2) {
                                Image(systemName: "bolt.fill")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.yellow)
                                Text("Şarj")
                                    .font(.system(size: 9, weight: .medium))
                                    .foregroundColor(.secondary)
                            }
                        }
                    } else {
                        Image(systemName: "wifi.slash")
                            .font(.system(size: 18))
                            .foregroundColor(.secondary)
                    }
                }
            }
            .padding(.leading, 6)

            // Right: Device details, Bar, and Status
            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 6) {
                    Image(systemName: "iphone")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(entry.isConnected ? .blue : .secondary)

                    Text(entry.deviceName)
                        .font(.system(size: 13, weight: .bold))
                        .lineLimit(1)
                        .foregroundColor(.primary)

                    Spacer()

                    HStack(spacing: 4) {
                        Circle()
                            .fill(entry.isConnected ? Color.green : Color.gray.opacity(0.6))
                            .frame(width: 6, height: 6)
                        Text(entry.isConnected ? "Bağlı" : "Bağlantı Yok")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(entry.isConnected ? .green : .secondary)
                    }
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(
                        Capsule()
                            .fill(entry.isConnected ? Color.green.opacity(0.12) : Color.gray.opacity(0.12))
                    )
                }

                // Battery Progress Bar
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 5, style: .continuous)
                            .fill(Color.gray.opacity(0.2))
                            .frame(height: 8)

                        RoundedRectangle(cornerRadius: 5, style: .continuous)
                            .fill(batteryColor)
                            .frame(
                                width: entry.isConnected
                                    ? geo.size.width * CGFloat(min(max(entry.batteryLevel, 0), 100)) / 100.0
                                    : 0,
                                height: 8
                            )
                    }
                }
                .frame(height: 8)

                HStack {
                    Text(entry.isConnected ? (entry.isCharging ? "⚡ Şarj Oluyor" : "Standby") : "Eşleşme bekleniyor")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.secondary)

                    Spacer()

                    Text("AetherLink")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(.blue.opacity(0.85))
                }
            }
            .padding(.trailing, 6)
        }
        .padding(12)
    }
}

struct AetherLinkBatteryWidgetEntryView: View {
    var entry: Provider.Entry
    @Environment(\.widgetFamily) var family

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                SmallBatteryWidgetView(entry: entry)
            default:
                MediumBatteryWidgetView(entry: entry)
            }
        }
        .containerBackground(for: .widget) {
            Color(NSColor.windowBackgroundColor)
        }
    }
}

@main
struct AetherLinkWidgetBundle: WidgetBundle {
    var body: some Widget {
        AetherLinkBatteryWidget()
    }
}

struct AetherLinkBatteryWidget: Widget {
    let kind: String = "org.aetherlink.mac.batteryWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            AetherLinkBatteryWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("AetherLink Batarya")
        .description("Android telefonunuzun anlık şarj seviyesini, şarj durumunu ve bağlantısını gösterir.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
