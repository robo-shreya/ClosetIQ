#!/usr/bin/env bash
#
# Copy photos from this Mac into the emulator's gallery so the app's picker can see them.
#
#   tools/push-photos.sh ~/Downloads/garments            -> album "ClosetIQ"
#   tools/push-photos.sh ~/Downloads/garments/user\ photos ClosetIQ-Me
#   tools/push-photos.sh ~/Desktop/one-photo.png
#
# Pushing alone is not enough: Android keeps its own index of images, and the photo picker
# reads that index rather than the filesystem. A file that has not been scanned is on the
# device but invisible, which looks exactly like a failed copy. This does both steps.

set -euo pipefail

ADB="${ADB:-$HOME/android/sdk/platform-tools/adb}"
SOURCE="${1:-}"
ALBUM="${2:-ClosetIQ}"
DEST="/sdcard/Pictures/${ALBUM}"

if [ -z "$SOURCE" ]; then
    echo "usage: $0 <file-or-folder> [album-name]" >&2
    exit 1
fi

if [ ! -e "$SOURCE" ]; then
    echo "No such file or folder: $SOURCE" >&2
    exit 1
fi

if [ ! -x "$ADB" ]; then
    echo "adb not found at $ADB — set ADB=/path/to/adb and retry." >&2
    exit 1
fi

if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "No emulator is running. Start one from Android Studio's Device Manager." >&2
    exit 1
fi

"$ADB" shell mkdir -p "$DEST"

push_one() {
    local file="$1"
    local name
    name="$(basename "$file")"

    # stdin is redirected on both calls because adb reads it, and inside the read loop
    # below that means it swallows the rest of the file list — silently pushing only the
    # first photo and reporting success.
    "$ADB" push "$file" "$DEST/$name" >/dev/null 2>&1 </dev/null

    # Scanned per file rather than per folder: a directory-wide broadcast is ignored on
    # current Android versions and silently does nothing.
    "$ADB" shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d 'file://$DEST/$name'" >/dev/null 2>&1 </dev/null
    echo "  $name"
}

echo "-> $DEST"

count=0
if [ -d "$SOURCE" ]; then
    while IFS= read -r -d '' file; do
        push_one "$file"
        count=$((count + 1))
    done < <(find "$SOURCE" -maxdepth 1 -type f \
        \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' \) -print0)
else
    push_one "$SOURCE"
    count=1
fi

if [ "$count" -eq 0 ]; then
    echo "No .png/.jpg/.jpeg files found in $SOURCE" >&2
    exit 1
fi

# Subfolders are not walked, so that a folder of garments and a folder of photos of the
# user stay separate albums rather than merging. Silently skipping them looked like the
# script had missed files, so it says so instead.
if [ -d "$SOURCE" ]; then
    while IFS= read -r -d '' sub; do
        echo
        echo "Skipped the subfolder \"$(basename "$sub")\" — run it separately to give it"
        echo "its own album:"
        echo "  $0 \"$sub\" <album-name>"
    done < <(find "$SOURCE" -mindepth 1 -maxdepth 1 -type d -print0)
fi

echo
echo "$count photo(s) pushed and scanned. They appear in the picker under Recent,"
echo "and as the album \"$ALBUM\"."
