#!/usr/bin/env bash
set -euo pipefail
APP=com.hubgoogle.record2
# The Android 15 emulator should not start sensors when permissions were revoked.
adb shell am force-stop "$APP"
adb shell pm revoke "$APP" android.permission.RECORD_AUDIO
adb shell pm revoke "$APP" android.permission.CAMERA
adb shell pm revoke "$APP" android.permission.ACCESS_FINE_LOCATION
adb shell pm revoke "$APP" android.permission.ACCESS_COARSE_LOCATION
adb shell am start -n "$APP/.MainActivity" >/dev/null
sleep 2
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb shell cat /sdcard/window.xml | grep -q '開始錄音'
# Explicit denial via Android system permission dialogue rather than broad UI bypasses.
click_text() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml /tmp/record2-denial.xml >/dev/null
  python3 - "$1" <<'PY'
import sys,xml.etree.ElementTree as ET,re,subprocess
text=sys.argv[1]
for node in ET.parse('/tmp/record2-denial.xml').getroot().iter('node'):
    if node.attrib.get('text') == text:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        break
else: raise SystemExit('UI text missing: '+text)
PY
}
click_deny() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml /tmp/record2-denial.xml >/dev/null
  python3 - <<'PY'
import xml.etree.ElementTree as ET,re,subprocess
nodes=list(ET.parse('/tmp/record2-denial.xml').getroot().iter('node'))
for node in nodes:
    name=node.attrib.get('text','')
    if name.casefold() in ("don't allow",'don’t allow','不允許','拒絕') or 'don\'t allow' in name.casefold():
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        break
else:
    raise SystemExit('Refuse permission button not found: '+str([n.attrib.get('text','') for n in nodes if n.attrib.get('text')]))
PY
}
click_text '開始錄音'
sleep 2
click_deny
sleep 1
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb shell cat /sdcard/window.xml | grep -q '開始錄音'
if adb shell dumpsys activity services "$APP" | grep -q 'AudioRecordService'; then echo 'Audio service remained active without permission'; exit 1; fi
click_text '開啟相機'
sleep 2
click_deny
sleep 1
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb shell cat /sdcard/window.xml | grep -q '開啟相機'
if adb shell dumpsys activity activities | grep -q 'mResumedActivity.*CameraActivity'; then echo 'Camera screen opened without permission'; exit 1; fi
adb exec-out screencap -p > docs/test-evidence/09-permission-denied.png
printenv | grep -q 'CI' || true
echo 'PASS: denied mic/camera requests did not start sensor access'
