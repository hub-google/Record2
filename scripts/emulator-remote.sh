#!/usr/bin/env bash
set -euo pipefail
# End-to-end authorization tests for loopback-only RemoteControlService.
# No token is stored in artifacts or echoed to Actions logs.
APP=com.hubgoogle.record2
OUT=docs/test-evidence
mkdir -p "$OUT"
adb shell am start -n "$APP/.MainActivity" >/dev/null
sleep 2
adb shell input swipe 480 1500 480 520 450
sleep 1
tap_text() {
  local expected="$1"
  for attempt in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/record2-remote-ui.xml >/dev/null 2>&1
    adb pull /sdcard/record2-remote-ui.xml /tmp/record2-remote-ui.xml >/dev/null 2>&1
    if python3 - "$expected" <<'PY'
import re,subprocess,sys,xml.etree.ElementTree as ET
wanted=sys.argv[1]
for n in ET.parse('/tmp/record2-remote-ui.xml').getroot().iter('node'):
    if n.attrib.get('text','')==wanted:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
        if y2<=0 or y1>=2250: break
        subprocess.check_call(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
        sys.exit(0)
sys.exit(1)
PY
    then return 0; fi
    adb shell input swipe 480 1700 480 550 350
    sleep 1
  done
  echo "FAIL: Cannot tap $expected"
  cp /tmp/record2-remote-ui.xml "$OUT/17-remote-ui-failed.xml" || true
  exit 1
}
tap_text '啟用遠端請求'
sleep 3
adb forward tcp:8765 tcp:8765
trap 'adb forward --remove tcp:8765 >/dev/null 2>&1 || true' EXIT
TOKEN=$(adb shell run-as "$APP" cat shared_prefs/remote_access.xml |
    python3 -c 'import re,sys;s=sys.stdin.read();m=re.search(r"[0-9a-f]{64}",s);print(m.group(0) if m else "")')
if [[ $(printf %s "$TOKEN" | wc -c) -ne 64 ]]; then echo "FAIL: missing private access token"; exit 1; fi
BASE=http://127.0.0.1:8765
auth() { curl --silent --show-error --max-time 8 -H "Authorization: Bearer $TOKEN" "$@"; }
CODE=$(curl --silent --max-time 8 -o /tmp/remote-unauthorized.json -w '%{http_code}' "$BASE/v1/status")
if [[ "$CODE" != 401 ]]; then echo "FAIL: unauthorized status returned HTTP $CODE"; exit 1; fi
echo 'PASS: no-token remote request rejected with HTTP 401'
auth "$BASE/v1/status" >/tmp/remote-status.json
python3 - <<'PY'
import json
obj=json.load(open('/tmp/remote-status.json'))
assert obj['connected'] is True and obj['approval_required'] is True
assert obj['camera_requires_visible_screen'] is True
print('PASS: authorized loopback status and consent constraints')
PY
auth -X POST "$BASE/v1/requests/audio_start" >/tmp/remote-request.json
ID=$(python3 -c 'import json;print(json.load(open("/tmp/remote-request.json"))["id"])')
sleep 3
adb exec-out screencap -p > "$OUT/17-remote-audio-consent.png"
auth "$BASE/v1/status" > /tmp/remote-status.json
python3 - <<'PY'
import json
assert not json.load(open('/tmp/remote-status.json'))['audio_running'], 'Remote request started audio before consent'
print('PASS: pending remote request does not start recording')
PY
tap_text '拒絕'
sleep 1
auth "$BASE/v1/requests/$ID" >/tmp/remote-request-status.json
python3 - <<'PY'
import json
assert json.load(open('/tmp/remote-request-status.json'))['status']=='denied'
print('PASS: device owner denied remote microphone request')
PY
auth -X POST "$BASE/v1/requests/audio_start" >/tmp/remote-request.json
ID=$(python3 -c 'import json;print(json.load(open("/tmp/remote-request.json"))["id"])')
sleep 3
tap_text '同意並執行'
sleep 3
auth "$BASE/v1/status" >/tmp/remote-status.json
python3 - <<'PY'
import json
assert json.load(open('/tmp/remote-status.json'))['audio_running'] is True
print('PASS: microphone starts only after in-app approval')
PY
auth "$BASE/v1/requests/$ID" >/tmp/remote-request-status.json
python3 - <<'PY'
import json
assert json.load(open('/tmp/remote-request-status.json'))['status']=='approved_to_attempt'
PY
auth -X POST "$BASE/v1/requests/audio_stop" >/tmp/remote-request.json
sleep 3
tap_text '同意並執行'
sleep 3
auth "$BASE/v1/status" >/tmp/remote-status.json
python3 - <<'PY'
import json
assert json.load(open('/tmp/remote-status.json'))['audio_running'] is False
print('PASS: remote audio stop also requires phone approval')
PY
auth -X POST "$BASE/v1/requests/location_start" >/tmp/remote-request.json
sleep 3
tap_text '同意並執行'
sleep 3
auth "$BASE/v1/status" >/tmp/remote-status.json
python3 - <<'PY'
import json
assert json.load(open('/tmp/remote-status.json'))['location_running'] is True
print('PASS: location tracking starts only after device approval')
PY
auth -X POST "$BASE/v1/requests/location_stop" >/tmp/remote-request.json
sleep 3
tap_text '同意並執行'
sleep 3
auth "$BASE/v1/status" >/tmp/remote-status.json
python3 - <<'PY'
import json
assert json.load(open('/tmp/remote-status.json'))['location_running'] is False
print('PASS: location tracking stops after device approval')
PY
auth -X POST "$BASE/v1/requests/camera_open" >/tmp/remote-request.json
sleep 3
tap_text '拒絕'
adb exec-out screencap -p > "$OUT/18-remote-camera-denied.png"
if adb shell dumpsys activity activities | grep -q 'mResumedActivity.*CameraActivity'; then
  echo 'FAIL: camera opened after denied remote request'
  exit 1
fi
echo 'PASS: denied remote camera request did not open camera'
tap_text '關閉遠端請求'
sleep 2
if curl -sS --max-time 3 "$BASE/v1/status" >/dev/null 2>&1; then
  echo 'FAIL: local bridge still accepting requests after user disabled it'
  exit 1
fi
adb exec-out screencap -p > "$OUT/19-remote-disabled.png"
echo 'PASS: remote loopback bridge closes when disabled by the owner'
