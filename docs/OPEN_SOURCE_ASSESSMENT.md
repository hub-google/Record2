# Record2：GitHub 開源方案與技術可行性調查

更新日期：2026-11-09。這是來源核對與開發方案評估，**不代表已整合或完成實機驗收**；專案原始要求請見 [README](../README.md)。

## 原始要求的可行性判定

| 原始需求 | 技術查核結論 |
| --- | --- |
| 免 ROOT | 可行；已有許多使用 Android 公開 API 的開源專案 |
| 多版本 Android 相容 | 可針對版本分流與測試；不能因單一作品宣稱「所有版本、品牌、機型皆適用」 |
| 遠端讀取位置 | 有開源實作，須使用者授權；背景定位涉及獨立權限、耗電策略及 OEM 差異 |
| 遠端要求拍照與錄音 | 有免 ROOT 實作，應保留本機可見、使用者核准及 OS 權限機制 |
| 完全無提示、隱形攝錄與不出現在系統應用清單 | 找不到可證明「所有標準 Android 機型均能合法、穩定運作」的公開 API 方案；Android 12+ 感測器指示由系統管理，不能將 Launcher 圖示顯示與系統安裝清單混為一談 |

## 已核對的 GitHub Repo

### 1. AndroidMCP — https://github.com/SixOfFive/AndroidMCP

- 定位：Android 8+、免 ROOT 的裝置端遠端操作服務。
- README 描述：具有位置、拍照、短音訊等敏感操作；雙重權限控制、每次操作核准、裝置端稽核紀錄；開發者聲稱 Samsung 手機及平板實測。
- 借鑑價值：**遠端請求 → 裝置身分驗證 → 權限與使用者確認 → 操作結果回報**的架構。
- 限制：其文件明確指出不能將背景相機／麥克風冷啟動或完全靜音運作當作無 ROOT 通用功能。經 2026-11-09 API 樹目錄檢查，**未見根目錄 LICENSE／COPYING**；公開可讀 ≠ 可自由複製，未釐清授權前僅作架構參考。
- 安全條件：可撤銷權限、明確同意、使用提示與操作稽核，不提供無感測器提示控制。

### 2. OwnTracks — https://github.com/owntracks/android

- 定位：開源背景位置紀錄／位置分享，有 Google 服務及獨立 OSS 建置。
- 借鑑價值：背景定位權限說明、事件紀錄、定位策略、重新連線、耗電／省電狀態提醒，以及跨品牌實機測試。
- GitHub 授權：EPL-1.0；如需搬用原始碼，必須先審查授權相容性。
- 重要反證：2026-08～09 有 Android 16 使用者回報特定 Samsung／Motorola 裝置在背景無位置回報，說明大眾使用的專案也不能無條件保證所有機型。

### 3. Haven — https://github.com/guardianproject/haven

- 定位：使用者啟用後的環境感測紀錄，偵測聲音／動作變化，並能遠端查看事件。
- 借鑑價值：感測器事件時間線、檔案保留、通知與使用者導向設定。
- 限制：GitHub 最近主要程式更新日期較久（API 查得最後 push 2022-10），需重新檢驗最新 Android 背景限制。
- 授權：GPL-3.0；不直接複製進本專案。

### 4. RustDesk — https://github.com/rustdesk/rustdesk

- 定位：遠端畫面、檔案傳輸及互動控制。
- 借鑑價值：連線驗證、授權流程、失敗處理；並可參考其跨平台裝置相容性報告。
- 限制：官方說明 Android 螢幕擷取需要本機同意，重新啟動後的無人值守並不保證；遠端桌面也不等於遠端秘密取得相機／麥克風。
- 授權：AGPL-3.0；未評估衍生授權前不可直接併入。

## Android 官方行為約束

1. [Android：背景啟動前景服務限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)：Android 12+ 背景啟動限制；Android 14+ 相機／麥克風等依使用期間授權檢查，非任意背景冷啟動。
2. [Android Open Source Project：隱私指示器](https://source.android.com/docs/core/permissions/privacy-indicators)：Android 12+ 相機／麥克風的使用標示由 System UI 處理。
3. [OwnTracks Android 16 實機討論](https://github.com/owntracks/android/issues/2307)：提供 OEM 和省電策略影響背景定位的具體案例。
4. [RustDesk 官方 Android 遠端控制解釋](https://www.rustdesk.com/zh-tw/blog/rustdesk-remote-control-android-ios-zh-tw/)：區分遠端桌面成功、使用者同意、無人值守限制。

## 對 Record2 的合理整合優先序（僅評估，未實作）

| 優先 | 方向 | 驗收要求 |
| --- | --- | --- |
| 1 | 強化現有授權式背景定位與不同 OEM 的穩定性 | 真機 GPS 持續紀錄、空間／省電／重啟等異常回報 |
| 2 | 在目前本機功能上加入**清楚授權的遠端請求機制** | 身分驗證、可撤銷控制、裝置端核准、完整操作紀錄；失敗要顯示原因 |
| 3 | 讓可見的相機與錄音工作階段能回應已核准的要求 | 每次操作有 OS 權限、可見狀態、拒絕後不會取得資料 |
| 4 | 擴充 Android 12–16 與 Samsung／Pixel／Xiaomi 等真機測試 | 每機每版本獨立報告，不以其他專案的 README 當作 Record2 實測 |

**不應整合**：繞過感測器指示、秘密攝錄、未經核准的遠端操作、隱匿於系統安裝清單。這些不能因 GitHub 上有人展示特定機型效果，就宣稱屬於「免 ROOT、所有 Android 皆可」的通用功能。

## 本次調查的完成與未完成

已完成：檢視四個專案的官方 GitHub 頁面、功能敘述、開源授權與近期狀態；交叉核對 Android 官方權限限制。

未完成：在 Record2 複製上述專案程式碼、實際建置候選專案 APK、於多台實體手機驗證其遠端功能。上述內容是**來源研究成果，不是新功能交付**。
