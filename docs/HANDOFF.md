# 交接文件（給接手的 Claude 對話）

> 用途：對話記憶（上下文）快滿時，把重點交接給新的對話。**接手的對話先讀完這份**，記憶到 90% 時更新這份文件、提交，再開新對話交接（使用者會提醒）。
> 最後更新：2026-11-26（v0.33.3 已發佈，網路版本維持 28，等待使用者於正式客戶端實測 `C1～C9` 回饋）。

## 1. 使用者與溝通
- 一律用**繁體中文**；使用者第一次做 Forge 模組、不懂程式：每一步先用白話說明「要做什麼、為什麼、做完會怎樣」，術語第一次出現附一句解釋。
- 使用者常用**手機遠端**看：結尾訊息要能獨立看懂，最後一句寫出問題與選項（例如「要現在發佈嗎？（現在發佈／先不要）」）。
- 回報時要誠實：哪些實測過、哪些只編譯過、哪些沒做，都講清楚。
- 對話記憶用到 **90%** 要在回覆開頭提醒（`get_usage` 的 `context.percentUsed`）。
- 自動記憶資料夾（`C:\Users\assas\.claude\projects\...tacz-reborn\memory\`，索引 `MEMORY.md`）跟著工作資料夾自動載入，裡面有更多偏好。

## 2. 規則
- **分工（使用者確認過）**：模組的所有實作（新要求、回饋、修錯）都由「目前接手的那個對話」負責，其他對話不寫程式。對話記憶用到 **90%**（`get_usage` 的 `context.percentUsed`）時：先在回覆開頭提醒使用者 → 更新本檔（最後更新日期、已完成、進行中／待決定）並提交 → 開新對話交接，並把這條「所有實作由新對話負責、90% 交接」的規則一起交代下去。
- **Claude 與 Gemini 輪流（使用者 2026-11-26 確認）**：使用者會輪流用 Claude 和 Gemini 改這個模組，**同一時間只有一個 AI 在這個資料夾工作**，都在 `feature/phase1-setup` 分支上，不另開分支或資料夾（避免合併衝突）。
  - 開始前：先讀本檔，跑 `git status`、`git log -3` 看上一個 AI 做到哪；有不是自己改的、還沒提交的檔案就先問使用者，不要覆蓋、不要 stash、不要切分支。
  - 結束前：全部提交，並更新本檔的「最後更新」與「進行中／待決定」，讓下一個 AI 接得上。
  - 提交訊息結尾寫實際做事的 AI（例如 Gemini 寫自己的名字），不要冒用別的 AI 的名字。
- **發佈與驗證節奏（使用者 2026-11-26 新規則）**：連續修改調整期間先改程式與文件，**等使用者決定要發佈時再一次執行 `gradlew build`、開發伺服器開機檢查與 `git commit`**，避免每改一小項就等編譯開服。
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
- **v0.33.3 已發佈（分身藏 ID、UI 跳躍、站姿同步轉身、斷線無敵修復、佔點不噴裝、移除 C4、職業限撿與槍王丟槍，Gemini 實作，待正式客戶端實測）**：
  1. `special/client/DecoyRenderer.java` + `special/entity/DecoyEntity.java`：分身隱藏頭頂 ID（`shouldShowName = false`）。
  2. `ui/UiWalk.java`：開 UI 介面時一併接管跳躍鍵（`in.jumping = jump`）。
  3. `ui/ClientForgeEvents.java` + `core/Hitbox.java`：站立／蹲下／趴下全面同步 `yBodyRot` 與視角朝向（消除 45° 轉向延遲）。
  4. `core/DisconnectBodies.java` + `venue/VenueManager.java` + `events/PlayerEvents.java` + `modes/BattleRoyaleMode.java` + `modes/arena/ArenaMode.java`：修復斷線重連後無敵（迷你死鬥斷線不再清空場地紀錄且登入自動偵測場地；重連強制清 `invulnerable`／`abilities.invulnerable`／`spawnInvulnerableTime`／`ETHEREAL`／`protectUntil`；BR 回歸補齊 `GameManager.addPlayer` 與隊伍同步）。
  5. `modes/arena/DomMode.java` + `modes/arena/ArenaManager.java`：佔點死亡直接清空玩家背包並取消全部掉落物（不噴裝、不產墓碑）。
  6. `data/provinggrounds/arena/shop.json` + `assets/lrtactical/lang/zh_tw.json`：移除全部 C4。
  7. `modes/arena/ArenaClass.java` + `modes/arena/ArenaMode.java` + `modes/arena/ArenaManager.java`：團隊死鬥／佔點禁止拾取非本職業限定槍械。
  8. `assets/provinggrounds/lang/zh_tw.json` + `en_us.json`：「槍戰對決」改名為「槍王之王」。
  9. `modes/arena/GkMode.java` + `modes/arena/ArenaMode.java` + `modes/arena/ArenaManager.java`：槍王之王允許丟棄槍械並以 `pg_gk_owner` 鎖定僅限本人拾取，沒有武器滿 5 秒自動補發本階段武器並清除場上丟棄的槍。
- **v0.33.2 已發佈（蹲下 Hitbox、趴下手感、室內團隊精簡、護甲修復包／殘影／彈簧鞋、佔點冷卻與獨立定價，Gemini 實作，待正式客戶端實測）**：
  1. `core/Hitbox.java`：蹲下時補上 `-0.125f` Y 位移（對齊原版 `PlayerRenderer.getRenderOffset`），修復蹲下 Hitbox 整體偏高。
  2. `ui/ClientMovement.java` + `core/Footsteps.java` + `network/QuietWalkPacket.java` + `core/ProneManager.java`：趴下／倒地爬行時雙端禁止蹲下（`shiftKeyDown = false`）與靜走。
  3. `ui/ClientForgeEvents.java` + `core/ProneManager.java` + `config/PGConfig.java`：移除 0.5x 趴下動畫放慢（動畫時間減半，約 0.55 秒），冷卻預設改鍵名 `prone.cooldown_seconds_v2 = 0.5`（減半）。
  4. `special/DomGear.java` + `special/NewSpecials.java`：團隊模式（佔點選單、團隊死鬥隨機池、團隊死鬥賭徒釣竿掉落）排除 6 種不適合室內作戰的道具（三種重新跳傘、擬態、兩把雷神錘）。
  5. `special/SpecialType.java` + `special/SpecialUse.java` + `special/SpecialConfig.java` + `mixin/ItemStackMixin.java` + `events/ArmorEvents.java` + `ui/ClientForgeEvents.java` + 語系檔：殘影分身改為可用 2 次；護甲修復包新增耐久上限 +25%（不可疊加，`pg_dura_bonus`）；強化護甲修復包改為抗彈係數 +1.5%（不可疊加，`pg_bp_bonus`），並於防具提示顯示強化效果。
  6. `special/DomGear.java` + `modes/arena/ArenaConfig.java` + `modes/arena/ArenaMode.java` + `client/ClientDomGear.java` + `special/client/SpecialHotbar.java` + `ui/LoadoutScreen.java` + `ui/ShopScreen.java`：佔點模式特殊道具改為依強度冷卻回合（弱 1／中 2／強 3，以 NBT `pg_dom_cd` 同步顯示），並調降商店加價為 51 種道具各自獨立定價（弱 +2%～+8%、中 +8%～+16%、強 +18%～+28%，支援 `item_markup_overrides_v1` 單獨覆寫），網路協定維持 28（small 更新）。
  7. `special/SpecialType.java` + `special/SpecialUse.java` + `special/DomGear.java` + 語系檔：彈簧鞋改為可用 2 次（第 1 次 60% 衝刺強度、第 2 次 40% 強度），並讓佔點模式中所有 `twoUses()` 道具可同回合用 2 次；全部已通過 `gradlew build` 與 `gradlew runServer`（0 ERROR），已發佈為 `v0.33.2`。
- **v0.33.1 已發佈（趴下動畫重製與 Hitbox 全面對齊，Gemini 實作）**：
  1. `mixin/ProneAnimMixin.java`：接管陸地 `PlayerRenderer.setupRotations`（`m_7523_`），將貼地偏移（`Hitbox.PRONE_Y/Z`）移到 `RX(-90°)` 之前並套用 `proneEase` 平滑曲線，徹底消除插進地板問題。
  2. `core/Hitbox.java` 重寫為原版 `Matrix4f` 轉換鏈＋共用 `computeLimbPose()`：修正四肢走路擺動相反、支援 `swimAmount` 平滑過渡插值（`ProneManager` 伺服器端也同步 0.5x 過渡速度）、完整對齊手持物品／使用動作（舉盾／拉弓／弩／望遠鏡／長矛）／攻擊揮手／TACZ 持槍與開鏡（新增 `GunCompat.aimProgress`）／降落傘／陸地趴下爬行與趴下持槍。
  3. `mixin/HumanoidArmsMixin.java`（priority 1500）：陸地趴下時直接套用 `Hitbox.computeLimbPose()`，取代原版水中划水動作並保證畫面四肢與 Hitbox 100% 一致。
  4. `ui/ClientForgeEvents.java` + `core/ProneManager.java`：每 tick 與每渲染幀將趴下玩家的 `yBodyRot` 鎖定跟隨視角，消除 45° 轉向延遲。
  5. 網路版本維持 28；`gradlew build`、`gradlew runServer` 與 `release.ps1 -Level small`（v0.33.1）皆已通過，等待使用者實測 `TESTING.md` 的 `A1～A6`。
- **v0.33.0 已發佈（計畫書 10＋領域展開實體碰撞，Gemini 實作）**：詳細說明在 `docs/PLAN10.md`。
- **v0.32.0／v0.32.1 已發佈（計畫書 9＋熱修）**：詳細紀錄在 `docs/PLAN9.md`。
- **未發佈（下一版 v0.30.2）**：標點不被方塊擋住＋顯示在地圖、致盲珠速度 ×2、閃光盾耐久 50、強心劑自傷不擊退、距離減傷兩段、刀打玻璃要冷卻、墓碑重力、近戰傷害平衡（左鍵 20／右鍵 55）。說明在 `docs/NEXT_RELEASE_NOTES.md`，測試在 `docs/TESTING.md` 最上面 L1～L8。都已提交、建置與開發伺服器通過，還沒實測、還沒發佈（發佈前照規則問伺服器與客戶端關了沒）。
- **佔點特殊物品裝備（計畫書 8）已完成、未發佈**（eab405c）：佔點移除職業、自選三個（冷卻制）、商店一次性特殊物品、長按 5 特殊物品輪盤、佔點投擲物改長按 6、每回合冷卻歸零。強度表與做法在 `docs/PLAN8_DOM_SPECIALS.md` 最後；核心在 `special/DomGear.java`；網路版本 25。測試 D1～D11 在 `docs/TESTING.md` 最上面。建置與開發伺服器通過，**還沒在真正客戶端實測**。使用者說不確定怎麼設計比較好玩，設計是 Claude 決定的，回饋後可能要調。
- 這個對話（「電腦4.0」）做過的重點：計畫書 7 全部（v0.29.0）、標點（X，近戰改 Y）、防彈只算被打中部位（`mixin/ArmorAbsorbMixin`＋`events/ArmorEvents`，防彈率已減半）、一行匯入地圖（`/pg map import auto`）、移除跑酷方塊（`events/LegacyCleanup` 清排行榜）、子彈穿透方塊（改伺服器與客戶端 `tacz/immersive_ballistic/data/tacz/tags/blocks/bullet_ignore.json`，原檔備份 `C:\mcdatapackackupsullet_ignore_20261125`）、隊友一律藍色（`ui/ClientTeamColors`）、刀子距離 1.75（`events/MeleeReach`）、下拉選單（`ui/Dropdown`）。
- **計畫書 7（`docs/PLAN7.md`，使用者手機端寫的第七份）**：A1～A8、B、C1～C11、D、E、F、G 全部完成並提交，建置與開發伺服器啟動通過（**都還沒在真正客戶端、多人實測**）。每項的做法、與計畫書不同的地方都寫在 PLAN7.md 最後的「電腦端進度紀錄」。發佈說明 `docs/NEXT_RELEASE_NOTES.md`、測試步驟 `docs/TESTING.md` 最上面已寫好。網路版本 23。墓碑畫面沒有套用 A4 的位置設定（回報裡說明）。
- **計畫書 6（`docs/PLAN6.md`）**：A1～A21、B、C、D、E 全部完成並提交，建置與開發伺服器啟動通過（**都還沒在真正客戶端實測**）。發佈說明 `docs/NEXT_RELEASE_NOTES.md`、測試步驟 `docs/TESTING.md` 最上面已寫好。A22 已移除 variantbarrels（jar 在伺服器與客戶端的 mods-backup），v0.28.0 已發佈，等使用者實測回饋。
- v0.27.0 已發佈（比賽中調 HUD、子彈彈道、降落傘繩、圈外受傷聲、炸毀方塊開關、補品互換——互換已在計畫書 6 A2 拿掉）。
- **介面大小 3 重新排版＋HUD 自訂畫面**：已完成並提交（0b85849，屬於未發佈的 v0.26.0），建置與開發伺服器啟動都通過，**還沒在真正客戶端看過**。內容：`ui/ClientGuiScale`（固定介面大小 3，設定 `[gui] force_gui_scale_v1`）、`ui/HudLayout`（15 個 HUD 元件的位置／大小／開關，存 `config/provinggrounds-hud.json`）、`ui/HudEditScreen`（主選單首頁「HUD 介面設定」）、主選單／大廳／商店／選地圖重新排版。測試項目 UI-1～5、HE-1～7 在 `docs/TESTING.md` 最上面。等使用者測完回饋。
- **Spark 已移除**（2026-11-24，移到伺服器與客戶端的 mods-backup）。改裝 5 個優化模組：客戶端 Embeddium 0.3.31、ImmediatelyFast 1.5.5、Entity Culling 1.11.3、FerriteCore 6.0.1、ModernFix 5.27.91；伺服器只裝 FerriteCore、ModernFix（正式伺服器已實際開關一次正常）。使用者若回報畫面異常，先懷疑 Embeddium。FPS 報告分析：20% 在等 FPS 上限、地形繪製 33%、我們 HUD 6.5% 已改 drawManaged。
- 已完成（未發佈 v0.26.0）：電梯方塊（block/ElevatorEvents）、準星 65%＋黑框（TACZ 準星改 EMPTY）、拔刀動畫播完才加速（events/MeleeDrawSpeed）。
- **三個第三方資料包**（armored elytra、custom villager shops、elevators，在伺服器 `world/datapacks/`）每 tick 搜尋實體，佔伺服器 22.9%，建議移除，**還沒問使用者**。
- 未做：掉線分身（H1）、大逃殺掉線保留、團隊模式倒地開關、擊殺的穿牆樣式、大逃殺戰績面板。
