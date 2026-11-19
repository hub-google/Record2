#!/usr/bin/env bash
set -euo pipefail
# Android 15 emulator: explicit consent, persistent camera foreground service,
# screen lock, STOP action, finalized MP4 and user-visible library.
APP=com.hubgoogle.record2
OUT=docs/test-evidence
BASE=/sdcard/Android/data/$APP/files/videos
mkdir -p "$OUT" /tmp/record2-videos
adb shell am start -n "$APP/.MainActivity" >/dev/null
sleep 2
# Start from the top before looking for the owner-facing recording button.
adb shell input swipe 500 450 500 1800 350
sleep 1
tap_text() {
  local wanted="$1"
  for attempt in 1 2 3 4 5 6 7 8; do
    adb shell uiautomator dump /sdcard/record2-video.xml >/dev/null 2>&1
    adb pull /sdcard/record2-video.xml /tmp/record2-video-ui.xml >/dev/null 2>&1
    if python3 - "$wanted" <<'PY'
import re,subprocess,sys,xml.etree.ElementTree as ET
wanted=sys.argv[1]
nodes=list(ET.parse('/tmp/record2-video-ui.xml').getroot().iter('node'))
for node in nodes:
    if node.attrib.get('text') == wanted and node.attrib.get('bounds'):
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
        if x2>x1 and y2>y1 and 0<y1<2200:
            subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
            sys.exit(0)
sys.exit(1)
PY
    then return 0; fi
    adb shell input swipe 480 1700 480 550 350
    sleep 1
  done
  echo "FAIL: UI control not found: $wanted"
  cp /tmp/record2-video-ui.xml "$OUT/20-video-ui-failed.xml" || true
  exit 1
}
tap_text '開始背景錄影'
sleep 1
tap_text '開始錄影'
sleep 10
adb exec-out screencap -p > "$OUT/20-video-recording.png"
if ! adb shell dumpsys activity services "$APP" | grep -q 'VideoRecordService'; then
  echo 'FAIL: camera foreground service is not running'
  exit 1
fi
if ! adb shell ls "$BASE" 2>/dev/null | grep -q '\.mp4\.partial'; then
  echo 'FAIL: expected in-progress video file was not created'
  exit 1
fi
echo 'PASS: explicit consent starts visible video foreground service'
adb shell input keyevent KEYCODE_HOME
sleep 6
adb exec-out screencap -p > "$OUT/21-video-background-home.png"
if ! adb shell dumpsys activity services "$APP" | grep -q 'VideoRecordService'; then
  echo 'FAIL: video foreground service did not survive switching apps'
  exit 1
fi
adb shell input keyevent KEYCODE_SLEEP
sleep 8
if ! adb shell dumpsys activity services "$APP" | grep -q 'VideoRecordService'; then
  echo 'FAIL: video service did not survive a short locked-screen interval'
  exit 1
fi
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell am start -n "$APP/.MainActivity" >/dev/null
sleep 2
tap_text '停止背景錄影'
sleep 6
adb pull "$BASE" /tmp/record2-videos/ >/dev/null
python3 - <<'PY'
from pathlib import Path
root=Path('/tmp/record2-videos/videos')
videos=list(root.glob('*.mp4'))
partial=list(root.glob('*.partial'))
assert len(videos)==1, f'Expected one finalized MP4, got {videos}'
assert not partial, f'Incomplete recordings remain: {partial}'
data=videos[0].read_bytes()
assert len(data)>4096 and data[4:8]==b'ftyp', 'MP4 file empty or missing ftyp'
assert b'moov' in data and b'mdat' in data, 'MP4 missing video container atoms'
print('PASS: screen-off foreground capture finalized a nonempty MP4')
PY
adb exec-out screencap -p > "$OUT/22-video-finished.png"
tap_text '管理錄音、照片與影片'
sleep 1
tap_text '影片清單'
sleep 1
adb shell uiautomator dump /sdcard/record2-video.xml >/dev/null 2>&1
adb shell cat /sdcard/record2-video.xml | grep -q '影片：1 個' || {
  echo 'FAIL: finalized video missing from media library'
  exit 1
}
adb exec-out screencap -p > "$OUT/23-video-library.png"
echo 'PASS: MP4 appears in on-device video library'
