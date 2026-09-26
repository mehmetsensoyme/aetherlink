/**
 * AetherLink Universal GitHub Releases Auto-Update Engine
 * Handles semantic version verification, changelog parsing, and release asset retrieval.
 */

export interface GitHubAsset {
  id: number;
  name: string;
  browser_download_url: string;
  size: number;
  content_type: string;
}

export interface GitHubRelease {
  tag_name: string;
  name: string;
  body: string; // Markdown Changelog
  published_at: string;
  html_url: string;
  prerelease: boolean;
  assets: GitHubAsset[];
}

export interface UpdateCheckResult {
  hasUpdate: boolean;
  currentVersion: string;
  latestVersion: string;
  releaseTitle: string;
  changelogMarkdown: string;
  releaseDate: string;
  releaseUrl: string;
  downloadUrl?: string;
  assetName?: string;
}

export class AetherUpdateEngine {
  private repoOwner: string;
  private repoName: string;
  private currentVersion: string;
  private platform: 'macOS' | 'android';

  constructor(
    repoOwner: string = 'mehmetsensoy',
    repoName: string = 'aetherlink',
    currentVersion: string = '1.0.0',
    platform: 'macOS' | 'android' = 'macOS'
  ) {
    this.repoOwner = repoOwner;
    this.repoName = repoName;
    this.currentVersion = currentVersion.replace(/^v/, '');
    this.platform = platform;
  }

  /**
   * Compares two semantic version strings (e.g. "1.0.0" vs "1.1.0-beta")
   * Returns:
   *   1 if v2 > v1 (update available)
   *   0 if v2 == v1
   *  -1 if v2 < v1
   */
  public static compareSemVer(v1: string, v2: string): number {
    const cleanV1 = v1.replace(/^v/, '').split(/[-+]/)[0];
    const cleanV2 = v2.replace(/^v/, '').split(/[-+]/)[0];

    const parts1 = cleanV1.split('.').map(n => parseInt(n, 10) || 0);
    const parts2 = cleanV2.split('.').map(n => parseInt(n, 10) || 0);

    for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
      const p1 = parts1[i] || 0;
      const p2 = parts2[i] || 0;
      if (p2 > p1) return 1;
      if (p2 < p1) return -1;
    }
    return 0;
  }

  /**
   * Fetches latest release from GitHub API and compares with local version
   */
  public async checkForUpdates(customFetch?: typeof fetch): Promise<UpdateCheckResult> {
    const url = `https://api.github.com/repos/${this.repoOwner}/${this.repoName}/releases/latest`;
    const fetchFn = customFetch || fetch;

    try {
      const response = await fetchFn(url, {
        headers: {
          'Accept': 'application/vnd.github.v3+json',
          'User-Agent': `AetherLink-Updater/${this.currentVersion}`
        }
      });

      if (!response.ok) {
        throw new Error(`GitHub API returned status ${response.status}: ${response.statusText}`);
      }

      const release: GitHubRelease = await response.json();
      const latestVer = release.tag_name.replace(/^v/, '');
      const isNewer = AetherUpdateEngine.compareSemVer(this.currentVersion, latestVer) > 0;

      // Find platform-specific asset (.dmg for macOS, .apk for Android)
      const targetExt = this.platform === 'macOS' ? '.dmg' : '.apk';
      const matchedAsset = release.assets.find(a => a.name.toLowerCase().endsWith(targetExt));

      return {
        hasUpdate: isNewer,
        currentVersion: this.currentVersion,
        latestVersion: latestVer,
        releaseTitle: release.name || release.tag_name,
        changelogMarkdown: release.body || 'No release notes provided for this build.',
        releaseDate: new Date(release.published_at).toLocaleDateString('tr-TR', {
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        }),
        releaseUrl: release.html_url,
        downloadUrl: matchedAsset ? matchedAsset.browser_download_url : release.html_url,
        assetName: matchedAsset ? matchedAsset.name : undefined
      };
    } catch (error) {
      console.warn('[AetherUpdateEngine] Update check failed:', error);
      return {
        hasUpdate: false,
        currentVersion: this.currentVersion,
        latestVersion: this.currentVersion,
        releaseTitle: '',
        changelogMarkdown: '',
        releaseDate: '',
        releaseUrl: ''
      };
    }
  }
}
