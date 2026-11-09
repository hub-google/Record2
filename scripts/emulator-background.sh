#!/usr/bin/env bash
set -euo pipefail
# Authorized, visibly notified foreground services must survive leaving the UI.
APP=com.hubgoogle.record2
EVIDENCE=docs/test-evidence
mkdir -p "$EVIDENCE"
adb shell pm grant "$APP" android.permission.RECORD_AUDIO
adb shell pm grant "$APP" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$APP" android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant "$APP" android.permission.POST_NOTIFICATIONS || true
adb shell cmd location set-location-enabled true || true
adb shell am force-stop "$APP"
adb shell am start -W -n "$APP/.MainActivity" >/dev/null
sleep 2
click_text() {
  local wanted="$1"
  adb shell uiautomator dump /sdcard/record2-background.xml >/dev/null
  adb pull /sdcard/record2-background.xml /tmp/record2-background.xml >/dev/null
  python3 - "$wanted" <<'PY'
import re,subprocess,sys,xml.etree.ElementTree as ET
for n in ET.parse('/tmp/record2-background.xml').getroot().iter('node'):
    if n.attrib.get('text','')==sys.argv[1]:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        sys.exit(0)
raise SystemExit('FAIL: button missing: '+sys.argv[1])
PY
}
click_text '開始錄音'
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 12
adb shell dumpsys activity services "$APP" > "$EVIDENCE/15-background-audio-service.txt"
if ! grep -q AudioRecordService "$EVIDENCE/15-background-audio-service.txt"; then
  echo 'FAIL: audio service stopped when leaving app'
  exit 1
fi
adb exec-out screencap -p > "$EVIDENCE/15-background-audio-home.png"
adb shell am start -n "$APP/.MainActivity" >/dev/null
sleep 2
click_text '停止錄音'
sleep 2
mkdir -p /tmp/record2-background-audio
adb pull "/sdcard/Android/data/$APP/files/audio" /tmp/record2-background-audio >/dev/null
python3 - <<'PY'
from pathlib import Path
files=list(Path('/tmp/record2-background-audio/audio').glob('*.m4a'))
assert files and max(f.stat().st_size for f in files)>3000, 'No recording data after leaving app'
print('PASS: visible, authorized audio foreground service survived Home and saved media')
PY
click_text '開始定位'
sleep 2
adb shell input keyevent KEYCODE_HOME
adb emu geo fix 121.5654 25.0330 || true
sleep 12
adb shell dumpsys activity services "$APP" > "$EVIDENCE/16-background-location-service.txt"
if ! grep -q LocationTrackService "$EVIDENCE/16-background-location-service.txt"; then
  echo 'FAIL: location service stopped when leaving app'
  exit 1
fi
adb exec-out screencap -p > "$EVIDENCE/16-background-location-home.png"
adb shell am start -n "$APP/.MainActivity" >/dev/null
sleep 2
click_text '停止定位'
sleep 2
adb pull "/sdcard/Android/data/$APP/files/events/events.jsonl" /tmp/record2-background-events.jsonl >/dev/null
python3 - <<'PY'
import json
with open('/tmp/record2-background-events.jsonl') as f:
    events=[json.loads(x) for x in f if x.strip()]
assert any(e.get('type')=='location_point' for e in events), 'No recorded location points'
print('PASS: visible, authorized location foreground service survived Home and logged position')
PY
