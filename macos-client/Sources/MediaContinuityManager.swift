import AppKit
import Foundation

@MainActor
public final class MediaContinuityManager: ObservableObject {
    public static let shared = MediaContinuityManager()
    
    @Published public var lastOpenedTrack: String? = nil
    @Published public var activePlatform: String? = nil
    
    private init() {}
    
    public func handleIncomingMedia(_ media: MediaSessionPayload, autoOpen: Bool = false) {
        guard media.isPlaying else { return }
        
        let trackKey = "\(media.packageName):\(media.trackTitle)-\(media.artist)"
        if lastOpenedTrack == trackKey && !autoOpen {
            return
        }
        
        switch media.packageName {
        case "com.spotify.music", "com.spotify.lite":
            activePlatform = "Spotify"
            if autoOpen {
                openInSpotify(track: media.trackTitle, artist: media.artist)
                lastOpenedTrack = trackKey
            }
            
        case "com.apple.android.music":
            activePlatform = "Apple Music"
            if autoOpen {
                openInAppleMusic(track: media.trackTitle, artist: media.artist)
                lastOpenedTrack = trackKey
            }
            
        default:
            // Do NOT trigger either app for other packages
            activePlatform = nil
            return
        }
    }
    
    public func openCurrentMedia(_ media: MediaSessionPayload) {
        handleIncomingMedia(media, autoOpen: true)
    }
    
    // MARK: - Spotify macOS Integration
    public func openInSpotify(track: String, artist: String) {
        print("[MediaContinuityManager] Opening Spotify for: \(track) - \(artist)")
        let query = "\(track) \(artist)".trimmingCharacters(in: .whitespacesAndNewlines)
        let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        
        // 1. Try Spotify URI scheme
        if let url = URL(string: "spotify:search:\(encodedQuery)") {
            NSWorkspace.shared.open(url)
        }
        
        // 2. Bring Spotify.app to front via AppleScript
        let scriptSource = """
        tell application "Spotify"
            activate
        end tell
        """
        if let appleScript = NSAppleScript(source: scriptSource) {
            var errorInfo: NSDictionary?
            appleScript.executeAndReturnError(&errorInfo)
            if let err = errorInfo {
                print("[MediaContinuityManager] Spotify AppleScript error: \(err)")
            }
        }
    }
    
    // MARK: - Apple Music macOS Integration
    public func openInAppleMusic(track: String, artist: String) {
        print("[MediaContinuityManager] Opening Apple Music (Music.app) for: \(track) - \(artist)")
        let query = "\(track) \(artist)".trimmingCharacters(in: .whitespacesAndNewlines)
        let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        
        // 1. Open Music.app URL Scheme if applicable
        if let url = URL(string: "music://music.apple.com/search?term=\(encodedQuery)") {
            NSWorkspace.shared.open(url)
        }
        
        // 2. Bring Music.app to front via AppleScript
        let scriptSource = """
        tell application "Music"
            activate
        end tell
        """
        if let appleScript = NSAppleScript(source: scriptSource) {
            var errorInfo: NSDictionary?
            appleScript.executeAndReturnError(&errorInfo)
            if let err = errorInfo {
                print("[MediaContinuityManager] Music.app AppleScript error: \(err)")
            }
        }
    }
    
    // MARK: - Auto-Pause Media on Call (KDE Connect pausemusic port)
    @Published public var wasSpotifyPlayingBeforeCall: Bool = false
    @Published public var wasMusicPlayingBeforeCall: Bool = false
    
    public func pauseMediaForIncomingCall() {
        print("[MediaContinuityManager] Pausing active media for incoming call...")
        
        let spotifyCheck = """
        tell application "System Events"
            set isRunning to (exists (processes where name is "Spotify"))
        end tell
        if isRunning then
            tell application "Spotify"
                if player state is playing then
                    pause
                    return "playing"
                end if
            end tell
        end if
        return "not_playing"
        """
        if let script = NSAppleScript(source: spotifyCheck) {
            var err: NSDictionary?
            let res = script.executeAndReturnError(&err)
            if res.stringValue == "playing" {
                wasSpotifyPlayingBeforeCall = true
                print("[MediaContinuityManager] Paused Spotify playback")
            }
        }
        
        let musicCheck = """
        tell application "System Events"
            set isRunning to (exists (processes where name is "Music"))
        end tell
        if isRunning then
            tell application "Music"
                if player state is playing then
                    pause
                    return "playing"
                end if
            end tell
        end if
        return "not_playing"
        """
        if let script = NSAppleScript(source: musicCheck) {
            var err: NSDictionary?
            let res = script.executeAndReturnError(&err)
            if res.stringValue == "playing" {
                wasMusicPlayingBeforeCall = true
                print("[MediaContinuityManager] Paused Apple Music playback")
            }
        }
    }
    
    public func resumeMediaAfterCall() {
        guard wasSpotifyPlayingBeforeCall || wasMusicPlayingBeforeCall else { return }
        print("[MediaContinuityManager] Resuming media after call completed...")
        
        if wasSpotifyPlayingBeforeCall {
            wasSpotifyPlayingBeforeCall = false
            let scriptSource = """
            tell application "Spotify" to play
            """
            NSAppleScript(source: scriptSource)?.executeAndReturnError(nil)
            print("[MediaContinuityManager] Resumed Spotify playback")
        }
        
        if wasMusicPlayingBeforeCall {
            wasMusicPlayingBeforeCall = false
            let scriptSource = """
            tell application "Music" to play
            """
            NSAppleScript(source: scriptSource)?.executeAndReturnError(nil)
            print("[MediaContinuityManager] Resumed Apple Music playback")
        }
    }
}
