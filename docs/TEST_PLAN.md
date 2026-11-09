# Record2 測試計畫與執行紀錄

## 環境與範圍

- Android：API 26–35 支援性目標；CI 使用 API 35 模擬器。跨品牌相容性待實機覆核。
- 資料：只儲存在本機，沒有後端、帳號、伺服器同步功能。
- 重要：不測試也不提供無提示啟用攝錄、隱形安裝、規避系統指示器的能力。

| ID | 操作步驟 | 預期結果 | 實際狀態 |
|---|---|---|---|
| T01 | 啟動 App | 首頁看到錄音／定位／相機／分享 | 靜態檢查通過；模擬器待驗證 |
| T02 | 首次按錄音並授權 | 顯示 Android 權限要求 | 程式路徑已檢查；模擬器待驗證 |
| T03 | 開始錄音／返回首頁 | 前景服務通知持續顯示 | 程式路徑已檢查；模擬器待驗證 |
| T04 | 停止錄音 | 儲存 M4A 檔，數量增加 | 程式路徑已檢查；模擬器待驗證 |
| T05 | 開始定位 | 顯示系統通知，等待位置 | 程式路徑已檢查；模擬器待驗證 |
| T06 | 透過 emulator geo fix 注入位置 | 最近位置更新，JSONL 多一筆 | 模擬器待驗證 |
| T07 | 停止定位 | 位置服務停止，不再接收更新 | 程式路徑已檢查；模擬器待驗證 |
| T08 | 開相機、拍照 | 可見預覽，儲存 JPEG | 程式路徑已檢查；模擬器待驗證 |
| T09 | 返回首頁 | 相機硬體與資源釋放 | 程式路徑已檢查；模擬器待驗證 |
| T10 | 不授權麥克風／位置／相機 | 功能無法擅自啟動，顯示說明 | 程式路徑已檢查；模擬器待驗證 |
| T11 | 分享紀錄摘要 | 系統分享選單顯示文字 | 程式路徑已檢查；模擬器待驗證 |
| T12 | 實機關閉位置服務／切換 App／耗電優化 | 能合理處理定位缺席與系統限制 | 待不同廠牌實機測試 |
| T13 | 安裝在 Android 8、11、12、13、14、15 | 權限／感測器差異合理處理 | 只有 API 35 排入 CI，其他待驗證 |
| T14 | 相機缺席裝置 | 明確告知無相機，不崩潰 | 程式路徑已檢查；模擬器待驗證 |

## 靜態測試

執行 `bash scripts/test-static.sh`：**PASS**（2026-11-06，本機原始碼靜態檢查）。確認 Manifest 能解析、必要權限、服務不可外部啟動、離線儲存／通知／相機處理程式路徑存在。這不是 APK 編譯測試，也不是裝置操作測試。

## 操作截圖（由 GitHub Actions 模擬器產生）

當 CI 成功，截圖將存於：

- `docs/test-evidence/01-home.png`：首頁
- `docs/test-evidence/02-recording.png`：錄音啟動後
- `docs/test-evidence/03-location.png`：定位啟動後
- `docs/test-evidence/04-camera.png`：相機預覽
- `docs/test-evidence/05-photo.png`：拍照後

**目前不將示意圖充當實際操作截圖。** 正式 PASS/FAIL 及截圖須以 CI 成功執行後的 GitHub Actions 紀錄為準。

## 已知限制

- 音質因手機麥克風、環境與 Android 音訊策略而異，不保證無損。
- Android 12+ 顯示相機／麥克風使用指示；通知為預期的可見行為。
- 不申請 ACCESS_BACKGROUND_LOCATION；定位前景服務必須由使用者從畫面啟動。
- 使用 Android 原生 Camera2，不同裝置圖像旋轉與尺寸仍需實機檢查。
- 這是最小可運作版本，不包含遠端控制、後端同步、登入與加密資料庫。

## 2026-11-09 回歸測試擴充

上次測試記錄不足以證明音檔可回放、JPEG 能解碼、拒絕權限時不會啟動感測器，因此此次補上以下**需要實際執行的測試**（未執行前不能列為 PASS）：

| ID | 測試 | 驗證方式 |
| --- | --- | --- |
| R01 | 本機錄音檔可瀏覽 | 首頁開啟檔案管理，清單出現 `錄音：1` |
| R02 | 錄音可播放 | 點「播放」，MediaPlayer 能準備並開始播放；需確認 UI 與錯誤日誌 |
| R03 | 照片可預覽 | 切到照片清單、點「檢視」，以 BitmapFactory 解碼圖片並出現視窗 |
| R04 | M4A 實際有資料 | ADB 抽取錄音，驗證 ftyp/moov/mdat；有 ffprobe 時驗證 AAC |
| R05 | JPEG 有資料且可解碼 | ADB 抽取圖片，驗證 JPEG 標頭／結尾與 R03 |
| R06 | 無麥克風權限時不會錄音 | `pm revoke` 後按開始錄音，點「Don't allow」，確認服務不啟動 |
| R07 | 無相機權限時不會開鏡頭 | `pm revoke` 後按開鏡頭，點「Don't allow」，確認仍留首頁 |
| R08 | 匯出檔案 | 手動選擇位置、儲存、由檔案管理員驗證內容；需實機覆核 |
| R09 | 刪除檔案 | 點刪除後取消／確認，數量與實體檔案一致；需實機覆核 |
| R10 | 切換前背景／相機旋轉／多次重開 | 相機資源釋放且不當機；需實機覆核 |

自動化新增 `scripts/emulator-permissions.sh`，其輸出與螢幕截圖會保留到 `docs/test-evidence/`。**此區段是測試設計，不是通過證明。**


## 2026-11-09 實際回歸測試報告（最終 CI）

- **執行紀錄**：[GitHub Actions #42543281609](https://github.com/hub-google/Record2/actions/runs/42543281609)
- **執行提交**：`3096c7c42e03fa1841458af2ee61ebba88238dab`
- **結果**：GitHub Actions Job **SUCCESS**（完整編譯、Android API 35 模擬器腳本執行、證據上傳成功）
- **平台**：Android 15／API 35 x86_64 Google APIs Emulator、JDK 17、Gradle 8.11.1
- **APK**：Actions Artifacts → `Record2-debug-APK`
- **測試檔**：Actions Artifacts → `Record2-test-evidence`；共 **9 張真實模擬器 PNG**，已同步提交 `docs/test-evidence/`。
- **錯誤日誌**：`docs/test-evidence/error-log.txt` 是空檔，只代表該腳本的 Logcat 過濾條件未擷取錯誤，不能據此保證沒有任何潛在問題。

| 測試項目 | 結果 | 證據與限制 |
|---|---|---|
| Debug APK 編譯／安裝／啟動 | **PASS** | GitHub Actions build + ADB 安裝、開啟首頁 |
| 錄音啟動與停止 | **PASS** | [02 錄音進行中](test-evidence/02-recording.png)、[06 儲存後計數](test-evidence/06-saved-files.png) |
| M4A 檔案有實際內容 | **PASS（基本格式）** | ADB 抽取檔案並驗證 `ftyp`／`moov`／`mdat`。**ffprobe 因 runner 未安裝而 SKIP，未驗證音訊品質** |
| 錄音列表與播放器 | **PASS（操作）** | [07 錄音檔案清單](test-evidence/07-audio-library.png)，呼叫 Android MediaPlayer 後顯示播放提示；未進行聽感與音質量測 |
| GPS 位置與停止 | **PASS（模擬座標）** | [03 定位](test-evidence/03-location.png)、[06 返回首頁](test-evidence/06-saved-files.png)；注入 `25.033000, 121.565398`，不是實際戶外移動驗證 |
| 相機開啟、拍照、關閉 | **PASS** | [04 相機預覽](test-evidence/04-camera.png)、[05 拍照](test-evidence/05-photo.png) |
| JPEG 格式與本機預覽 | **PASS** | ADB 驗證 JPEG 標記 + BitmapFactory 真實解碼，[08 照片預覽](test-evidence/08-photo-preview.png) |
| 拒絕麥克風／相機授權 | **PASS** | `pm revoke` 後經 Android 權限對話框選拒絕，確認沒有錄音服務或相機 Activity 啟動，[09 拒絕後畫面](test-evidence/09-permission-denied.png) |
| 使用者選擇位置匯出檔案 | **尚未驗證** | `ACTION_CREATE_DOCUMENT` 介面已實作；需實機完成 DocumentsUI 操作、外部目錄確認 |
| 刪除確認／取消與多筆資料 | **尚未驗證** | 程式邏輯已實作，需另做完整操作測試 |
| 長時間背景執行／省電策略／螢幕鎖定 | **尚未驗證** | Android 廠牌／省電政策差異大 |
| Android 8–14 與多機型 | **尚未驗證** | 本輪僅測 Android 15／API 35 模擬器 |
| 音訊實際可聽性、無損、電話錄音 | **尚未驗證／非功能保證** | 這版採麥克風 AAC/M4A，不是電話上／下行音訊直錄或無損錄音 |

### 本輪失敗修正紀錄

1. **#42542293029**：主功能與檔案結構驗證通過；拒絕權限腳本因在權限撤銷後沒有正確重啟 App 而取得不到 UI root。已修正成先 `force-stop`、撤銷權限，再啟動。
2. **#42542744007**：模擬器冷啟動後首頁在等待時間內未可見，加入喚醒、解鎖、較完整重試及失敗時擷取 Logcat／螢幕截圖的診斷。
3. **#42543281609**：完整 CI **SUCCESS**，包含麥克風及相機權限明確拒絕測試。

**結論：本輪 Android 15 模擬器回歸測試通過，但不表示已完成所有實機、所有版本的功能驗收。**

## 2026-11-09 最新回歸驗收（後續版本；以本節為準）

- **正式依據**：[Android 15 全流程測試 — Run 42569471579](https://github.com/hub-google/Record2/actions/runs/42569471579) → **SUCCESS**。
- **跨版本依據**：[Android API 26、29、34 — Run 42567323144](https://github.com/hub-google/Record2/actions/runs/42567323144) → **三個 Job 均 SUCCESS**。
- 先前報告中標示「檔案匯出／刪除尚未驗證」是舊版紀錄，本輪已完成對應模擬器實測，不應再將其當成最新狀態。

| 測試項目 | 最新結果 | 可核對證據 |
|---|---|---|
| Android Debug APK 編譯 | PASS | Actions Job |
| 麥克風錄音／M4A 檔案／播放頁面 | PASS（API35） | CI 日誌、`02-recording.png`、`07-audio-library.png` |
| GPS 定位、相機預覽、JPEG 拍照 | PASS（API35） | CI 日誌、`03-location.png`、`04-camera.png`、`05-photo.png` |
| JPEG／M4A 從系統檔案選擇器真正匯出 | **PASS** | CI 日誌明確指出匯出到 Downloads 後與來源 **byte-for-byte 一致**；`13-photo-exported.png`、`14-audio-exported.png` |
| 取消匯出、取消刪除／確認刪除 | **PASS** | CI 日誌、`11-photo-deleted.png`、`12-audio-deleted.png` |
| 拒絕麥克風／相機權限 | PASS | `09-permission-denied.png`、CI 服務狀態檢查 |
| 切離 App 後錄音仍繼續、並儲存 M4A | **PASS（短時間）** | `15-background-audio-home.png`、`15-background-audio-service.txt`、CI 內的音檔大小檢查 |
| 切離 App 後定位服務仍持續、更新新 GPS 座標 | **PASS（短時間）** | `16-background-location-home.png`、`16-background-location-service.txt`、`16-background-events.jsonl` |
| Android 8、10、14 核心流程 | PASS（模擬器） | API 26／29／34 各自 CI artifact |
| 真機跨品牌、長時間待機／省電／重啟恢復 | **尚未驗證** | 無實機證據，不能宣稱已通過 |
| 隱形安裝／無提示攝錄、所有版本機型通用 | **未實作／無法保證** | 不屬於目前授權式 App 的功能 |

**此版結論**：可見授權式 Android 感測紀錄 App 的上述模擬器測試通過，但原始要求中的隱形、完全無系統提示、所有機型保證仍未達成；不得將模擬器短時間測試表述為正式跨品牌實機驗收。
