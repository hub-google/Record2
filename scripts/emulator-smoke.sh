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
# Emulator cold-boot screens, first-launch transitions and package-manager grants are
# not synchronized. Explicitly wake/unlock and collect diagnostics on failure.
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell am force-stop "$APP" || true
adb shell am start -W -n "$APP/.MainActivity" || true
home_ready=false
for attempt in $(seq 1 45); do
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  if adb shell cat /sdcard/window.xml 2>/dev/null | grep -q '開始錄音'; then
    home_ready=true
    break
  fi
  if ((attempt % 10 == 0)); then
    echo "Waiting for Record2 home screen ($attempt/45)"
    adb shell input keyevent KEYCODE_WAKEUP || true
    adb shell wm dismiss-keyguard || true
    adb shell am start -n "$APP/.MainActivity" || true
  fi
  sleep 2
done
if [[ "$home_ready" != true ]]; then
  echo 'FAIL: Home screen not ready; collecting emulator diagnostics'
  adb exec-out screencap -p > docs/test-evidence/00-startup-failure.png || true
  adb logcat -d -t 6000 > docs/test-evidence/startup-logcat.txt || true
  adb shell dumpsys activity activities > docs/test-evidence/startup-activity.txt || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml docs/test-evidence/startup-window.xml || true
  exit 1
fi
sleep 2
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
adb shell cmd location set-location-enabled true || true
adb emu geo fix 121.5654 25.0330 || true
sleep 8
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
sleep 3
adb exec-out screencap -p > docs/test-evidence/06-saved-files.png
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml /tmp/record2-after.xml >/dev/null
python3 - <<'PY'
import xml.etree.ElementTree as ET
texts=[n.attrib.get('text','') for n in ET.parse('/tmp/record2-after.xml').getroot().iter('node')]
assert any('錄音檔：1' in t and '照片：1' in t for t in texts), 'Recorded audio/photo count not incremented'
print('PASS: audio and JPEG saved and visible in local file summary')
PY
adb shell dumpsys activity services "$APP" > docs/test-evidence/services.txt || true
adb logcat -d -s Record2:E AndroidRuntime:E > docs/test-evidence/error-log.txt || true
python3 - <<'PY'
from pathlib import Path
for name in ('01-home.png','02-recording.png','03-location.png','04-camera.png','05-photo.png','06-saved-files.png'):
    p=Path('docs/test-evidence')/name
    assert p.exists() and p.stat().st_size>1000, name
print('PASS: six screenshots captured from Android emulator')
PY

# Regression: real playback, JPEG decode, and in-app file access.
click_text '管理錄音與照片'
sleep 2
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb shell cat /sdcard/window.xml | grep -q '錄音：1' || { echo 'Audio library did not show saved audio'; exit 1; }
click_text '播放'
sleep 1
adb exec-out screencap -p > docs/test-evidence/07-audio-library.png
click_text '照片清單'
sleep 1
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb shell cat /sdcard/window.xml | grep -q '照片：1' || { echo 'Photo library did not show saved photo'; exit 1; }
click_text '檢視'
sleep 1
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb shell cat /sdcard/window.xml | grep -q '關閉' || { echo 'Photo preview dialog did not open'; exit 1; }
adb exec-out screencap -p > docs/test-evidence/08-photo-preview.png
click_text '關閉'

# Validate underlying bytes: MP4 atoms + JPEG markers; no assumption that a file count is sufficient.
mkdir -p /tmp/record2-media
adb pull /sdcard/Android/data/$APP/files/audio /tmp/record2-media/ >/dev/null
adb pull /sdcard/Android/data/$APP/files/photos /tmp/record2-media/ >/dev/null
python3 - <<'PY'
from pathlib import Path
base=Path('/tmp/record2-media')
audio=list((base/'audio').glob('*.m4a'))
photos=list((base/'photos').glob('*.jpg'))
assert len(audio)==1 and len(photos)==1, (audio,photos)
a=audio[0].read_bytes()
assert len(a)>1500 and a[4:8]==b'ftyp', 'recording is not an MP4/M4A container'
assert b'moov' in a and b'mdat' in a, 'M4A audio lacks required media atoms'
p=photos[0].read_bytes()
assert len(p)>1000 and p.startswith(b'\xff\xd8\xff') and p.endswith(b'\xff\xd9'), 'JPEG is invalid'
print('PASS: M4A container structure and JPEG signatures verified')
PY
if command -v ffprobe >/dev/null 2>&1; then
  ffprobe -v error -select_streams a -show_entries stream=codec_name -of default=noprint_wrappers=1 /tmp/record2-media/audio/*.m4a | grep -q 'codec_name=aac'
  echo 'PASS: M4A decoded as AAC by ffprobe'
else
  echo 'SKIP: ffprobe not available (M4A container structure verified)'
fi
