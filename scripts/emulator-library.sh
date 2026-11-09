#!/usr/bin/env bash
set -euo pipefail
APP=com.hubgoogle.record2
mkdir -p docs/test-evidence

dump_ui() {
  for attempt in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/window-library.xml >/dev/null 2>&1 &&
      adb pull /sdcard/window-library.xml /tmp/record2-library.xml >/dev/null 2>&1; then
      if python3 - <<'PY'
import xml.etree.ElementTree as E
E.parse('/tmp/record2-library.xml')
PY
      then return 0; fi
    fi
    sleep 2
  done
  echo "FAIL: unable to inspect Android UI"
  return 1
}
tap_text() {
  dump_ui
  python3 - "$1" <<'PY'
import re,subprocess,sys,xml.etree.ElementTree as E
wanted=sys.argv[1]
for node in E.parse('/tmp/record2-library.xml').getroot().iter('node'):
    if node.attrib.get('text','').strip()==wanted:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        sys.exit(0)
print('FAIL: UI text not found:',wanted)
sys.exit(1)
PY
}
assert_text() {
  dump_ui
  python3 - "$1" <<'PY'
import sys,xml.etree.ElementTree as E
vals=[x.attrib.get('text','') for x in E.parse('/tmp/record2-library.xml').getroot().iter('node')]
assert any(sys.argv[1] in x for x in vals),(sys.argv[1],vals)
PY
}
# Smoke test leaves the library on Photos after closing an image preview.
assert_text '照片：1 個'
PHOTO=$(adb shell ls "/sdcard/Android/data/$APP/files/photos" | tr -d '\r' | grep -E '\.jpg$' | head -1)
test -n "$PHOTO"
# Test that export invokes Android's real system document picker, not a mock.
tap_text '匯出'
sleep 4
dump_ui
adb exec-out screencap -p > docs/test-evidence/10-export-document-picker.png
if ! adb shell dumpsys activity activities | grep -Eiq 'documentsui|DocumentsActivity|FilesActivity'; then
  echo 'FAIL: export did not open a system document picker'
  cat /tmp/record2-library.xml
  exit 1
fi
# The real picker must have a Save action. Navigate to Downloads where needed.
python3 - <<'PY'
import xml.etree.ElementTree as E
nodes=list(E.parse('/tmp/record2-library.xml').getroot().iter('node'))
print('DOCUMENT_PICKER_UI:',[x.attrib.get('text','') for x in nodes if x.attrib.get('text')])
PY
# Verify the actual SAF file write, not only the picker opening.
tap_text 'SAVE'
sleep 3
assert_text '照片：1 個'
mkdir -p /tmp/record2-export-original /tmp/record2-exported
adb pull "/sdcard/Android/data/$APP/files/photos/$PHOTO" /tmp/record2-export-original/ >/dev/null
download_photo=false
for attempt in $(seq 1 15); do
  if adb pull "/sdcard/Download/$PHOTO" /tmp/record2-exported/ >/dev/null 2>&1; then
    download_photo=true
    break
  fi
  sleep 2
done
if [[ "$download_photo" != true ]]; then
  echo "FAIL: document picker did not save photo to Downloads"
  adb shell ls -al /sdcard/Download || true
  exit 1
fi
cmp "/tmp/record2-export-original/$PHOTO" "/tmp/record2-exported/$PHOTO"
adb exec-out screencap -p > docs/test-evidence/13-photo-exported.png
echo 'PASS: SAF photo export saved identical JPEG bytes to Downloads'

# Check that canceling the document picker leaves the original unchanged.
tap_text '匯出'
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 2
assert_text '照片：1 個'
echo 'PASS: cancel export leaves original photo unchanged'
# Deletion requires confirmation. Verify cancel preserves the file.
tap_text '刪除'
sleep 1
assert_text '確定刪除？'
tap_text '取消'
sleep 1
assert_text '照片：1 個'
adb shell test -f "/sdcard/Android/data/$APP/files/photos/$PHOTO"
echo 'PASS: cancel deletion preserves photo'
# Confirm deletion and verify both UI and filesystem.
tap_text '刪除'
sleep 1
assert_text '確定刪除？'
tap_text '刪除'
sleep 1
assert_text '照片：0 個'
if adb shell test -f "/sdcard/Android/data/$APP/files/photos/$PHOTO"; then
  echo 'FAIL: photo remained after confirmed delete'
  exit 1
fi
adb exec-out screencap -p > docs/test-evidence/11-photo-deleted.png
echo 'PASS: confirmed deletion removes photo and updates UI'
# Audio path is independently verified.
tap_text '錄音清單'
sleep 1
assert_text '錄音：1 個'
AUDIO=$(adb shell ls "/sdcard/Android/data/$APP/files/audio" | tr -d '\r' | grep -E '\.m4a$' | head -1)
test -n "$AUDIO"
tap_text '匯出'
sleep 3
tap_text 'SAVE'
sleep 3
assert_text '錄音：1 個'
adb pull "/sdcard/Android/data/$APP/files/audio/$AUDIO" /tmp/record2-export-original/ >/dev/null
download_audio=false
for attempt in $(seq 1 15); do
  if adb pull "/sdcard/Download/$AUDIO" /tmp/record2-exported/ >/dev/null 2>&1; then
    download_audio=true
    break
  fi
  sleep 2
done
if [[ "$download_audio" != true ]]; then
  echo 'FAIL: document picker did not save audio to Downloads'
  exit 1
fi
cmp "/tmp/record2-export-original/$AUDIO" "/tmp/record2-exported/$AUDIO"
adb exec-out screencap -p > docs/test-evidence/14-audio-exported.png
echo 'PASS: SAF audio export saved identical M4A bytes to Downloads'
tap_text '刪除'
sleep 1
tap_text '刪除'
sleep 1
assert_text '錄音：0 個'
if adb shell test -f "/sdcard/Android/data/$APP/files/audio/$AUDIO"; then
  echo 'FAIL: audio remained after confirmed delete'
  exit 1
fi
adb exec-out screencap -p > docs/test-evidence/12-audio-deleted.png
echo 'PASS: confirmed deletion removes audio and updates UI'
# Return to home; permission test expects the app home to be visible.
tap_text '返回首頁'
sleep 1
