#!/bin/sh
# Scan stress test: fires N generic scan broadcasts at the app from a shell on the device and
# reads the counters of tools/scan-stress/index.html back through uiautomator (no typing involved).
#
#   tools/scan-stress/run.sh [-s <adb serial>] [-n <count>] [-d <seconds between scans>] [-o <out dir>]
#
# Before you start: serve this folder (python3 -m http.server 8766), open it in the app with a
# profile whose scanner.genericIntent is "on" and terminator "enter" (on an emulator the host is
# http://10.0.2.2:8766/), and make sure the input field has the focus. Results and a logcat go to
# the output directory. See docs/testing-scan-stress.md.
set -eu
SERIAL=""; COUNT=200; DELAY=0.3; OUT=scan-stress-out
while getopts "s:n:d:o:" opt; do
  case $opt in
    s) SERIAL="$OPTARG" ;; n) COUNT="$OPTARG" ;; d) DELAY="$OPTARG" ;; o) OUT="$OPTARG" ;;
    *) echo "usage: $0 [-s serial] [-n count] [-d delay] [-o outdir]" >&2; exit 2 ;;
  esac
done
ADB="adb"; [ -n "$SERIAL" ] && ADB="adb -s $SERIAL"
PKG=de.ictbaden.industrialkioskbrowser
mkdir -p "$OUT"
LABEL="n${COUNT}-d${DELAY}"

cat > "$OUT/burst.sh" <<'DEV'
#!/system/bin/sh
# runs on the device: burst.sh <count> <delay-or-0>
N=$1; SL=$2; PKG=de.ictbaden.industrialkioskbrowser; i=1
echo "START $(date +%s.%N)"
while [ $i -le $N ]; do
  am broadcast -a $PKG.action.BARCODE -p $PKG --es data "$(printf 'SCAN%04d' $i)" --es symbology CODE128 >/dev/null 2>&1
  [ "$SL" != "0" ] && sleep $SL
  i=$((i + 1))
done
echo "END $(date +%s.%N)"
DEV
$ADB push "$OUT/burst.sh" /data/local/tmp/ikb-burst.sh >/dev/null
$ADB shell chmod 755 /data/local/tmp/ikb-burst.sh

meminfo() { $ADB shell dumpsys meminfo $PKG | grep -m1 'TOTAL PSS:' | awk '{print $3}'; }

# reset the page counters (the page treats the value RESET as a control code)
$ADB shell am broadcast -a $PKG.action.BARCODE -p $PKG --es data RESET >/dev/null 2>&1
sleep 2
$ADB logcat -c
$ADB logcat -v time > "$OUT/logcat-$LABEL.txt" 2>&1 &
LOGPID=$!
echo "PSS before: $(meminfo) kB" | tee "$OUT/result-$LABEL.txt"
$ADB shell sh /data/local/tmp/ikb-burst.sh "$COUNT" "$DELAY" | tee "$OUT/timing-$LABEL.txt"
sleep 4
$ADB shell uiautomator dump /sdcard/ikb-ui.xml >/dev/null 2>&1 || true
$ADB shell cat /sdcard/ikb-ui.xml | tr '>' '\n' | grep -o 'text="IKB[^"]*"' | head -1 | tee -a "$OUT/result-$LABEL.txt"
$ADB shell dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -1 | tee -a "$OUT/result-$LABEL.txt"
echo "PSS after: $(meminfo) kB" | tee -a "$OUT/result-$LABEL.txt"
$ADB exec-out screencap -p > "$OUT/screen-$LABEL.png"
kill $LOGPID 2>/dev/null || true
START=$(awk '/^START/{print $2}' "$OUT/timing-$LABEL.txt"); END=$(awk '/^END/{print $2}' "$OUT/timing-$LABEL.txt")
awk -v s="$START" -v e="$END" -v n="$COUNT" 'BEGIN { d = e - s; printf "sent %d scans in %.1f s = %.2f scans/s\n", n, d, n / d }' | tee -a "$OUT/result-$LABEL.txt"
echo "logcat warnings:" | tee -a "$OUT/result-$LABEL.txt"
grep -E ' ANR |am_anr|E/AndroidRuntime|FATAL EXCEPTION|Choreographer.*Skipped|StrictMode' "$OUT/logcat-$LABEL.txt" | grep -v uiautomator | head -20 | tee -a "$OUT/result-$LABEL.txt" || true
echo "done – results in $OUT/"
