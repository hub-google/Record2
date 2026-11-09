# Record2 — Android 私有感測記錄工具

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
| 背景開鏡頭 | 目前必須在可見相機畫面預覽、拍照；離開釋放鏡頭 | **未達成** |
| 不發出任何提醒 | 使用 Android 權限對話框、前景服務通知與系統感測器指示 | **未達成** |
| 實際測試、提供截圖 | GitHub Actions 的 Android 8、10、14、15 模擬器測試及 PNG 證據；未完成全機型實測 | **部分完成** |

**重要約束**：Android 的裝置／OEM 差異、權限與背景限制，以及麥克風／相機系統指示器，使「免 ROOT 且任何版本／機型都能隱形、完全無提示運作」無法得到保證。這個專案不提供規避系統感測器指示、秘密監控或未授權攝錄的功能。即使在自有裝置上使用，也不能將未實現的條件標為完成。

### 0.2 目前可交付的已授權實作

- **麥克風錄音**：前景服務、通知、停止與 AAC/M4A 儲存。
- **定位**：前景服務、GPS／Network Provider、位置與時間事件紀錄。
- **相機**：需可見預覽的拍照與 JPEG 儲存。
- **檔案**：播放、預覽、匯出（Storage Access Framework）、取消／確認刪除、本機事件。
- **資料與安全**：無雲端帳號、無網路權限、資料預設留在 App 儲存目錄。
- **驗收**：測試計畫 [docs/TEST_PLAN.md](docs/TEST_PLAN.md)，實際測試證據 [docs/test-evidence/](docs/test-evidence/)，最新已成功執行的 [Android 15 完整操作與背景運作測試](https://github.com/hub-google/Record2/actions/runs/42569471579) 與 [Android 8／10／14 相容性測試](https://github.com/hub-google/Record2/actions/runs/42567323144)。

### 0.4 最新驗證結果（2026-11-09）

- Android 15／API 35 的 GitHub Actions Run [42569471579](https://github.com/hub-google/Record2/actions/runs/42569471579) 為 **SUCCESS**：錄音、定位、相機、檔案儲存、檔案真實匯出、刪除與拒絕權限均通過。
- **切至手機首頁後仍持續運作的可見前景服務**已實測：錄音切換背景約 12 秒仍保存 M4A；定位切至背景後注入新座標，約 35 秒後確認位置事件寫入。證據見 [背景錄音截圖](docs/test-evidence/15-background-audio-home.png)、[背景定位截圖](docs/test-evidence/16-background-location-home.png) 與 [GPS 事件紀錄](docs/test-evidence/16-background-events.jsonl)。
- Android 8／10／14 模擬器核心功能的相容性測試：[42567323144](https://github.com/hub-google/Record2/actions/runs/42567323144) **SUCCESS**。
- **未涵蓋**：所有品牌／版本實體手機、長時間鎖屏、省電政策、遠端操控、隱藏使用或規避任何系統指示。短時間背景測試不能推論全天候可靠性。

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

Android 8.0 (API 26) 至目標 Android 15 (API 35)。首次按各功能時依照 Android 提示授權；定位需開啟手機位置服務。

## 測試與截圖

- `bash scripts/test-static.sh`：離線靜態結構、Manifest 與隱私控制檢查。
- `.github/workflows/android.yml`：在 GitHub Actions 編譯 Debug APK，於 Android 35 模擬器執行操作流程。
- `scripts/emulator-smoke.sh`：錄製首頁、錄音、定位、相機預覽、拍照後畫面的實機型模擬器截圖，成功時寫到 `docs/test-evidence/` 並上傳 Actions artifact。
- `docs/TEST_PLAN.md`：完整測試案例與待驗證事項。

CI 執行與實機測試結果應以 GitHub Actions 實際紀錄為準；倘若沒有圖片或工作流程失敗，不能宣稱操作截圖已完成。

[GitHub Actions](https://github.com/hub-google/Record2/actions)

## 檔案管理（2026-11-09 更新）

首頁新增「管理錄音與照片」：列出最近 40 筆錄音或照片；錄音可播放／停止播放，照片可預覽，兩種檔案均可透過 Android 的**儲存檔案**選擇器匯出並可確認後刪除。

檔案匯出使用 Android Storage Access Framework 的 `ACTION_CREATE_DOCUMENT`，**不需要全裝置儲存權限**。未選擇匯出時，不會自行上傳或分享。App 不要求網路權限。照片使用縮圖解碼以降低記憶體佔用；音訊播放器在離開頁面後停止。

測試範圍擴充為檔案清單、音訊格式／照片驗證、照片預覽，以及 Android 系統明確拒絕麥克風與相機授權後不會自行啟動的測試。詳見 `docs/TEST_PLAN.md`。實際 PASS/FAIL 以 GitHub Actions 內容為準。
