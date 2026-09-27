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
# 3. Generate Apple HIG squircle icon sizes via Python & PIL
# ==============================================================================
echo "==> 2. Generating Apple HIG standard squircle (824x824 on 1024x1024) icons..."

python3 -c "
import os
from PIL import Image, ImageDraw, ImageFilter

src_path = '$SOURCE_ICON'
temp_iconset = '$TEMP_ICONSET'
appiconset_xcassets = '$APPICONSET_XCASSETS'
appiconset_root = '$APPICONSET_ROOT'

src = Image.open(src_path).convert('RGBA')

# Crop tile tightly to align glass bevel directly with macOS squircle
tile = src.crop((180, 175, 834, 829))

SQUIRCLE_SIZE = 824
CORNER_RADIUS = 185
CANVAS_SIZE = 1024
OFFSET = (CANVAS_SIZE - SQUIRCLE_SIZE) // 2

tile_resized = tile.resize((SQUIRCLE_SIZE, SQUIRCLE_SIZE), Image.Resampling.LANCZOS)

mask = Image.new('L', (SQUIRCLE_SIZE, SQUIRCLE_SIZE), 0)
draw = ImageDraw.Draw(mask)
draw.rounded_rectangle((0, 0, SQUIRCLE_SIZE, SQUIRCLE_SIZE), radius=CORNER_RADIUS, fill=255)

tile_masked = Image.new('RGBA', (SQUIRCLE_SIZE, SQUIRCLE_SIZE), (0, 0, 0, 0))
tile_masked.paste(tile_resized, (0, 0), mask)

master = Image.new('RGBA', (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
shadow = Image.new('RGBA', (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
shadow_draw = ImageDraw.Draw(shadow)
shadow_draw.rounded_rectangle((OFFSET, OFFSET + 14, OFFSET + SQUIRCLE_SIZE, OFFSET + SQUIRCLE_SIZE + 14), radius=CORNER_RADIUS, fill=(0, 0, 0, 70))
shadow = shadow.filter(ImageFilter.GaussianBlur(16))

master.paste(shadow, (0, 0), shadow)
master.paste(tile_masked, (OFFSET, OFFSET), mask)

master.save(os.path.join(appiconset_xcassets, 'AppIcon-1024.png'))
master.save(os.path.join(appiconset_root, 'AppIcon-1024.png'))

sizes = [
    ('icon_16x16.png', 16),
    ('icon_16x16@2x.png', 32),
    ('icon_32x32.png', 32),
    ('icon_32x32@2x.png', 64),
    ('icon_128x128.png', 128),
    ('icon_128x128@2x.png', 256),
    ('icon_256x256.png', 256),
    ('icon_256x256@2x.png', 512),
    ('icon_512x512.png', 512),
    ('icon_512x512@2x.png', 1024),
]

for filename, sz in sizes:
    resized = master.resize((sz, sz), Image.Resampling.LANCZOS)
    resized.save(os.path.join(temp_iconset, filename))
    resized.save(os.path.join(appiconset_xcassets, filename))
    resized.save(os.path.join(appiconset_root, filename))
"

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

if [[ -d "/Applications/AetherLink.app/Contents/Resources" ]]; then
  cp "$RESOURCES_DIR/AppIcon.icns" "/Applications/AetherLink.app/Contents/Resources/AppIcon.icns"
fi

if [[ -d "/Users/mehmetsensoy/Applications/AetherLink.app/Contents/Resources" ]]; then
  cp "$RESOURCES_DIR/AppIcon.icns" "/Users/mehmetsensoy/Applications/AetherLink.app/Contents/Resources/AppIcon.icns"
fi

cp "$RESOURCES_DIR/AppIcon.icns" "$XCASSETS_DIR/AppIcon.icns"

echo "==> AppIcon generation completed successfully!"
