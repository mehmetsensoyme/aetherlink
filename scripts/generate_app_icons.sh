#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# AetherLink - macOS AppIcon Generator from Android Asset
# Generates complete Apple AppIcon.appiconset and AppIcon.icns
# ==============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> 1. Searching for highest resolution Android icon..."
CANDIDATES=(
  "$REPO_ROOT/android-client/app/src/main/res/ic_launcher-playstore.png"
  "$REPO_ROOT/android-client/app/src/main/ic_launcher-playstore.png"
  "$REPO_ROOT/android-client/ic_launcher-playstore.png"
  "$REPO_ROOT/android-client/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
  "$REPO_ROOT/android-client/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png"
  "$REPO_ROOT/android-client/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"
  "$REPO_ROOT/macos-client/AppIcon.appiconset/AppIcon-1024.png"
)

SOURCE_ICON=""
MAX_PIXELS=0

for c in "${CANDIDATES[@]}"; do
  if [[ -f "$c" ]]; then
    W=$(sips -g pixelWidth "$c" 2>/dev/null | awk '/pixelWidth/ {print $2}')
    H=$(sips -g pixelHeight "$c" 2>/dev/null | awk '/pixelHeight/ {print $2}')
    if [[ -n "$W" && -n "$H" ]]; then
      PIXELS=$((W * H))
      echo "    Candidate: $c (${W}x${H})"
      if (( PIXELS > MAX_PIXELS )); then
        MAX_PIXELS=$PIXELS
        SOURCE_ICON="$c"
      fi
    fi
  fi
done

if [[ -z "$SOURCE_ICON" ]]; then
  echo "Error: No valid source icon found." >&2
  exit 1
fi

echo "==> Selected source icon: $SOURCE_ICON (Resolution: $(sips -g pixelWidth "$SOURCE_ICON" | awk '/pixelWidth/{print $2}')x$(sips -g pixelHeight "$SOURCE_ICON" | awk '/pixelHeight/{print $2}'))"

# Keep Android 1024/512 playstore icon populated
mkdir -p "$REPO_ROOT/android-client/app/src/main/res"
for target in "$REPO_ROOT/android-client/app/src/main/ic_launcher-playstore.png" "$REPO_ROOT/android-client/ic_launcher-playstore.png" "$REPO_ROOT/android-client/app/src/main/res/ic_launcher-playstore.png"; do
  if [[ "$SOURCE_ICON" != "$target" ]]; then
    cp "$SOURCE_ICON" "$target"
  fi
done

# ==============================================================================
# 2. Target Directories
# ==============================================================================
XCASSETS_DIR="$REPO_ROOT/macos-client/Assets.xcassets"
APPICONSET_XCASSETS="$XCASSETS_DIR/AppIcon.appiconset"
APPICONSET_ROOT="$REPO_ROOT/macos-client/AppIcon.appiconset"
TEMP_ICONSET="$REPO_ROOT/macos-client/AppIcon.iconset"
RESOURCES_DIR="$REPO_ROOT/macos-client/Resources"

mkdir -p "$APPICONSET_XCASSETS"
mkdir -p "$APPICONSET_ROOT"
mkdir -p "$TEMP_ICONSET"
mkdir -p "$RESOURCES_DIR"

# Write root Assets.xcassets Contents.json
cat <<'EOF' > "$XCASSETS_DIR/Contents.json"
{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
EOF

# ==============================================================================
# 3. Generate icon sizes via sips
# ==============================================================================
echo "==> 2. Resizing icons via sips for macOS standard..."

generate_icon() {
  local name="$1"
  local size="$2"
  sips -z "$size" "$size" "$SOURCE_ICON" --out "$TEMP_ICONSET/$name" >/dev/null
  cp "$TEMP_ICONSET/$name" "$APPICONSET_XCASSETS/$name"
  cp "$TEMP_ICONSET/$name" "$APPICONSET_ROOT/$name"
  echo "    Generated: $name (${size}x${size})"
}

generate_icon "icon_16x16.png" 16
generate_icon "icon_16x16@2x.png" 32
generate_icon "icon_32x32.png" 32
generate_icon "icon_32x32@2x.png" 64
generate_icon "icon_128x128.png" 128
generate_icon "icon_128x128@2x.png" 256
generate_icon "icon_256x256.png" 256
generate_icon "icon_256x256@2x.png" 512
generate_icon "icon_512x512.png" 512
generate_icon "icon_512x512@2x.png" 1024

cp "$SOURCE_ICON" "$APPICONSET_XCASSETS/AppIcon-1024.png"
cp "$SOURCE_ICON" "$APPICONSET_ROOT/AppIcon-1024.png"

# ==============================================================================
# 4. Generate AppIcon.appiconset/Contents.json
# ==============================================================================
echo "==> 3. Writing AppIcon.appiconset/Contents.json..."

cat <<'EOF' > "$APPICONSET_XCASSETS/Contents.json"
{
  "images": [
    {
      "size": "16x16",
      "idiom": "mac",
      "filename": "icon_16x16.png",
      "scale": "1x"
    },
    {
      "size": "16x16",
      "idiom": "mac",
      "filename": "icon_16x16@2x.png",
      "scale": "2x"
    },
    {
      "size": "32x32",
      "idiom": "mac",
      "filename": "icon_32x32.png",
      "scale": "1x"
    },
    {
      "size": "32x32",
      "idiom": "mac",
      "filename": "icon_32x32@2x.png",
      "scale": "2x"
    },
    {
      "size": "128x128",
      "idiom": "mac",
      "filename": "icon_128x128.png",
      "scale": "1x"
    },
    {
      "size": "128x128",
      "idiom": "mac",
      "filename": "icon_128x128@2x.png",
      "scale": "2x"
    },
    {
      "size": "256x256",
      "idiom": "mac",
      "filename": "icon_256x256.png",
      "scale": "1x"
    },
    {
      "size": "256x256",
      "idiom": "mac",
      "filename": "icon_256x256@2x.png",
      "scale": "2x"
    },
    {
      "size": "512x512",
      "idiom": "mac",
      "filename": "icon_512x512.png",
      "scale": "1x"
    },
    {
      "size": "512x512",
      "idiom": "mac",
      "filename": "icon_512x512@2x.png",
      "scale": "2x"
    }
  ],
  "info": {
    "version": 1,
    "author": "xcode"
  }
}
EOF

cp "$APPICONSET_XCASSETS/Contents.json" "$APPICONSET_ROOT/Contents.json"

# ==============================================================================
# 5. Compile AppIcon.icns via iconutil
# ==============================================================================
echo "==> 4. Compiling AppIcon.icns via iconutil..."
iconutil -c icns "$TEMP_ICONSET" -o "$RESOURCES_DIR/AppIcon.icns"
rm -rf "$TEMP_ICONSET"

# Copy AppIcon.icns to bundles
if [[ -d "$REPO_ROOT/macos-client/AetherLink.app/Contents/Resources" ]]; then
  cp "$RESOURCES_DIR/AppIcon.icns" "$REPO_ROOT/macos-client/AetherLink.app/Contents/Resources/AppIcon.icns"
fi

if [[ -d "/Users/mehmetsensoy/Applications/AetherLink.app/Contents/Resources" ]]; then
  cp "$RESOURCES_DIR/AppIcon.icns" "/Users/mehmetsensoy/Applications/AetherLink.app/Contents/Resources/AppIcon.icns"
fi

cp "$RESOURCES_DIR/AppIcon.icns" "$XCASSETS_DIR/AppIcon.icns"

echo "==> AppIcon generation completed successfully!"
