# Record2 — 測試計畫與實際執行報告

**測試日期**：2026-11-06  
**程式版本**：`0bc52f98e3e8a754659ebbe84d1ded48cc37beb0`（當次測試原始碼）  
**測試裝置**：GitHub Actions Ubuntu + Android Emulator API 35 / Google APIs / x86_64 / Pixel 6  
**CI 執行結果**：**SUCCESS** — [Actions Run 42162974699](https://github.com/hub-google/Record2/actions/runs/42162974699)  
**測試方式**：Gradle Debug APK 編譯、`bash scripts/test-static.sh`、ADB 操作、UIAutomator 尋找按鈕、GPS emulator geo fix、畫面截圖及檔案數量斷言。

## 實測結論

- **PASS**：程式碼靜態檢查；Gradle `assembleDebug`；APK 成功安裝並啟動；錄音啟動與停止；定位啟動與停止；模擬 GPS 座標成功顯示；相機預覽與拍照；返回首頁；本機資料顯示 **錄音檔 1、照片 1**；六張實際模擬器截圖均產出。
- **未測／待補**：手動拒絕權限、離開 App 後長時間持續執行、真實 GPS 移動、音訊檔完整回放及音質量測、相機 JPEG 解碼／EXIF、分享選單、App 強制關閉與重啟、不同 Android API／廠牌、無鏡頭手機、真機長時間可靠性、耗電測試。
- **紀錄**：`docs/test-evidence/error-log.txt` 在本次操作結束時為空；只能表示所擷取的 `Record2:E`、`AndroidRuntime:E` 過濾項目沒有紀錄，不代表全面沒有錯誤。
- **注意**：並未提供隱藏啟動、規避系統通知或不經授權啟動感測器等功能；這些也不在測試範圍。

## 逐項測試案例

| ID | 操作／預期 | 結果 | 觀察與佐證 |
|---|---|---|---|
| T01 | 安裝、啟動 App，首頁顯示錄音、定位、相機與分享 | **PASS** | [01-home.png](test-evidence/01-home.png)，且 UIAutomator 驗證「開始錄音」可見 |
| T02 | 首次錄音時請求使用者權限，拒絕時不得錄音 | **未測** | CI 使用 `adb pm grant` 預先授權，未經權限對話框 |
| T03 | 開始錄音，前景服務與可見狀態正常 | **部分通過** | [02-recording.png](test-evidence/02-recording.png)；畫面顯示「錄音進行中」及系統麥克風使用指示，但未驗證切換 App 後的長時間持續性 |
| T04 | 停止錄音後產生錄音檔 | **PASS（數量）** | [06-saved-files.png](test-evidence/06-saved-files.png) 顯示「錄音檔：1」；尚未對 M4A 進行回放／頻譜檢查 |
| T05 | 啟動持續定位並呈現服務狀態 | **PASS** | [03-location.png](test-evidence/03-location.png)，顯示「持續定位中」 |
| T06 | 注入座標並刷新顯示，事件紀錄更新 | **部分通過** | `adb emu geo fix 121.5654 25.0330`；畫面顯示 `25.033000, 121.565398 (±5m)`；尚未直接解析 JSONL 內容 |
| T07 | 停止定位，按鈕恢復初始狀態 | **PASS（介面）** | [06-saved-files.png](test-evidence/06-saved-files.png) 顯示「未定位」；未做長時間停止後無更新驗證 |
| T08 | 相機預覽可開啟，拍照可保存 | **PASS（數量）** | [04-camera.png](test-evidence/04-camera.png)、[05-photo.png](test-evidence/05-photo.png)，返回首頁後照片數 1；未做 JPEG 解碼／畫質驗證 |
| T09 | 關閉相機返回首頁 | **PASS** | [06-saved-files.png](test-evidence/06-saved-files.png) |
| T10 | 拒絕麥克風、位置、相機權限 | **未測** | 待手動與 UI 自動化拒絕流程 |
| T11 | 分享紀錄摘要 | **未測** | 待驗證 Android 分享面板及傳出內容 |
| T12 | 背景、低電量、省電與定位服務關閉情境 | **未測** | 待實機測試 |
| T13 | Android 8、11、12、13、14、15 與廠牌交叉相容 | **部分通過** | 僅 CI Android 15 / API 35；其他未測 |
| T14 | 無相機硬體或相機權限撤銷 | **未測** | 待相容性測試 |

## 操作截圖

以下 PNG 全部由此次 GitHub Actions 中的 Android 35 模擬器擷取，**非示意圖**：

1. [01-home.png — 首頁](test-evidence/01-home.png)
2. [02-recording.png — 錄音執行中](test-evidence/02-recording.png)
3. [03-location.png — 持續定位及注入座標](test-evidence/03-location.png)
4. [04-camera.png — 相機預覽](test-evidence/04-camera.png)
5. [05-photo.png — 點擊拍照後](test-evidence/05-photo.png)
6. [06-saved-files.png — 錄音檔 1、照片 1](test-evidence/06-saved-files.png)

[完整 CI 執行紀錄](https://github.com/hub-google/Record2/actions/runs/42162974699)；從該頁 **Artifacts** 下載 `Record2-debug-APK` 與 `Record2-test-evidence`。

## 風險與已知限制

- API 35 模擬器的 PASS 不等於所有 Android 版本、廠牌、機型皆可用；本次未完成真機相容性驗證。
- 資料預設存於 App 專屬的手機本機目錄；不是雲端資料庫，也沒有遠端指揮、同步或登入。
- 錄音與定位需要使用者先行授權；會顯示前景服務通知或 Android 的麥克風／相機／定位系統提示。
- 音質依裝置及環境而異；未做無損錄音／音質量測承諾。
- 首輪 CI 的 SDK 安裝失敗及首次模擬器截圖不完整，已在後續版本修正。以 **42162974699** 為此次正式驗證依據。

## 重測指令

```bash
bash scripts/test-static.sh
gradle --no-daemon :app:assembleDebug
# 在已啟動的 Android 模擬器上：
bash scripts/emulator-smoke.sh
```
