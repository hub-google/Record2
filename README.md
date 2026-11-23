# Record2 — Android 私有感測記錄工具

> **功能開發盤點：尚未完全符合原始要求，最新修正尚未測試。** 已逐項核對目前原始碼、未完成範圍與後續統一驗收條件：[開發完整性檢查報告](docs/IMPLEMENTATION_AUDIT.md)。

## 0. 使用者最初要求（原始產品定位，2026-11-06）

> **本節忠實保留需求，不代表已實現，也不代表 Android 能保證提供這些能力。** 請勿將原始要求與現有產品功能混淆。

使用者原始要求：

1. **不須 ROOT**。
2. **任何安卓版本或機型都能用**。
3. **安裝後不會出現 App 或任何東西在列表（隱形）**。
4. **可以即時背景錄音、定位地理位置、打開相機鏡頭，且不會發出任何提醒**。
5. 先研究可取得原始碼的 Android GitHub Repo，再以 **Record2** 實作；完成後要自行提出測試計畫、實際測試、提供**操作截圖**，並依需求開發，不擅自增加新功能。

**原始產品定位**：使用者希望在自己的 Android 手機上，打造不需 ROOT、跨機型通用、整合麥克風／位置／相機感測的背景操作工具，且要求隱形與完全無提示運作。

### 0.1 原始要求與目前實際落差（持續更新）

| 原始需求 | 目前實作／驗證 | 狀態 |
|---|---|---|
| 免 ROOT | 原生 Android App 使用一般公開權限；不依賴 ROOT | **已實作** |
| 所有 Android 版本、品牌、機型 | API 26、29、34、35 的 Android 模擬器操作測試；仍缺實機和其他 API | **未達成通用保證** |
| 安裝後不出現在 App 列表 | App 有正常啟動圖示與可見介面 | **未實作** |
| 即時背景錄音 | 使用者在畫面手動授權並啟動麥克風前景服務，可切離介面；顯示通知與 Android 使用指示 | **部分符合，非隱藏錄音** |
| 即時地理位置 | 使用者手動授權並啟動位置前景服務，定期記錄座標；受手機位置開關／省電影響 | **部分符合，非無提示** |
| 背景開鏡頭 | 經使用者在可見 App 確認後可啟動**無聲背景錄影**，在切離 App／短暫熄屏時繼續運作；相機預覽拍照仍須可見畫面 | **部分符合，非隱藏攝錄** |
| 不發出任何提醒 | 使用 Android 權限對話框、前景服務通知與系統感測器指示 | **未達成** |
| 實際測試、提供截圖 | GitHub Actions 的 Android 8、10、14、15 模擬器測試及 PNG 證據；未完成全機型實測 | **部分完成** |

**重要約束**：Android 的裝置／OEM 差異、權限與背景限制，以及麥克風／相機系統指示器，使「免 ROOT 且任何版本／機型都能隱形、完全無提示運作」無法得到保證。這個專案不提供規避系統感測器指示、秘密監控或未授權攝錄的功能。即使在自有裝置上使用，也不能將未實現的條件標為完成。

### 0.2 目前可交付的已授權實作

- **麥克風錄音**：前景服務、通知、停止與 AAC/M4A 儲存。
- **定位**：前景服務、GPS／Network Provider、位置與時間事件紀錄。
- **相機**：需可見預覽的拍照與 JPEG 儲存。
- **背景錄影**：需從可見 App 主動開始、再次確認，使用可見 `camera` 前景服務錄製無聲 MP4；切至桌面或短暫鎖屏可繼續錄影，通知可直接停止。
- **檔案**：播放、預覽、匯出（Storage Access Framework）、取消／確認刪除、本機事件。
- **資料與安全**：無雲端帳號，資料預設留在 App 儲存目錄。遠端請求功能需手機使用者手動啟用，新增的網路權限僅用於手機 **127.0.0.1 本機回環介面**，預設不建立連線、不上傳音檔或位置。
- **驗收**：測試計畫 [docs/TEST_PLAN.md](docs/TEST_PLAN.md)，實際測試證據 [docs/test-evidence/](docs/test-evidence/)，最新已成功執行的 [Android 15 完整操作與背景運作測試](https://github.com/hub-google/Record2/actions/runs/42569471579) 與 [Android 8／10／14 相容性測試](https://github.com/hub-google/Record2/actions/runs/42567323144)。

### 0.4 最新驗證結果（2026-11-09）

- Android 15／API 35 的 GitHub Actions Run [42569471579](https://github.com/hub-google/Record2/actions/runs/42569471579) 為 **SUCCESS**：錄音、定位、相機、檔案儲存、檔案真實匯出、刪除與拒絕權限均通過。
- **切至手機首頁後仍持續運作的可見前景服務**已實測：錄音切換背景約 12 秒仍保存 M4A；定位切至背景後注入新座標，約 35 秒後確認位置事件寫入。證據見 [背景錄音截圖](docs/test-evidence/15-background-audio-home.png)、[背景定位截圖](docs/test-evidence/16-background-location-home.png) 與 [GPS 事件紀錄](docs/test-evidence/16-background-events.jsonl)。
- Android 8／10／14 模擬器核心功能的相容性測試：[42567323144](https://github.com/hub-google/Record2/actions/runs/42567323144) **SUCCESS**。
- **未涵蓋**：所有品牌／版本實體手機、長時間鎖屏、省電政策、未經每次確認的遠端操作、隱藏使用或規避任何系統指示。短時間背景測試不能推論全天候可靠性。

### 0.3 尚未完成、不可宣稱完成的部分

- 目前的 App 與**原始隱形／無提示需求不符**；不支援無可見提示的背景相機啟動。
- 不保證所有 Android 版本、品牌、實體手機皆可用；缺長時間背景、低電量、定位停用、重啟與不同 OEM 的完整實機驗證。
- 本專案不是通話上下行雙向錄音 App；麥克風錄音與電信通話數位錄音不同。不要與 `hub-google/Record` 的通話錄音 PRD 混淆。

---

原生 Java Android App，不需 ROOT。以**明確授權與可見操作**提供三種功能：

- **錄音**：按「開始錄音」後建立麥克風前景服務、顯示系統通知、儲存 AAC/M4A；按停止即結束。
- **定位**：按「開始定位」建立位置前景服務；GPS／Network Provider 更新位置，存入 `events/events.jsonl` 及畫面摘要。
- **相機**：需開啟可見相機預覽頁面才能拍照，儲存 JPEG；離開頁面釋放相機。
- **儲存**：資料預設留在裝置的 App 專屬儲存空間（`Android/data/com.hubgoogle.record2/files/`），不會自動上傳伺服器。
- **分享**：透過 Android 系統分享選單傳送摘要文字，沒有無聲背景上傳。

沒有隱形安裝、不規避 Android 麥克風／相機指示器、不遠端無提示啟用感測器。不保證所有 Android 版本／機型一致，部分裝置可能沒有 GPS 或鏡頭。

## 編譯

以 Android Studio 開啟本專案；使用 JDK 17，Android SDK Platform 35、Gradle 8.11.1、Android Gradle Plugin 8.9.2。

也可使用已安裝的 Gradle：

```bash
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 7.0 (API 24) 起可安裝（2026-11-20 降低 minSdk，**未在 Android 7 真機／模擬器編譯驗證**），編譯目標為 Android 15 (API 35)。首次按各功能時依照 Android 提示授權；定位需開啟手機位置服務。

## 測試與截圖

- `bash scripts/test-static.sh`：離線靜態結構、Manifest 與隱私控制檢查。
- `.github/workflows/android.yml`：在 GitHub Actions 編譯 Debug APK，於 Android 35 模擬器執行操作流程。
- `scripts/emulator-smoke.sh`：錄製首頁、錄音、定位、相機預覽、拍照後畫面的實機型模擬器截圖，成功時寫到 `docs/test-evidence/` 並上傳 Actions artifact。
- `docs/TEST_PLAN.md`：完整測試案例與待驗證事項。

CI 執行與實機測試結果應以 GitHub Actions 實際紀錄為準；倘若沒有圖片或工作流程失敗，不能宣稱操作截圖已完成。

[GitHub Actions](https://github.com/hub-google/Record2/actions)

## 檔案管理（2026-11-09 更新）

首頁新增「管理錄音與照片」：列出最近 40 筆錄音或照片；錄音可播放／停止播放，照片可預覽，兩種檔案均可透過 Android 的**儲存檔案**選擇器匯出並可確認後刪除。

檔案匯出使用 Android Storage Access Framework 的 `ACTION_CREATE_DOCUMENT`，**不需要全裝置儲存權限**。未選擇匯出時，不會自行上傳或分享。新增遠端請求只在使用者主動啟用後監聽 127.0.0.1，需網路權限但不連外部伺服器，也不開放音檔下載 API。照片使用縮圖解碼以降低記憶體佔用；音訊播放器在離開頁面後停止。

測試範圍擴充為檔案清單、音訊格式／照片驗證、照片預覽，以及 Android 系統明確拒絕麥克風與相機授權後不會自行啟動的測試。詳見 `docs/TEST_PLAN.md`。實際 PASS/FAIL 以 GitHub Actions 內容為準。

## GitHub 開源方案調查（2026-11-09）

已檢查 AndroidMCP、OwnTracks、Haven 及 RustDesk 的遠端控制、背景定位及感測器功能。詳細比較、原始碼授權、Android 官方限制與可參考的授權式實作方向，請見 **[docs/OPEN_SOURCE_ASSESSMENT.md](docs/OPEN_SOURCE_ASSESSMENT.md)**。此文件是技術研究，不代表已整合；原始隱形／無提示條件仍屬未達成。

## 免 ROOT 遠端請求整合（2026-11-09）

參考 AndroidMCP 的「能力預設關閉／每次敏感要求須由手機持有人核准」及 OwnTracks 的使用者可見定位運作方式，Record2 新增 **本機回環式遠端操作請求**：

- 預設關閉，使用者在主畫面按「啟用遠端請求」後才會啟用有通知的服務；只繫結 `127.0.0.1:8765`，無 LAN 監聽或公網伺服器。
- 電腦透過使用者自行授權的 `adb forward` 建立轉送，再以隨機 256-bit 金鑰向 API 發出操作請求；不會自動配對、遠端安裝或偷偷啟用。
- 支援請求 **開始／停止錄音、開始／停止定位、開啟可見相機預覽**。手機用戶必須對每次請求按「同意並執行」或「拒絕」；相機需保持可見頁面及 Android 權限機制。API 的 `approved_to_attempt` 僅表示使用者核准操作嘗試，需再查 `/v1/status` 確認實際服務狀態。
- 金鑰可在手機主動查看／重新產生；拒絕或逾時不會執行。最多 3 個待核准請求，90 秒自動過期，服務啟動 15 分鐘後自動停止。遠端客戶端**不能自行批准自己的要求**。
- 遠端介面**不傳輸實際 GPS 座標、音檔、照片，亦不提供遠端自動拍攝或隱藏感測器指示的 API**。

完整操作說明、安全界線與測試：**[docs/REMOTE_BRIDGE.md](docs/REMOTE_BRIDGE.md)**；最初的隱形／無提示／所有機型通用條件仍**未達成**。

## 開發期驗收順序（2026-11-09）

依使用者最新要求，**先完成合法、授權式版本的功能，再集中進行完整測試**。在完成剩餘功能與安全性檢查前，兩套 GitHub Actions 驗收工作流程暫改為 `workflow_dispatch`，不再因每次推送自動執行全套模擬器。待功能完成後，手動執行 Android 15 全流程與 Android 8／10／14 相容性測試，整理失敗修正與操作截圖後才宣告交付。

此調整只改變測試時機，**並非測試已通過或全部需求已實現**。原始「隱形、無提示感測器控制」需求仍記錄在前段的未達成矩陣中，不會將其誤列為可交付的授權式功能。


## 2026-11-19：背景錄影整合（仍需最新 APK／模擬器驗收）

參考 [FadCam](https://github.com/anonfaded/FadCam) 的背景錄影產品模式，但**未直接複製第三方程式碼**，由 Record2 自行加入 Camera2 + MediaRecorder + Android `camera` 類型前景服務。

- 主畫面新增 **「背景錄影（無聲）」**：手機持有人必須從可見 App 畫面手動授權、再次確認開始錄影；不支援遠端 API 自動啟動錄影。
- 啟動後可切換 App、按 Home 或關閉螢幕，讓具有可見通知的相機前景服務繼續錄影。通知含「停止錄影」按鈕，亦可返回 App 停止。
- 將錄影輸出寫至 `videos/*.mp4.partial`，正常停止並完成 MP4 容器後再改名為 `videos/*.mp4`，避免未完成片段在影片清單被誤認為可播放檔。
- 「我的檔案」新增影片清單、內建播放、Android 文件選擇器匯出與刪除；首頁顯示影片數量。
- **此版本是純影像錄製，不包含麥克風聲音**，既有單獨的 M4A 錄音功能維持原樣。
- Android 14+ 相機前景服務必須從**可見 App**啟動；Android 系統相機指示和通知不能規避。裝置耗電策略可能中止長時間錄影；不是全品牌保證。
- 本輪新增 `scripts/emulator-video.sh`，驗證同意啟動、切 Home、短暫鎖屏、停止後 MP4、影片清單，併入 Android 15 CI。**程式碼提交不代表該 CI 已通過**，以 Actions 實際結果為準。


## 2026-11-19 背景錄影實際驗收

- [GitHub Actions Run 44501123406](https://github.com/hub-google/Record2/actions/runs/44501123406)：**SUCCESS**；Android 15／API 35 模擬器，含現有錄音、定位、拍照、匯出、權限拒絕、遠端授權／撤銷，以及新增的背景錄影。
- 本次日誌列出 **31 項 PASS、0 項 FAIL**，包含明確開始錄影、切至 Android 桌面、短時間關閉螢幕、停止後 MP4 容器檢查與影片清單。
- 影片證據：[錄影中](docs/test-evidence/20-video-recording.png)、[切至桌面](docs/test-evidence/21-video-background-home.png)、[錄影結束](docs/test-evidence/22-video-finished.png)、[影片清單](docs/test-evidence/23-video-library.png)。
- 仍未驗證的部分：長時間鎖屏和省電限制、多品牌實機、影片播放完整性與匯出真實比對、前景通知欄停止動作的實際 UI 點擊、錄影中斷救援與分段自動修復。**不能推論任意 Android 版本和機型都能穩定運作。**
- 依先前開發流程設定，Android 15 全套驗收工作流程完成此輪測試後恢復為 **`workflow_dispatch` 手動觸發**。


## 2026-11-20：原始需求差距修正（本輪**依指示未進行測試**）

- 將 GPS／網路位置服務的請求條件由「30 秒或 25 公尺」調整為 **約 5 秒、0 公尺**；Android 實際回傳頻率可能受省電、訊號及系統政策影響，不能保證精確每五秒。首頁最近定位會附上定位回傳時間，避免把過期座標當作即時位置。
- 影片異常停止／無法完成 MP4 時，不再直接刪除非空的 `.mp4.partial`；可在影片清單看到「未完成影片」並選擇匯出原始片段或刪除，不假裝它是可播放 MP4，也**沒有**保證修復影片。
- 原有正常停止錄影與完成 MP4、播放、匯出和刪除介面保留。
- 依使用者本輪指示，本次**只提交程式碼，不跑測試、不產生新版 APK**。先前 CI 成功僅適用於先前版本，不可視為本輪新修改已驗證。

- 2026-11-20 另補 Android 7（API24）相容分支：舊版不要求通知頻道，從可見畫面以 `startService` 啟動；Android 8+ 則維持 `startForegroundService`。**本輪均未測試，不能宣稱 Android 7 已驗證可用。**


## 2026-11-20：Android 5.0 相容性程式（尚未編譯或實測）

- 本次針對「支援更多 Android 版本」，`minSdk` 從 Android 7（API 24）下修到 **Android 5.0（API 21）**。
- 新增 `PlatformCompat.java` 統一處理舊版權限、通知可用性、相機和通知系統服務查詢、`PendingIntent` 標記。
- `MainActivity`、`CameraActivity`、`AudioRecordService`、`LocationTrackService`、`VideoRecordService`、`RemoteControlService`、`Notifications` 採用相容層；Android 5.x 不會呼叫 Android 6 才引入的 `checkSelfPermission`，或 Android 7 才引入的 `areNotificationsEnabled`。
- Android 5.x 的相機仍依賴 Camera2 硬體功能，**不保證所有機型都支援完整錄影**；Android 5 以前不支援。
- **本輪依使用者要求不執行 Gradle／模擬器／GitHub Actions 測試，不能將本次程式提交視為相容性驗收通過。**


## 2026-11-20：減少 App 非必要操作提示（未執行測試）

- 錄音、定位、背景錄影與遠端請求的日常開始／停止，不再額外彈出「正在啟動」、「已要求停止」等重複 Toast；使用者仍能從主畫面狀態與 Android 系統通知看到目前動作。
- 拍照成功改成頁面內提示，不額外顯示 Toast；播放錄音的開始／停止／自然結束不再顯示重複 Toast。
- **保留必要提醒**：Android 系統權限要求、麥克風／相機指示、前景服務通知、開始背景錄影的明確同意、遠端操作核准、操作失敗與權限異常提示。
- 這次是減少**非必要 App 提示**，**不是**「完全沒有通知或提示」，更不是背景隱形攝錄。此輪只提交 GitHub main 原始碼，依先前指示暫不測試。


## 2026-11-23：背景錄製與位置追蹤可靠性修正（尚未測試）

- 背景錄影的停止動作（首頁及通知）改為要求前景服務先在相機工作執行緒完成 MediaRecorder 停止與 MP4 封存，之後才結束服務；避免一般手動停止直接呼叫 `stopService()` 造成 `onDestroy()` 與檔案封存交錯。
- 錄音、定位前景通知新增「停止」動作，離開 App 仍能直接結束；保留 Android 通知及系統感測器使用指示。
- GPS／網路定位回傳比本次追蹤已接收之位置更舊的 `elapsedRealtimeNanos` 時，略過過期資料，降低最新座標被覆蓋的機率。
- 更新靜態檢查規則，但**依使用者先前指示，本輪沒有觸發測試或產生 APK**。影片遭系統強制終止及所有 OEM 仍無法保證。
