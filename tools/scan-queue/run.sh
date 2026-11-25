#!/bin/sh
# Scan queue test: fires N scan broadcasts (SCAN0001 …) from a shell on the device while the page of
# tools/scan-queue/server.py saves, then prints what the server saved (in order, missing, duplicates).
#
#   tools/scan-queue/run.sh [-s <adb serial>] [-n <count>] [-d <seconds between scans>] [-u <server url>]
#
# Before you start: run server.py, open its page in the app (profile with scanner.genericIntent "on"),
# see docs/testing-scan-queue.md. -u is the server as seen from this PC (default http://localhost:8767).
set -eu
SERIAL=""; COUNT=10; DELAY=0.5; URL=http://localhost:8767
while getopts "s:n:d:u:" opt; do
  case $opt in
    s) SERIAL="$OPTARG" ;; n) COUNT="$OPTARG" ;; d) DELAY="$OPTARG" ;; u) URL="$OPTARG" ;;
    *) echo "usage: $0 [-s serial] [-n count] [-d delay] [-u server-url]" >&2; exit 2 ;;
  esac
done
ADB="adb"; [ -n "$SERIAL" ] && ADB="adb -s $SERIAL"
PKG=${PKG:-de.ictbaden.industrialkioskbrowser}

curl -fsS "$URL/reset" >/dev/null
$ADB logcat -c
$ADB shell "i=1; while [ \$i -le $COUNT ]; do
  am broadcast -a $PKG.action.BARCODE -p $PKG --es data \$(printf 'SCAN%04d' \$i) --es symbology CODE128 >/dev/null 2>&1
  [ '$DELAY' != '0' ] && sleep $DELAY
  i=\$((i + 1))
done"
echo "sent $COUNT scans, $DELAY s apart – waiting for the queue to drain"
sleep "${WAIT:-20}"
curl -fsS "$URL/log" | head -1
echo "discarded by the app (logcat):"
$ADB logcat -d -s BrowserActivity:W | grep -i "discarded" || echo "  none"
