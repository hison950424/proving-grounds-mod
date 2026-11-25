# 交接文件（給接手的 Claude 對話）

> 用途：對話記憶（上下文）快滿時，把重點交接給新的對話。**接手的對話先讀完這份**，記憶到 90% 時更新這份文件、提交，再開新對話交接（使用者會提醒）。
> 最後更新：2026-11-25（v0.30.1 已發佈：隊友藍色、防彈減半、閃光盾 45%、刀子 1.75 格、取消距離減傷；等實測回饋）。

## 1. 使用者與溝通
- 一律用**繁體中文**；使用者第一次做 Forge 模組、不懂程式：每一步先用白話說明「要做什麼、為什麼、做完會怎樣」，術語第一次出現附一句解釋。
- 使用者常用**手機遠端**看：結尾訊息要能獨立看懂，最後一句寫出問題與選項（例如「要現在發佈嗎？（現在發佈／先不要）」）。
- 回報時要誠實：哪些實測過、哪些只編譯過、哪些沒做，都講清楚。
- 對話記憶用到 **90%** 要在回覆開頭提醒（`get_usage` 的 `context.percentUsed`）。
- 自動記憶資料夾（`C:\Users\assas\.claude\projects\...tacz-reborn\memory\`，索引 `MEMORY.md`）跟著工作資料夾自動載入，裡面有更多偏好。

## 2. 規則
- **分工（使用者確認過）**：模組的所有實作（新要求、回饋、修錯）都由「目前接手的那個對話」負責，其他對話不寫程式。對話記憶用到 **90%**（`get_usage` 的 `context.percentUsed`）時：先在回覆開頭提醒使用者 → 更新本檔（最後更新日期、已完成、進行中／待決定）並提交 → 開新對話交接，並把這條「所有實作由新對話負責、90% 交接」的規則一起交代下去。
- **發佈**：先問「伺服器和客戶端都關了嗎？」，得到回覆、確認沒有 java 程序，才跑
  `powershell -ExecutionPolicy Bypass -File tools\release.ps1 -Level big|small -NotesFile <說明.md>`（在模組資料夾執行，不要加 `*>&1`）。
  大型計畫書一路做完再發佈一次；小回饋做完就問。版本規則：`docs/VERSIONS.md`（新系統／操作改變／網路版本變 = big；修正與數值 = small）。發佈後傳 `docs/TESTING.md` 給使用者。
- **發佈前先移除 Spark**（見第 5 點）。
- **下載**：做模組需要的東西可以下載，但先問，說明用途、檔名、來源、大小；能核對雜湊就核對。素材要能散佈的授權（公眾領域／CC0，CC BY 要記在 `docs/CREDITS.md`）。不要用其他遊戲的素材。
- 改伺服器設定預設值：用**改名的設定鍵**（例如 `xxx_v2`），不要動使用者的 toml。改伺服器 toml 前確認沒有 java 在跑。
- `.mcfunction`／`.json` 用 UTF-8 無 BOM。提交訊息繁體中文，結尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- 不要分享使用者的 email 或個資；不要取出 git 憑證；release 腳本不 `--force`、不改寫歷史。

## 3. 位置與工具
| 東西 | 位置 |
|---|---|
| 模組原始碼 | `C:\mcdatapack\proving-grounds-mod`（分支 `feature/phase1-setup`，套件 `com.provinggrounds`） |
| 模組伺服器 | `C:\mcdatapack\1.20.1 Proving Grounds_Server_Mod`（設定 `world/serverconfig/provinggrounds-server.toml`） |
| Prism 客戶端 | `C:\Users\assas\AppData\Roaming\PrismLauncher\instances\1.20.1 Proving Grounds_Client_Mod\minecraft` |
| 舊資料包（舊版規則參考） | `C:\mcdatapack\1.20.1 Proving Grounds_Server Side_V3.0.0\world\datapacks\tacz_reborn` |
| ffmpeg | `C:\mcdatapack\tools\ffmpeg\bin\` |
| Python | `C:\Users\assas\AppData\Local\Programs\Python\Python314\python.exe`（加 `-I`；印中文設 `PYTHONIOENCODING=utf-8`） |
| javap（看 TACZ 類別） | `C:\Program Files\Java\jdk-17\bin\javap.exe` |
| 槍包原始立體聲備份 | `C:\mcdatapack\backups\tacz_stereo_20261124` |

- 開發伺服器：`./gradlew runServer`（用 `run_in_background`，等日誌出現 `Done (`），RCON 埠 25575，密碼在 `run/server.properties`。開發客戶端：`./gradlew runClient`（只能看日誌，電腦控制工具抓不到遊戲視窗）。
- Mixin 沒有 refmap：`remap = false`，方法名同時寫開發名與 SRG 名。SRG 對照：`~/.gradle/caches/forge_gradle/mcp_repo/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/joined/downloadClientMappings/client_mappings.txt`（官方名→混淆名）＋同目錄 `rename/config/joined.tsrg`（混淆名→SRG）。客戶端專用 mixin 放 `provinggrounds.mixins.json` 的 `client`。
- TACZ 只能在 `compat/` 用反射存取（建置的 `checkGunModIsolation` 會檢查）。TACZ 設定可用反射設值（例：`ClientGunAim.applyDotCrosshairOnce`、`hideTaczInteractText`）。
- 小技巧：Bash heredoc 會吃反斜線，大段替換建議用 Write 寫一個替換清單檔再用 Python 套用；`./gradlew build -q` 會跑完整檢查。

## 4. 已完成（重點）
- v0.25.0 已發佈：計畫書 5（`docs/PLAN5_REPORT.md`）、互動鍵 F、點狀準心、邊緣標題、擊殺提示、團隊模式規則、防蹲點、佔點光柱、戰績面板、商店 122 把槍（`docs/SHOP_PRICES.md`）、跑庫塔、方塊還原（`data/BlockLedger` + `LevelSetBlockMixin`）、只有大逃殺會破壞方塊（`core/BlockProtection`）、匯入地圖（`/pg map import`，`docs/MAP_IMPORT.md`）、全員準備自動開始、單人可開任何模式、不擊退（`events/BulletHitEvents`、`ExplosionHitPlayersMixin`）、倒地速度 30%。
- 已提交、**未發佈（v0.26.0）**，說明草稿 `docs/NEXT_RELEASE_NOTES.md`：降落傘（`item/ParachuteItem`、`ui/ParachuteRenderer`、`HumanoidArmsMixin`）、毒氣彈（模糊漸變、真人咳嗽 `tools/extract_cough_clips.py`、方向偏移）、槍聲方向（`tools/mono_gunpack_sounds.py`，已處理客戶端槍包）、管理員 F3+B 精準判定框（`ui/HitboxDebugRenderer`）、隱藏 TACZ 互動字樣、全體孤狼、一般物品優先放背包。

## 5. 進行中／待決定
- **計畫書 7（`docs/PLAN7.md`，使用者手機端寫的第七份）**：A1～A8、B、C1～C11、D、E、F、G 全部完成並提交，建置與開發伺服器啟動通過（**都還沒在真正客戶端、多人實測**）。每項的做法、與計畫書不同的地方都寫在 PLAN7.md 最後的「電腦端進度紀錄」。發佈說明 `docs/NEXT_RELEASE_NOTES.md`、測試步驟 `docs/TESTING.md` 最上面已寫好。網路版本 23。墓碑畫面沒有套用 A4 的位置設定（回報裡說明）。
- **計畫書 6（`docs/PLAN6.md`）**：A1～A21、B、C、D、E 全部完成並提交，建置與開發伺服器啟動通過（**都還沒在真正客戶端實測**）。發佈說明 `docs/NEXT_RELEASE_NOTES.md`、測試步驟 `docs/TESTING.md` 最上面已寫好。A22 已移除 variantbarrels（jar 在伺服器與客戶端的 mods-backup），v0.28.0 已發佈，等使用者實測回饋。
- v0.27.0 已發佈（比賽中調 HUD、子彈彈道、降落傘繩、圈外受傷聲、炸毀方塊開關、補品互換——互換已在計畫書 6 A2 拿掉）。
- **介面大小 3 重新排版＋HUD 自訂畫面**：已完成並提交（0b85849，屬於未發佈的 v0.26.0），建置與開發伺服器啟動都通過，**還沒在真正客戶端看過**。內容：`ui/ClientGuiScale`（固定介面大小 3，設定 `[gui] force_gui_scale_v1`）、`ui/HudLayout`（15 個 HUD 元件的位置／大小／開關，存 `config/provinggrounds-hud.json`）、`ui/HudEditScreen`（主選單首頁「HUD 介面設定」）、主選單／大廳／商店／選地圖重新排版。測試項目 UI-1～5、HE-1～7 在 `docs/TESTING.md` 最上面。等使用者測完回饋。
- **Spark 已移除**（2026-11-24，移到伺服器與客戶端的 mods-backup）。改裝 5 個優化模組：客戶端 Embeddium 0.3.31、ImmediatelyFast 1.5.5、Entity Culling 1.11.3、FerriteCore 6.0.1、ModernFix 5.27.91；伺服器只裝 FerriteCore、ModernFix（正式伺服器已實際開關一次正常）。使用者若回報畫面異常，先懷疑 Embeddium。FPS 報告分析：20% 在等 FPS 上限、地形繪製 33%、我們 HUD 6.5% 已改 drawManaged。
- 已完成（未發佈 v0.26.0）：電梯方塊（block/ElevatorEvents）、準星 65%＋黑框（TACZ 準星改 EMPTY）、拔刀動畫播完才加速（events/MeleeDrawSpeed）。
- **三個第三方資料包**（armored elytra、custom villager shops、elevators，在伺服器 `world/datapacks/`）每 tick 搜尋實體，佔伺服器 22.9%，建議移除，**還沒問使用者**。
- 未做：掉線分身（H1）、大逃殺掉線保留、團隊模式倒地開關、擊殺的穿牆樣式、大逃殺戰績面板。
