#!/bin/zsh
# Retake the demo screenshots in docs/screenshots and rebuild overview.png.
# Needs: a running emulator with the app installed (1080 px wide), adb, and ImageMagick (`magick`).
# Usage: scripts/screenshots.sh [adb-serial]   (default emulator-5554)
set -eu
DEV=${1:-emulator-5554}
OUT=${0:A:h:h}/docs/screenshots
PKG=io.github.lacostej.playtime
mkdir -p $OUT

D() { adb -s $DEV shell "$@"; }
ui() { D uiautomator dump /sdcard/ui.xml >/dev/null; adb -s $DEV exec-out cat /sdcard/ui.xml; }
# Prints "x y width" of the first node whose attributes match the regex $1, or fails.
center_of() {
  local found
  for _ in 1 2 3; do # uiautomator dumps are occasionally empty while the screen settles
    found=$(ui | python3 -c "
import sys,re
x=sys.stdin.read(); m=re.search(r'$1[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', x)
print((int(m[1])+int(m[3]))//2, (int(m[2])+int(m[4]))//2, int(m[3])-int(m[1])) if m else print('')")
    [[ -n $found ]] && { echo $found; return; }
    sleep 1
  done
  echo "not on screen: $1" >&2; return 1
}
tap_text() { local p; p=($(center_of "text=\"$1\"")); D input tap $p[1] $p[2]; sleep 1; }
tap_prefix() { local p; p=($(center_of "text=\"$1")); D input tap $p[1] $p[2]; sleep 1; }
shot() { sleep 1; adb -s $DEV exec-out screencap -p > $OUT/$1.png; echo "saved $1.png"; }
# Turn the wheel by $1 degrees (negative = anticlockwise), starting at the top of the ring.
turn() {
  local p; p=($(center_of 'content-desc="Today.s play time'))
  D "$(python3 -c "
import math
cx,cy,r=$p[1],$p[2],$p[3]*116/300; deg=$1; n=max(2,abs(int(deg))//5)
pts=[(cx+r*math.sin(math.radians(deg*i/n)), cy-r*math.cos(math.radians(deg*i/n))) for i in range(n+1)]
print(';'.join(['input motionevent DOWN %d %d'%pts[0]]+['input motionevent MOVE %d %d'%q for q in pts[1:]]+['input motionevent UP %d %d'%pts[-1]]))")"
  sleep 1
}
reset() { tap_prefix "Today"; } # a tap away from the wheel resets it

# Clean status bar: fixed clock, full battery, no notifications.
D settings put global sysui_demo_allowed 1
bc() { D am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
bc enter; bc clock -e hhmm 1200; bc battery -e level 100 -e plugged false
bc network -e wifi show -e level 4 -e mobile hide; bc notifications -e visible false
trap 'bc exit; D settings put global sysui_demo_allowed 0' EXIT

D monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 3
if ui | grep -q 'text="Exit demo"'; then tap_text "Exit demo"; sleep 3; fi
tap_text Demo
shot 1-not-started
tap_prefix "Demo 1/4"; shot 2-playing
tap_text "Stop play"; shot 5-dialog-stop; tap_text Cancel
turn 90; shot 2b-playing-add-15
reset; turn -90; shot 2c-playing-reduce-15
tap_prefix "Demo 2/4"; shot 3-bedtime-cuts
tap_prefix "Move bedtime to"; shot 3c-bedtime-fit
reset; turn 60; shot 3b-bedtime-moves
tap_prefix "Demo 3/4"; shot 4-bedtime-passed
tap_text About; shot 6-about; tap_text Close
tap_text "Exit demo"

cd $OUT
magick montage 1-not-started.png 2-playing.png 2b-playing-add-15.png 2c-playing-reduce-15.png 3-bedtime-cuts.png \
  3c-bedtime-fit.png 3b-bedtime-moves.png 4-bedtime-passed.png 5-dialog-stop.png 6-about.png \
  -tile 5x2 -geometry 270x600+6+6 -background '#cccccc' overview.png
echo "saved overview.png"
