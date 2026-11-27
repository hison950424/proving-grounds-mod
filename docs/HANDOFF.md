# 交接文件（給接手的 Claude 對話）

> 用途：對話記憶（上下文）快滿時，把重點交接給新的對話。**接手的對話先讀完這份**，記憶到 90% 時更新這份文件、提交，再開新對話交接（使用者會提醒）。
> 最後更新：2026-11-27（v0.40.1 開發中：新增室內團隊地圖「黑曜地堡」`/pg arena map generate`＋多團隊場地管理 `ArenaMapRegistry`＋決賽圈半徑 `11.5` 格＋縮圈邊界逐幀平滑移動 `ClientBRState.smoothCircle`＋其他容器替換背包放回原容器 `Backpack.handleContainerClick`＋HUD 設定面板收起／展開 `HudEditScreen`＋大地圖標題移至左側避開按鈕 `BigMapScreen`＋5 張地圖三層高密度升級與原地重建 `/pg map generate regen`，網路版本維持 `"35"`；前版 v0.40.0 已發佈於 `4ab4c96`）。

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
- **v0.40.1（決賽圈 `11.5` 格＋邊界平滑移動＋容器替換背包＋HUD 面板收起與大地圖標題位置調整＋5 張地圖三層高密度升級與原地重建）**：
  - `BattleRoyaleMode.tick()` 縮圈時 10Hz 同步＋`ClientBRState.smoothCircle()` 逐幀線性插值驅動 `CircleRenderer`／`HUD`／`BigMapScreen`，消除邊界跳格；`Backpack.handleContainerClick()`＋`ContainerMenuMixin` 支援在其他容器內 Shift+點擊或游標點擊替換背包時將舊背包放回原容器。
  - `HudEditScreen` 支援側邊設定面板「收起／展開設定」；`BigMapScreen.renderMapTitle` 將地圖名稱（含「地圖預覽：xxx」）移至地圖左側顯示（極窄視窗自動退回安全截斷置放），避免與「HUD 設定」按鈕重疊。
  - 新增室內近距團隊地圖「黑曜地堡」：`ObsidianBunkerBlueprint`（85×14×65 雙層全室內、走道 3～4 格寬、高密度沙包／機櫃／貨櫃掩體、紅藍 3 出口、4 樓梯＋2F 天橋）＋`ArenaMapRegistry`（SavedData 切換 `default` 與自訂場地）＋`ArenaMapGenerator`＋`/pg arena map <generate|regen|list|select|tp|status|cancel>`，連動 `ArenaMode`／`TdmMode`／`DomMode`／`GkMode`；新增 `ObsidianBunkerBlueprintTest` 驗證 3D BFS 屏障封鎖與全圖連通性。
  - 新增 `MapRelightJob`（`ChunkSkyLightSources` + `ThreadedLevelLightEngine` 天空／方塊光照清空重傳並補送 `ClientboundLightUpdatePacket`）＋`/pg map relight [all|<id>|status|cancel]`，同時接入 `BRMapGenerator` 的 `RELIGHT` 階段與 `initializeLightSources()`，徹底解決 5 張新地圖地面殘留陰影問題。
  - `Circle.FINAL_RADIUS` 由 `7.5` 改為 `11.5` 格（並同步更新 `CircleCommand`/`CirclePreset`/`PGConfig` 說明）。
  - 新增 `admin/build/WildStructures.java`（8 種無木箱小型建築 `HOUSE`/`GAS_STATION`/`BUNKER`/`WAREHOUSE`/`MINKA`/`COTTAGE`/`ADOBE`/`ARCTIC_POD`＋4 種野區微據點 `CHECKPOINT`/`FIELD_CAMP`/`CONTAINER_YARD`/`RUIN_WALLS`），擴充 `BRMapLayout` 自動在大城區間填入小房區（帶支線小路）、微據點與密集樹林／岩脊／戰術矮牆掩體，並在 `BRMapGenerator` 加入 `/pg map generate regen` 原地重建機制。
  - 網路協定維持 `PGNetwork.VERSION="35"`（屬於 `small` 更新）；擴充 `BRMapLayoutTest` 驗證全部三層建築零重疊與數量密度。
- **v0.40.0（5 張主題大逃殺地圖生成＋大廳地圖預覽＋管理員右鍵傳送，已於 `4ab4c96` 發佈）**：
  - 新增 `BRMapPresets`、`BRMapLayout`、`BRMapGenerator` 與 `/pg map generate <list|all|status|cancel|preset>`：提供 `300 / 400 / 500 / 600 / 700` 五張主題分區密集建築群地圖（共 17/22/30/38/55 棟無木箱建築）、放射與環狀道路及跨河橋、野外戰術掩體、多座復活祭壇（5/7/9/11/13 座並寫入 `AltarLedger`）與自動 `MapRenderJob` 俯視圖；新增 `BRMapLayoutTest` 驗證零重疊與邊界/祭壇配置。
  - `MenuDataPacket.MapEntry` 擴充地圖中心／半徑，`MenuRequestPacket` 與 `LobbyManager` 在玩家進入大廳、開啟選單、切換大逃殺地圖或切回大逃殺模式時同步大逃殺地圖俯視圖（`MapImagePacket`）。
  - `MainMenuScreen` 於大廳大逃殺模式為所有玩家提供「預覽大逃殺地圖」入口；`BigMapScreen` 支援大廳預覽與管理員右鍵點擊任意位置開啟選單並透過 `MapTeleportPacket`（伺服器強制載入區塊＋高程安全檢查）傳送過去。
  - `PGNetwork.VERSION` 已升為 `"35"`（下次發佈需走 `big`）。
- **v0.39.0（室內物資點緊貼掩體＋8 大風格各新增 3 棟建築共 52 棟＋半磚平滑與平地等速，已於 `4c43820` 發佈）**：
  - `SlabRampShapes`＋`BlockCollisionMixin` 對生物將下半磚與相鄰上半磚／實心方塊高差接縫轉為 16 階／8 階平滑斜坡，`StairMovement` 擴充支援半磚斜坡 3D 等速補償與下階貼地。
  - `BuildingCanvas.finalizeLootNearCover()` 在定型時自動把開闊處物資點吸附到鄰近掩體旁並避開門洞；同時 8 個建築檔共調整既有走廊物資點並新增 24 棟建築（皆無木箱、無新增電梯、寬樓梯與多入口掩體）。
  - 已更新 `PGNetwork.VERSION` 為 `"34"`（`v0.39.0` `big`），並新增 `SlabRampShapesTest` 與 `BuildingCatalogTest`；尚未於真實客戶端／伺服器實測（見 `docs/TESTING.md` 的 `J1～J4`、`K1～K3`）。
- **v0.38.2（近戰破玻璃依各刀真實揮刀命中幀＋樓梯與平地 3D 等速＋攀爬放慢 100% 與方向取消＋決賽圈半徑 7.5 格，已於 `c0e5b97` 發佈）**：
  1. `mixin/MeleeGlassMixin.java` + `provinggrounds.mixins.json` + `compat/GunCompat.java`：新增 `@Pseudo` Mixin 掛勾 `CombatProperties.postAttack` 呼叫 `IMeleeWeapon.attack` 的瞬間，讓伺服器在每把 `lrtactical` 近戰武器各自真正的揮刀命中幀呼叫 `MeleeGlassPacket.tryBreakGlassAtAim`，自動完全遵循每把刀的拔刀時間、左／右鍵與連段揮刀延遲與冷卻。
  2. `network/MeleeGlassPacket.java` + `ui/ClientMeleeGlass.java`：`lrtactical` 近戰武器不再於滑鼠按下瞬間送包，也不再受 `COOLDOWN_LIGHT = 10` / `COOLDOWN_HEAVY = 14` 固定冷卻限制；封包格式與 `PGNetwork.VERSION = "33"` 維持不變（屬 `small` 更新），僅為非 `lrtactical` 近戰武器保留原本按鍵破玻璃後備路徑。
  3. `ui/StairMovement.java` + `mixin/StairMovementMixin.java`：移除寫死 `0.70` 減速，改為依玩家實際移動方向對 45° 斜坡量測腳底幾何坡度 `m = |dy| / 0.25`（0.5 格高腳底框避免矮天花板誤擋），套用 `1 / sqrt(1 + m^2)` 使 3D 實際移動速度在直上、直下、斜走、橫走與平地平台皆維持與平地 100% 相同，並補足 3D 距離至 `walkDist` 與下樓 `moveDist` 維持相同視野晃動與腳步聲節奏。
  4. `core/WallClimb.java` + `ui/ClientWallClimb.java` + `network/WallClimbPacket.java`：攀爬速度減慢 100%（`STOW_TICKS = 4`、`DURATION_TICKS = 18`，共 22 tick ≈ 1.1 秒，`TaczRedraw.expect` 延長至 `4000L`）；攀爬中偵測新按下後退／左／右鍵（或第 2 tick 起持續按後退）即朝該水平方向輕推取消攀爬，本地立即恢復動作與舉槍動畫，並透過 `WallClimbPacket.cancel()`（`yaw = NaN`，不改封包格式與版本 `33`）通知伺服器解除鎖定及同步所有客戶端。
  5. `modes/br/Circle.java` + `command/CircleCommand.java` + `config/PGConfig.java` + `modes/br/CirclePreset.java`：大逃殺決賽圈固定半徑 `Circle.FINAL_RADIUS` 由 `2.5` 格增加 5 格改為 `7.5` 格（直徑 15 格），並同步更新指令顯示與註解說明。
- **v0.38.0 已發佈（樓梯 45° 斜坡、柵欄石牆 1 格高、跳躍二段空白鍵翻爬零抖動與切刀演出，Gemini 實作）**：
  1. `core/StairRampShapes.java` + `mixin/BlockCollisionMixin.java`：雙端將所有正放樓梯（`Half.BOTTOM` 含直階與內／外轉角）改為 16 階（每階 0.0625 格）45° 平滑斜坡碰撞箱；並將 `#minecraft:fences`、`#minecraft:walls` 與關閉中 `#minecraft:fence_gates` 碰撞高度改為 1 格高可直接跳過。
  2. `core/WallClimb.java` + `ui/ClientWallClimb.java` + `mixin/StairMovementMixin.java` + `network/WallClimbPacket.java` + `network/WallClimbSyncPacket.java`：改為「跳躍浮空後再按空白鍵」即可攀爬前方 ±45° 內、頂面不高於自身頭頂的任意碰撞方塊；於 `LivingEntity.travel` HEAD 接管本地移動並取消伺服器每 tick `setPos` 回扯，徹底消除第一人稱視角抖動。
  3. 第一人稱與重新切刀／切槍：`ui/ClientWallClimb.java` 于 `RenderHandEvent` 先 2 tick 下收手持物品後改畫雙手前抓向下撐起手臂（`renderPlayerArm` `m_109346_`）；翻完透過 `ClientGunAim.triggerItemDraw`（`AnimateGeoItemRenderer.tryInit` + TACZ `draw`）重播 `lrtactical` 切刀與 `TACZ` 切槍動畫，並以 `MeleeDrawSpeed.restartDraw` 重置伺服器拔刀計時。
  4. 同步包含 `ActionLock` 動作鎖定、`EntityTurnMixin` 攀爬左右 ±45° 轉頭限制、`ItemInHandLayerMixin` 第三人稱空手、`Hitbox`＋`ProneAnimMixin`＋`HumanoidArmsMixin` 同步判定框，以及 `PGNetwork.VERSION = "33"`。
- **v0.37.0 已發佈（近戰右鍵破玻璃／鐵欄、柵欄石牆攀爬完善、跳傘優化與樓梯一致化，Gemini 實作，待正式客戶端實測 `G1～G6`）**：
  1. `ui/ClientMeleeGlass.java` + `network/MeleeGlassPacket.java`：支援近戰左／右鍵破壞玻璃、玻璃片與鐵欄杆（監聽 `InteractionKeyMappingTriggered` 與 `MouseButton.Pre` 防 `lrtactical` 攔截；右鍵冷卻 14 tick）。
  2. `core/WallClimb.java` + `network/WallClimbPacket.java` + `network/WallClimbSyncPacket.java` + `ui/ClientWallClimb.java` + `mixin/ItemInHandLayerMixin.java`：需視野面向牆面（±55° 內）才觸發攀爬；持物／持槍時先 3 tick 收起物品騰雙手再翻爬（第一／三人稱隱藏手持物品），爬完重播切換舉起與 TACZ draw 動畫；透過 `ActionLock` + `DownedEvents` + `ClientGunAim`／`GunCompat` 禁止開槍、攻擊、使用與中斷持續吃藥／換彈。
  3. `mixin/EntityTurnMixin.java` + `ui/ClientForgeEvents.java` + `ui/ClientSkydive.java`：在 `Entity.turn` 輸入層直接硬限攀爬左右 ±45° 與跳傘鞘翅抬頭角度，消除橡皮筋拉扯感。
  4. `ui/HUD.java` + `ui/HudLayout.java` + `ui/ParachuteRenderer.java` + `modes/BattleRoyaleMode.java` + `events/BREvents.java` + `special/NewSpecials.java` + `special/SpecialEvents.java`：拉高提示高度並清除飛機／自動開傘重複提示，雙腳落地當下雙端立即結束跳傘狀態並保留 12 tick 摔落傷害免疫。
  5. `core/Hitbox.java` + `mixin/ProneAnimMixin.java` + `mixin/HumanoidArmsMixin.java` + `PGNetwork.java`（VERSION `31` → `32`）：攀爬動畫與伺服器／`F3+B` Hitbox 完全同步。
  6. `ui/StairMovement.java` + `mixin/StairMovementMixin.java`：上、下樓梯水平速度降 30% 且不中斷疾跑，下樓梯自動貼地降階踩過每一格台階、維持上下一致的速度感。
- **v0.36.0 已發佈（大逃殺電梯戰術平衡＋8 大風格共 28 棟超大型無木箱高物資建築，Gemini 實作）**：
  1. `block/ElevatorEvents.java`：大逃殺進行中（非創造／旁觀）搭乘電梯需讀條 **1.8 秒**，兩層播放機械警示音與雲霧粒子，離開方塊或受 `LivingHurtEvent` 傷害立即中斷，到達後響鈴並冷卻 **6 秒**（大廳／創造／團隊模式維持即時傳送）。
  2. `BuildingStyle.java` + `BuildingPickerScreen.java` + `BuildingCatalog.java`：擴充為 **8 大風格**（2 列 × 4 欄分頁）、`MAX_UNDO = 8`、`PGNetwork.VERSION` 升為 `31`。
  3. 升級既有 4 風格（各補強掩體與物資點＋各新增 1 棟超大型：`mod_prison_fortress`、`ind_oil_refinery`、`jp_edo_street`、`sci_mech_bay`），並新建 `CivicBuildings.java`、`MedievalBuildings.java`、`DesertBuildings.java`、`MaritimeBuildings.java` 各 3 棟（合計 **28 棟**，每棟 **34～52 個物資點**，零木箱，僅 4 棟超高建築留單座電梯）。
- **v0.35.0 已發佈（建築生成修復、12 棟高物資大型無木箱建築、物資工具準星指向＋3D 預覽框，Gemini 實作）**：
  1. `admin/build/BuildingCanvas.java`：`finalizeShapes()` 改為兩輪 `Block.updateFromNeighbourShapes`（第二輪依 Y 由高到低排序）修復玻璃片／石牆／鐵欄／柵欄／樓梯轉角連接；新增 `staircaseZ`／`staircaseX` 讓最後一階精準與上層地板齊平；補地基深度擴為 16 格；新增無木箱室內掩體與照明輔助函式。
  2. `ModernBuildings.java`、`IndustrialBuildings.java`、`JapaneseBuildings.java`、`ScifiBuildings.java`：刪除舊 40 棟建築，重製 4 風格各 3 棟（共 12 棟，尺寸 `29×25×19`～`39×31×18`，每棟 **28～36 個物資點**）大型／超大型建築與豐富掩體內裝（零木箱／木桶）。
  3. `admin/AdminTools.java`、`admin/SpawnPointView.java`、`ui/SpawnPointRenderer.java`、`item/AdminToolItem.java`：`SPAWN_PLACER`／`SPAWN_REMOVER` 改為準星 16 格射線指向判定（防隔牆誤刪），手持自動顯示黃框，放置顯示綠／橘預覽框，刪除顯示紅鎖定放大框＋交叉線。
  4. `BuildingCatalog.java`、`BuildingStyle.java`、`ui/BuildingPickerScreen.java`、`PGNetwork.java`（VERSION `29` → `30`）：更新 12 棟註解／上限 `MAX_UNDO = 10`／舊 NBT 失效自動彈窗重選。
- **v0.34.0 已發佈（大逃殺建築生成工具與 4 風格共 40 棟建築，Gemini 實作）**：
  1. `admin/build/BuildingStyle.java` + `BuildingBlueprint.java` + `BuildingCanvas.java` + `BuildingCatalog.java`：支援 4 向旋轉、飛行對地（24 格）、斜坡往下補地基（12 格）、自動登記物資點到目前所在的 BR 地圖、最多 15 筆一鍵復原（還原方塊＋移除新增物資點）。
  2. `ModernBuildings.java`（10 棟）、`IndustrialBuildings.java`（10 棟）、`JapaneseBuildings.java`（10 棟）、`ScifiBuildings.java`（10 棟）：4 種風格共 40 棟命名建築。
  3. `item/AdminToolItem.java` + `command/PGCommand.java` + `network/BuildingPacket.java` + `PGNetwork.java`（VERSION 升為 `29`，下次發佈為 `big` → `v0.34.0`）：新增 `building_placer` 與 `building_undo` 工具物品與 `/pg build undo`。
  4. `ui/BuildingPickerScreen.java` + `ui/BuildingPreviewRenderer.java`＋圖示／模型／語系檔：4 風格分類選擇 UI 與手持 3D 青色佔地／金色正門預覽框。
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
