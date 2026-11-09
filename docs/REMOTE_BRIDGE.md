# Record2 — 經授權的本機遠端請求

這是參考 AndroidMCP 遠端管理核准概念、由 Record2 自行重新實作的功能。**預設關閉**，不依賴 ROOT，不從外部網路自動連入裝置，不提供秘密錄音／偷拍或繞過系統指示的能力。

## 使用方法

1. 手機安裝 Record2 Debug APK，明確開啟 Record2，點選 **「啟用遠端請求」**。會顯示常駐前景服務通知。
2. 點 **「查看連線金鑰與使用方式」**，手機持有人可查看專屬隨機 256-bit 金鑰；不要將金鑰上傳到 GitHub 或公開聊天室。
3. 手機連接電腦，使用者自行啟用並同意 ADB 配對／偵錯。在電腦執行：
   ```sh
   adb forward tcp:8765 tcp:8765
   ```
4. 電腦端（先把金鑰寫入本機環境變數，僅限可信任電腦）：
   ```sh
   export RECORD2_TOKEN='從手機顯示的 64 字元連線金鑰'
   curl --fail -H "Authorization: Bearer $RECORD2_TOKEN" http://127.0.0.1:8765/v1/status
   curl --fail -X POST -H "Authorization: Bearer $RECORD2_TOKEN" http://127.0.0.1:8765/v1/requests/audio_start
   ```
5. 第二個指令會回傳 `id` 與 `pending`。**手機使用者必須打開 App，看到這次操作內容並點選「同意並執行」**。拒絕或未在 90 秒內核准時不可啟用感測器。查詢結果：
   ```sh
   curl --fail -H "Authorization: Bearer $RECORD2_TOKEN" http://127.0.0.1:8765/v1/requests/填入回傳的id
   ```
6. 手機可隨時關閉遠端請求或重新產生金鑰；15 分鐘後服務會自動停止，需由使用者再次開啟。

## API

| 方法 | 路徑 | 回傳 |
|---|---|---|
| GET | /v1/status | `connected`、`audio_running`、`location_running`、`approval_required` |
| POST | /v1/requests/audio_start | 使用者同意後嘗試啟動有通知的錄音 |
| POST | /v1/requests/audio_stop | 使用者同意後停止錄音 |
| POST | /v1/requests/location_start | 使用者同意後嘗試啟動定位 |
| POST | /v1/requests/location_stop | 使用者同意後停止定位 |
| POST | /v1/requests/camera_open | 使用者同意後開啟**可見相機預覽**，仍需手機手動拍照 |
| GET | /v1/requests/ID | `pending`／`denied`／`expired`／`approved_to_attempt` 等請求狀態 |

所有路徑都需要 `Authorization: Bearer <token>`；無有效 token 回 HTTP 401。API **不**提供「批准請求」路徑、拍照指令、音檔下載或 GPS 座標讀取。

## 安全架構與限制

- 只監聽 `127.0.0.1:8765`，不監聽 `0.0.0.0`；不得在公開 Wi-Fi 直接暴露此 HTTP API。只有先經授權的 ADB 轉送才讓電腦連到本機回環服務。
- 隨機 32-byte token 儲存在 Android 私有 SharedPreferences，`android:allowBackup=false`，不寫進日誌或網路回應；重新產生後舊金鑰無效。
- 所有感測器要求在 App **前景**顯示確認對話框。即使令牌正確，遠端用戶端也不能直接執行、核准或規避 Android 權限與感測器指示。
- `approved_to_attempt` 不是「操作成功」，需檢查 `GET /v1/status` 或裝置畫面。手機權限不足、關閉 GPS 或 Android 前景服務限制都可能造成操作失敗。
- 本服務屬於 15 分鐘內的顯式操作工作階段，並非開機自動啟動、不聲稱可無人值守長期監控、也不是可透過公共 Internet 使用的服務。
- 測試計畫、模擬器截圖、結果另見 [TEST_PLAN.md](TEST_PLAN.md)；沒有跨品牌真機驗收不能宣稱全機型支援。

## 來源與授權

- [AndroidMCP](https://github.com/SixOfFive/AndroidMCP)：參考使用者核准、能力預設關閉與稽核概念，**未直接複製第三方程式碼**；其公開儲存庫未見明確根目錄授權檔案。
- [OwnTracks](https://github.com/owntracks/android)：背景定位測試和各 Android 版本相容性的參考；未複製 EPL 代碼。
- [Haven](https://github.com/guardianproject/haven)：感測器事件紀錄參考；未複製 GPL 代碼。
- [RustDesk](https://github.com/rustdesk/rustdesk)：使用者核准與遠端協作 UI 概念；未複製 AGPL 代碼。
