# 移植盤點（計畫書 C）

這份文件是為了日後兩件事做準備：

1. **移除 TACZ 與 lrtactical**，改用自製的槍械與道具。
2. **升級遊戲版本**（例如 26.x）。

現在還不實作，只先盤點「會被影響的地方」與「預期怎麼改」。之後動手時照著這份清單逐項處理即可。

> 26.x 的版本規則、所需 Java 版本、Forge／NeoForge 等載入器的支援狀況，這份文件**沒有**可靠資料；屆時請查官方文件，不要依這份文件推論。

---

## 1. 第三方模組引用（TACZ、lrtactical）

### 1-1. Java 程式

| 位置 | 用到什麼 | 日後改法 |
|---|---|---|
| `compat/TaczGunAdapter.java` | 用反射掛 TACZ 事件（開槍、近戰、換彈、命中、子彈打方塊）、`TimelessAPI` 查槍的類型 | 新增一個 `GunAdapter` 實作（例如 `OwnGunAdapter`），在 `GunCompat.init()` 改選它 |
| `compat/GunCompat.java` | 依 `ModList.isLoaded("tacz")` 選適配層 | 加上自製槍械的判斷 |
| `compat/AmmoCategories.java` | `tacz:ammo` 物品、`AmmoId` NBT、`TimelessAPI` 查子彈 | 改成自製子彈；介面 `ammoFor()`、`addAmmoForGuns()` 保留 |
| `mixin/GunDataMixin.java` | 目標類別 `com.tacz...GunData`（改槍吃的子彈） | 自製槍械不需要，直接刪除並從 `provinggrounds.mixins.json` 移除 |

**不在 `compat/` 的程式都已改成透過介面或資料**，不會直接寫死第三方 ID：

- **快捷欄規則**（`HotbarRules`）
  - 槍：`GunCompat.classify()`。
  - 補品、投擲物：物品標籤。
- **祭壇基礎裝備**（`RespawnKit`）：物品寫在設定 `[battle_royale.respawn_kit].items`。
- **子彈傷害判斷**（`ArmorEvents`、`BulletHitEvents`）：`GunCompat.BULLET_DAMAGE`，也就是標籤 `provinggrounds:bullets`。
- **戰利品模擬**（`LootSimulator`）：改用適配層與標籤分類。

### 1-2. 設定（`PGConfig`）

| 設定 | 內容 | 日後改法 |
|---|---|---|
| `[ammo]` 五個清單 | TACZ／suffuse 原本的子彈 ID → 我們的 5 大類 | 自製槍械直接吃大類彈藥，可整段移除 |
| `[battle_royale.respawn_kit].items` | `tacz:modern_kinetic_gun{GunId:"tacz:p320"}`、`lrtactical:consumable{ConsumableId:"lrtactical:ai2"}` | 改成自製物品 ID |

### 1-3. 資料檔（`src/main/resources/data/`）

| 檔案 | 內容 | 日後改法 |
|---|---|---|
| `br/loot_tables/pools/*.json` | 槍（`tacz:modern_kinetic_gun` + `GunId`）、配件（`tacz:attachment`）、醫療與投擲物（`lrtactical:*`） | 換成自製物品 |
| `br/loot_tables/crates/*.json` | 大類彈藥（`tacz:ammo` + `AmmoId`） | 換成自製子彈 |
| `provinggrounds/tags/items/healing.json`、`throwable.json`、`melee.json` | `lrtactical:consumable`、`lrtactical:throwable`、`lrtactical:melee` | 只改標籤內容 |
| `provinggrounds/tags/items/ammo.json`、`attachment.json` | `tacz:ammo`、`tacz:attachment` | 只改標籤內容 |
| `provinggrounds/tags/damage_type/bullets.json` | `#tacz:bullets` | 改成自製子彈的傷害類型 |

所有標籤項目都設成 `"required": false`。所以沒有安裝 TACZ／lrtactical 時，標籤只是空的，不會讓資料包載入失敗。

### 1-4. 槍械包（`gunpack/`）

- `provinggrounds_ammo`：5 大類彈藥的 TACZ 槍械包（彈藥定義、圖示）。
- `gunpack/tools/make_ammo_icons.ps1`：產生彈藥圖示。

移除 TACZ 後整個資料夾都不再需要。圖示可以沿用到自製子彈。

### 1-5. 沒有槍械模組時的行為（已實測）

測試方式：`.\gradlew.bat runServer -PnoGunMods`，這個參數會讓開發環境不載入 TACZ 與 lrtactical。

- **啟動：** 伺服器正常啟動，log 顯示 `No supported gun mod loaded; gun adapter disabled.`
- **槍械相關功能：** 自動停用（開槍攔截、爆頭防彈、玻璃記錄、彈藥分類），不會崩潰。
- **快捷欄規則：** 沒有槍可分類，第 1～3 格等於空著。
- **戰利品表：** 引用 TACZ／lrtactical 物品的表會載入失敗（log 有 `Couldn't parse element loot_tables:br:...`），箱子會是空的。這是預期的，換成自製物品後就會恢復。
- **舊世界：** 曾經裝過這些模組的世界會出現 `Unidentified mapping` 警告。**不要用正式世界做這個測試**，否則世界裡的 TACZ 物品會被移除。

---

## 2. Forge 專屬 API（換載入器或升級版本時逐項檢查）

### 2-1. 事件（`@SubscribeEvent`）

**伺服器端：**

| 檔案 | 處理的事件 |
|---|---|
| `events/WorldEvents` | 開關伺服器、每 tick |
| `events/PlayerEvents` | 登入、死亡 |
| `events/BREvents` | 受傷、致死、摔落 |
| `events/DownedEvents` | 互動、攻擊、丟棄、開容器、回血、下座椅 |
| `events/ArmorEvents` | 防彈 |
| `events/BulletHitEvents` | 擊退、抖動 |
| `events/GlassEvents` | 玻璃 |
| `events/HotbarEvents` | 撿取、換手、整理 |
| `events/BackpackEvents` | 背包讀寫 |
| `modes/br/BRMovement` | 移動 |
| `modes/br/StatsBoard` | 看板、放置與破壞權限 |
| `admin/SpawnPointView` | 生成點顯示同步 |

**客戶端：**

| 檔案 | 處理的事件 |
|---|---|
| `ui/ClientModEvents` | 註冊 HUD、按鍵、方塊實體繪製 |
| `ui/ClientForgeEvents` | 登出清除、HUD 隱藏原版元素等 |
| `ui/ClientSkydive` | 跳傘 |
| `ui/ClientWheel` | 輪盤 |
| `ui/UiWalk` | 畫面中移動 |
| `ui/ShiftDragMove` | Shift 拖移 |
| `ui/BackpackOverlay` | 背包欄、鎖格遮罩 |
| `ui/CircleRenderer` | 毒圈 |
| `ui/SpawnPointRenderer` | 生成點方框 |

第三方模組的事件（TACZ）只在 `compat/TaczGunAdapter` 用反射掛。

### 2-2. 網路封包

- **管道：** `network/PGNetwork` 用 Forge `SimpleChannel`。每次封包格式改變就把 `VERSION` 加 1，目前是 12。
- **封包：** 共 17 種，都是 `record`，有 `encode`／`decode`／`handle`。
- **換載入器時：** 改成新的封包 API 即可，封包內容不用動。
- **客戶端專用程式：** 用 `DistExecutor` 隔開，見 `MatchResultPacket`、`Stats*Packet`、`SpawnPointsPacket`、`FloatTextPacket`。

### 2-3. 存檔（`SavedData`）

| 類別 | 檔名（`world/data/`） | 內容 |
|---|---|---|
| `data/PlayerDataManager` | `provinggrounds_players.dat` | 玩家累計數據、分模式 `ModeStats` |
| `data/MapRegistry` | （見類別） | 地圖、箱子生成點 |
| `data/GlassLedger` | （見類別） | 被打破的玻璃 |
| `data/AirdropLedger` | （見類別） | 空投 |
| `admin/FloatText.Log` | `provinggrounds_float_text_log.dat` | 浮空文字刪除紀錄 |

另外，**玩家實體的 `persistentData`** 也存了資料：

- `pg_br`：BR 還原資料
- `pg_backpack`：背包欄
- 大廳標籤

能力（Capability）目前沒有使用。

### 2-4. 註冊

`registry/PGBlocks` 用 `DeferredRegister` 註冊以下內容：

- **方塊：** 箱子 ×4、祭壇、數據看板
- **物品：** 背包 ×3、復活牌、管理工具 ×5
- **方塊實體：** 箱子、祭壇、看板
- **創造模式分頁：** PG 管理工具

### 2-5. 設定（`ForgeConfigSpec`）

- `config/PGConfig`：伺服器設定，會同步到客戶端。
- `config/PGClientConfig`：客戶端設定。

注意：**已存在的 toml 不會自動帶入新的預設值**，只會補上新的鍵。

### 2-6. Mixin（每一個都說明為什麼不能用事件）

| Mixin | 目標 | 理由 |
|---|---|---|
| `GunDataMixin` | TACZ `GunData` | 改槍吃的子彈；TACZ 沒有對應事件 |
| `ItemStackMixin` | `ItemStack.getMaxDamage` | 防具耐久 ×2；耐久上限由物品類別決定，Forge 沒有事件 |
| `ContainerMenuMixin` | `AbstractContainerMenu.clicked` | 快捷欄規則的點擊檢查；Forge 沒有可取消的容器點擊事件 |
| `InventoryMenuMixin` | `InventoryMenu` 建構子 | 加背包欄；Forge 沒有替原版物品欄加格子的事件，而且計畫書要求不依賴 Curios |
| `SlotMixin` | `Slot.mayPlace` / `mayPickup` | 背包鎖格；所有畫面的主背包格都經過這裡 |
| `PlayerPoseMixin` | `Player.updatePlayerPose` | 趴下／倒地強制趴下姿勢；TACZ 的匍匐功能每 tick 會清掉本地玩家的強制姿勢，沒有事件能在它之後、原版決定姿勢之前介入 |

所有 Mixin 都沒有 refmap。注入點同時寫開發名稱與 SRG 名稱（`remap = false`），並設 `defaultRequire = 0`。換版本時要重新核對 SRG 名稱，或改用新載入器的對應方式。

### 2-7. 其他 Forge 專屬

| API | 用在哪裡 |
|---|---|
| `IGuiOverlay` | `HUD`、`ClientWheel` |
| `ServerLifecycleHooks` | `Backpack`、`StatsBoard` |
| `ModList` | 版本號、槍械模組偵測 |
| `ForgeRegistries` | 查物品 |
| `BlockEntity.getRenderBoundingBox` | 看板 |
| `onLoad` / `onChunkUnloaded` | 看板、祭壇 |

---

## 3. 規則（新功能也要遵守）

1. **隔離槍械模組：** `com.tacz` 與 `com.lrtactical` 的 import 只能出現在 `compat/` 底下。
   - Gradle 任務 `checkGunModIsolation` 掛在 `compileJava` 與 `check` 上，違反時建置失敗並列出檔案與行號。
2. **不寫死物品 ID：** 第三方物品 ID 只能放在設定、戰利品表、標籤這些資料檔裡，不寫死在程式。
3. **透過介面使用槍：** 新功能要判斷槍、讀槍的分類，一律透過 `GunCompat`／`GunAdapter`，不准繞過。
4. **少用 Mixin：** 能用事件解決的就不用 Mixin。新增 Mixin 時要寫明理由，並更新上面的表格。
