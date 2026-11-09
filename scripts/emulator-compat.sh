#!/usr/bin/env bash
set -euo pipefail
APP=com.hubgoogle.record2
API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
OUT=docs/compat-evidence
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
for p in RECORD_AUDIO CAMERA ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
  adb shell pm grant "$APP" "android.permission.$p"
done
adb shell pm grant "$APP" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell settings put secure location_mode 3 || true
adb shell am start -W -n "$APP/.MainActivity" || true
DUMP=/tmp/record2-compat.xml
dump_ui() {
  adb shell uiautomator dump /sdcard/compat.xml >/dev/null 2>&1 || return 1
  adb pull /sdcard/compat.xml "$DUMP" >/dev/null 2>&1
}
tap_text() {
  dump_ui
  python3 - "$1" "$DUMP" <<'PY'
import sys,subprocess,re,xml.etree.ElementTree as ET
target,path=sys.argv[1:]
for node in ET.parse(path).getroot().iter('node'):
    if node.attrib.get('text','')==target:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        sys.exit(0)
raise SystemExit('Missing button: '+target)
PY
}
ready=false
for i in $(seq 1 35); do
  if dump_ui && grep -q '開始錄音' "$DUMP"; then ready=true; break; fi
  if [[ -f "$DUMP" ]] && grep -q 'android:id/aerr_close' "$DUMP"; then
    python3 - "$DUMP" <<'PY'
import sys,re,subprocess,xml.etree.ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).getroot().iter('node'))
title=next((n.attrib.get('text','') for n in nodes if n.attrib.get('resource-id')=='android:id/alertTitle'),'')
if 'Record2' in title or ("isn't responding" not in title and 'not responding' not in title):
    raise SystemExit('FAIL: unexpected ANR '+title)
btn=next(n for n in nodes if n.attrib.get('resource-id')=='android:id/aerr_close')
x1,y1,x2,y2=map(int,re.findall(r'\d+',btn.attrib['bounds']))
print('Closing system-process ANR:',title)
subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
PY
  fi
  if ((i % 12 == 0)); then adb shell am start -n "$APP/.MainActivity" || true; fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  adb exec-out screencap -p > "$OUT/api$API-startup-failed.png" || true
  adb logcat -d -t 6000 > "$OUT/api$API-startup-log.txt" || true
  echo "FAIL: home not ready on Android API $API"
  exit 1
fi
adb exec-out screencap -p > "$OUT/api$API-home.png"
tap_text '開始錄音'
sleep 5
tap_text '停止錄音'
sleep 2
adb shell ls "/sdcard/Android/data/$APP/files/audio" | grep -q '\.m4a'
adb exec-out screencap -p > "$OUT/api$API-audio-saved.png"
echo "PASS: API $API audio saved"
# Android 8 location providers may take longer to initialize after setting mode.
adb shell settings put secure location_mode 3 || true
adb emu geo fix 121.5654 25.0330 || true
sleep 5
tap_text '開始定位'
sleep 5
if ! dump_ui || ! grep -q '停止定位' "$DUMP"; then
  echo "FAIL: location foreground service did not start on API $API"
  adb exec-out screencap -p > "$OUT/api$API-location-failed.png" || true
  adb shell dumpsys location > "$OUT/api$API-dumpsys-location.txt" || true
  adb shell settings get secure location_mode > "$OUT/api$API-location-mode.txt" || true
  adb shell dumpsys activity services "$APP" > "$OUT/api$API-services.txt" || true
  adb logcat -d -t 6000 > "$OUT/api$API-location-logcat.txt" || true
  adb pull "/sdcard/Android/data/$APP/files/events/events.jsonl" "$OUT/api$API-events.jsonl" >/dev/null 2>&1 || true
  cp "$DUMP" "$OUT/api$API-location-failed.xml" || true
  exit 1
fi
adb emu geo fix 121.5654 25.0330 || true
sleep 4
tap_text '停止定位'
adb exec-out screencap -p > "$OUT/api$API-location-stopped.png"
echo "PASS: API $API location start and stop"
tap_text '開啟相機'
camera_ready=false
for i in $(seq 1 20); do
  if dump_ui && grep -q '拍照並儲存' "$DUMP"; then
    if python3 - "$DUMP" <<'PY'
import sys,xml.etree.ElementTree as ET
nodes=[x for x in ET.parse(sys.argv[1]).getroot().iter('node') if x.attrib.get('text')=='拍照並儲存']
sys.exit(0 if nodes and nodes[0].attrib.get('enabled')=='true' else 1)
PY
    then camera_ready=true; break; fi
  fi
  sleep 2
done
if [[ "$camera_ready" != true ]]; then
  adb exec-out screencap -p > "$OUT/api$API-camera-failed.png" || true
  echo "FAIL: camera preview not ready on API $API"
  exit 1
fi
tap_text '拍照並儲存'
sleep 3
tap_text '關閉相機'
sleep 1
adb shell ls "/sdcard/Android/data/$APP/files/photos" | grep -q '\.jpg'
adb exec-out screencap -p > "$OUT/api$API-photo-saved.png"
echo "PASS: API $API camera photo saved"
adb logcat -d -s AndroidRuntime:E Record2:E > "$OUT/api$API-errors.log" || true
echo "PASS: API $API core app regression"
