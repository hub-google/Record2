#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path('app/src/main')
manifest=ET.parse(root/'AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
perms={x.attrib[ns+'name'] for x in manifest.findall('uses-permission')}
expected={'android.permission.CAMERA','android.permission.RECORD_AUDIO',
 'android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION',
 'android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_MICROPHONE',
 'android.permission.FOREGROUND_SERVICE_LOCATION','android.permission.FOREGROUND_SERVICE_DATA_SYNC',
 'android.permission.INTERNET'}
assert expected<=perms,expected-perms
app=manifest.find('application')
assert app.attrib[ns+'allowBackup']=='false'
assert app.attrib[ns+'usesCleartextTraffic']=='false'
services=app.findall('service')
assert len(services)==3
assert {x.attrib[ns+'foregroundServiceType'] for x in services}=={'microphone','location','dataSync'}
assert all(x.attrib[ns+'exported']=='false' for x in services)
java=root/'java/com/hubgoogle/record2'
for name in ['MainActivity','CameraActivity','AudioRecordService','LocationTrackService','Store','Notifications','MediaLibraryActivity','RemoteControlService']:
    assert (java/(name+'.java')).exists(),name
assert 'createCaptureSession' in (java/'CameraActivity.java').read_text()
assert 'MediaRecorder' in (java/'AudioRecordService.java').read_text()
assert 'requestLocationUpdates' in (java/'LocationTrackService.java').read_text()
assert 'Intent.ACTION_SEND' in (java/'MainActivity.java').read_text()
assert 'Intent.ACTION_CREATE_DOCUMENT' in (java/'MediaLibraryActivity.java').read_text()
assert 'MediaPlayer' in (java/'MediaLibraryActivity.java').read_text()
assert 'BitmapFactory.decodeFile' in (java/'MediaLibraryActivity.java').read_text()
assert 'startForeground(' in (java/'AudioRecordService.java').read_text()
assert 'startForeground(' in (java/'LocationTrackService.java').read_text()
assert 'android.intent.category.LAUNCHER' in (root/'AndroidManifest.xml').read_text()
remote=(java/'RemoteControlService.java').read_text()
assert '127.0.0.1' in remote and 'ServerSocket' in remote
assert 'MessageDigest.isEqual' in remote and 'new SecureRandom()' in remote
assert 'approved_to_attempt' in remote and 'Unauthorized' in remote
assert 'remotePromptShowing' in (java/'MainActivity.java').read_text()
assert not any(x.attrib.get(ns+'exported')=='true' and
  x.attrib.get(ns+'name')=='.RemoteControlService' for x in services)
assert app.attrib[ns+'usesCleartextTraffic']=='false'
print('PASS: manifest permissions, service export, sensor access, file storage, share, privacy defaults')
PY
