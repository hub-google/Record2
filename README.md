# Record2 — Android 私有感測記錄工具

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
