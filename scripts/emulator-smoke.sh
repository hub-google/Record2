#!/usr/bin/env bash
set -euo pipefail
# Requires Android emulator + adb, executed by GitHub Actions.
mkdir -p docs/test-evidence
APK=app/build/outputs/apk/debug/app-debug.apk
adb install -r "$APK"
APP=com.hubgoogle.record2
adb shell pm grant "$APP" android.permission.RECORD_AUDIO
adb shell pm grant "$APP" android.permission.CAMERA
adb shell pm grant "$APP" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$APP" android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant "$APP" android.permission.POST_NOTIFICATIONS || true
adb shell am start -n "$APP/.MainActivity"
sleep 3
adb exec-out screencap -p > docs/test-evidence/01-home.png
click_text() {
  local expected="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml /tmp/record2-window.xml >/dev/null
  python3 - "$expected" <<'PY'
import sys,xml.etree.ElementTree as ET,re,subprocess
root=ET.parse('/tmp/record2-window.xml').getroot()
for node in root.iter('node'):
    if node.attrib.get('text') == sys.argv[1]:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        sys.exit(0)
raise SystemExit('BUTTON NOT FOUND: '+sys.argv[1])
PY
}
click_text '開始錄音'
sleep 4
adb exec-out screencap -p > docs/test-evidence/02-recording.png
click_text '停止錄音'
sleep 2
click_text '開始定位'
adb emu geo fix 121.5654 25.0330 || true
sleep 5
adb exec-out screencap -p > docs/test-evidence/03-location.png
click_text '停止定位'
sleep 1
click_text '開啟相機'
sleep 6
adb exec-out screencap -p > docs/test-evidence/04-camera.png
click_text '拍照並儲存'
sleep 3
adb exec-out screencap -p > docs/test-evidence/05-photo.png
click_text '關閉相機'
sleep 2
adb shell dumpsys activity services "$APP" > docs/test-evidence/services.txt || true
adb logcat -d -s Record2:E AndroidRuntime:E > docs/test-evidence/error-log.txt || true
python3 - <<'PY'
from pathlib import Path
for name in ('01-home.png','02-recording.png','03-location.png','04-camera.png','05-photo.png'):
    p=Path('docs/test-evidence')/name
    assert p.exists() and p.stat().st_size>1000, name
print('PASS: five screenshots captured from Android emulator')
PY
