package com.provinggrounds.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 檔案: PGConfig.java
 * 目的: 伺服器端設定（world/serverconfig/provinggrounds-server.toml）。
 *       可透過 /pg reload 熱重載，不需重啟伺服器。
 *       標「占位」的數值是尚未定案的暫定值。
 */
public class PGConfig {

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final PGConfig SERVER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SERVER = new PGConfig(builder);
        SERVER_SPEC = builder.build();
    }

    /** 原版疾跑速度（格/秒），用來推算 BR 內的疾跑速度與縮圈速度上限。 */
    public static final double VANILLA_SPRINT_SPEED = 5.612;

    // ── 大廳 ──────────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue lobbyCountdownSeconds;
    public final ForgeConfigSpec.IntValue minPlayersToStart;
    public final ForgeConfigSpec.IntValue forceStartSeconds;
    public final ForgeConfigSpec.DoubleValue endSoundVolume;

    // ── BR：縮圈 ──────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue brGameDurationSeconds;
    public final ForgeConfigSpec.IntValue brCircleWarnSeconds;
    public final ForgeConfigSpec.DoubleValue brFastSpeed, brStandardSpeed, brSlowSpeed;
    public final ForgeConfigSpec.IntValue brFastFirstWait, brStandardFirstWait, brSlowFirstWait;
    public final ForgeConfigSpec.ConfigValue<List<? extends Double>> brFastRatios;
    public final ForgeConfigSpec.ConfigValue<List<? extends Double>> brStandardRatios;
    public final ForgeConfigSpec.ConfigValue<List<? extends Double>> brSlowRatios;
    public final ForgeConfigSpec.IntValue brFastInterval;
    public final ForgeConfigSpec.IntValue brStandardInterval;
    public final ForgeConfigSpec.IntValue brSlowInterval;
    public final ForgeConfigSpec.DoubleValue brDamageBase;
    public final ForgeConfigSpec.DoubleValue brDamagePerStage;

    // ── BR：飛機與跳傘 ─────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue brPlaneHeight;
    public final ForgeConfigSpec.DoubleValue brPlaneSpeed;
    public final ForgeConfigSpec.IntValue boardLockSeconds;
    public final ForgeConfigSpec.IntValue jumpInvisibleSeconds;
    // 跳傘（計畫書 B1）
    public final ForgeConfigSpec.DoubleValue skydiveAutoChuteHeight, chuteSink, chuteHorizontal, glidePitchUpMax;
    public final ForgeConfigSpec.BooleanValue hotbarRulesEnabled;
    public final ForgeConfigSpec.DoubleValue wheelHoldSeconds;
    public final ForgeConfigSpec.BooleanValue uiWalkAllowed;
    public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> randomMelee;
    public final ForgeConfigSpec.IntValue backpackColumns;
    public final ForgeConfigSpec.DoubleValue mvpKill, mvpAssist, mvpDamagePerPoint, mvpRevive, mvpAltar, mvpWinBonus;
    public final ForgeConfigSpec.DoubleValue adminTextDeleteRange, adminSpawnViewRange;
    public final ForgeConfigSpec.IntValue adminTextUndoMax;
    public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> respawnKitItems;

    // ── BR：移動 ──────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.DoubleValue brSpeedMultiplier, brMeleeSpeedMultiplier;
    public final ForgeConfigSpec.DoubleValue brJumpHeight;

    // ── BR：戰利品 ────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.DoubleValue lootSpawnChance;
    public final ForgeConfigSpec.IntValue lootWeightLow;
    public final ForgeConfigSpec.IntValue lootWeightMid;
    public final ForgeConfigSpec.IntValue lootWeightHigh;
    public final ForgeConfigSpec.IntValue lootPointsPerTick;

    // BR 空投
    public final ForgeConfigSpec.DoubleValue brAirdropTwoChance;
    public final ForgeConfigSpec.IntValue brAirdropMinGapSeconds;
    public final ForgeConfigSpec.DoubleValue brAirdropRadiusFactor;
    public final ForgeConfigSpec.IntValue brAirdropHeight;
    public final ForgeConfigSpec.DoubleValue brAirdropFallSpeed;

    // BR 倒地 / 扶起
    public enum DownedStyle { CRAWL, SIT }
    public final ForgeConfigSpec.EnumValue<DownedStyle> brDownedStyle;
    public final ForgeConfigSpec.DoubleValue brBleedPerSecond;
    public final ForgeConfigSpec.DoubleValue brDownedHealth;
    public final ForgeConfigSpec.DoubleValue brDownedSpeedPenalty;
    public final ForgeConfigSpec.IntValue brReviveSeconds;
    public final ForgeConfigSpec.DoubleValue brReviveRange;
    public final ForgeConfigSpec.DoubleValue brReviveHealth;
    public final ForgeConfigSpec.DoubleValue brMaxHealth;
    public final ForgeConfigSpec.BooleanValue brNaturalRegen;

    // BR 復活牌 / 祭壇
    public final ForgeConfigSpec.IntValue brCardGroundSeconds;
    public final ForgeConfigSpec.IntValue brAltarCooldownSeconds;
    public final ForgeConfigSpec.IntValue brAltarActivateSeconds;
    public final ForgeConfigSpec.DoubleValue brAltarRange;
    public final ForgeConfigSpec.IntValue brAltarRespawnHeight;

    // ── 等級 / XP ─────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue xpWin;
    public final ForgeConfigSpec.IntValue xpKill;
    public final ForgeConfigSpec.IntValue xpAssist;
    public final ForgeConfigSpec.DoubleValue xpCompetitiveMultiplier;
    public final ForgeConfigSpec.IntValue levelBaseXp;
    public final ForgeConfigSpec.IntValue levelStepXp;

    // ── TDM ──────────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue tdmKillsToWin;
    public final ForgeConfigSpec.IntValue tdmTimeLimitSeconds;

    // ── DOM ──────────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue domScoreToWin;
    public final ForgeConfigSpec.IntValue domCaptureTimeSeconds;

    // ── GK ──────────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue gkRoundTimeSeconds;

    // ── 彈藥分類（所有模式通用） ─────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue ammoEnabled;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> ammoLight, ammoMedium, ammoHeavy, ammoShotgun, ammoHeavyWeapon;

    // ── 中彈效果（所有模式通用） ─────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue bulletKnockback;
    public final ForgeConfigSpec.BooleanValue bulletHurtShake;

    // ── 趴下（所有模式通用） ─────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue proneEnabled;
    public final ForgeConfigSpec.DoubleValue proneCooldownSeconds;

    // ── 移動方式與腳步聲（所有模式通用） ─────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue autoSprint;
    public final ForgeConfigSpec.DoubleValue spotRange, spotRangeCrouch, spotRangeProne, spotAngle, spotCloseRange, spotCloseAngle;
    public final ForgeConfigSpec.IntValue spotDelayTicks;
    public final ForgeConfigSpec.BooleanValue preciseHitbox;
    public final ForgeConfigSpec.DoubleValue hitboxTolerance;
    public final ForgeConfigSpec.DoubleValue quietWalkSpeed, stepVolumeSprint, stepVolumeWalk, stepVolumeQuiet, stepMaterialContrast;

    // ── 防彈（所有模式通用） ───────────────────────────────────────────────────
    public final ForgeConfigSpec.DoubleValue armorDurabilityMultiplier;
    /** 特殊物品的設定（[special] 區段）。 */
    public final com.provinggrounds.special.SpecialConfig special;
    public final ForgeConfigSpec.BooleanValue bulletproofEnabled;
    public final ForgeConfigSpec.BooleanValue bulletproofHeadBodySplit;
    public final ForgeConfigSpec.DoubleValue bulletproofLeather;
    public final ForgeConfigSpec.DoubleValue bulletproofChainmail;
    public final ForgeConfigSpec.DoubleValue bulletproofIron;
    public final ForgeConfigSpec.DoubleValue bulletproofGold;
    public final ForgeConfigSpec.DoubleValue bulletproofDiamond;
    public final ForgeConfigSpec.DoubleValue bulletproofNetherite;
    public final ForgeConfigSpec.DoubleValue bulletproofTurtle;

    // ── 槍械平衡（所有模式通用，GunBalance） ─────────────────────────────────
    public final ForgeConfigSpec.BooleanValue gunBalanceEnabled;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> venueList;
    public final ForgeConfigSpec.DoubleValue venueMaxHealth;
    public final ForgeConfigSpec.ConfigValue<String> venueAmmoBox;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> weaponModeThrowables;
    public final ForgeConfigSpec.DoubleValue autoTtk, pistolTtk, shotgunShotDamage, sniperHeadMultiplier;
    public final ForgeConfigSpec.IntValue sniperBodyShots, sniperBoltRpm;
    public final ForgeConfigSpec.DoubleValue rifleFalloffStart, rifleFalloffEnd, rifleFalloffMin;
    public final ForgeConfigSpec.DoubleValue smgFalloffStart, smgFalloffEnd, smgFalloffMin;
    public final ForgeConfigSpec.DoubleValue shotgunFalloffStart, shotgunFalloffEnd, shotgunFalloffMin;
    public final ForgeConfigSpec.BooleanValue bulletIgnoresVanillaArmor;

    private PGConfig(ForgeConfigSpec.Builder builder) {
        builder.push("lobby");
        uiWalkAllowed = builder.comment("是否允許玩家開著介面（物品欄、箱子、地圖…）時用移動鍵走動（預設允許）。設成 false 是緊急關閉：所有玩家都不能（不管各自的客戶端設定）。",
                "（原本的鍵 ui_walk_allowed 預設關閉、已停用；改名是為了讓舊設定檔自動換成新的預設值）")
            .define("ui_walk_allowed_v2", true);
        lobbyCountdownSeconds = builder.comment("登機倒數秒數（BR 在這段時間分批刷新戰利品；最後 10 秒畫面中央顯示數字＋音效）")
            .defineInRange("countdown_seconds", 10, 5, 300);
        forceStartSeconds = builder.comment("管理員按「強制開始」後，全體提示並倒數的秒數")
            .defineInRange("force_start_seconds", 10, 1, 60);
        endSoundVolume = builder.comment("遊戲結束音效的音量（1.0 = 原本音量，0.5 = 一半）")
            .defineInRange("end_sound_volume", 0.5, 0.0, 1.0);
        minPlayersToStart = builder.comment("最少開始人數（1 = 方便單人測試）")
            .defineInRange("min_players", 1, 1, 100);
        builder.pop();

        builder.push("battle_royale");

        builder.push("circle");
        brGameDurationSeconds = builder.comment("BR 最長局時（秒）。目前未使用，保留給之後的超時規則")
            .defineInRange("game_duration_seconds", 600, 60, 3600);
        brCircleWarnSeconds = builder.comment("縮圈開始前幾秒發出預警")
            .defineInRange("circle_warn_seconds", 30, 5, 120);
        // 縮圈：每種模式各自設定（2026-11-23：快速約 5 分、標準約 12 分、慢速約 20 分；鍵名 _v3，舊設定檔的數值不會蓋掉新預設）
        // 管理員也可以在遊戲裡用 /pg circle 指令查看、修改（會寫回這個設定檔）
        brFastRatios = builder.comment("快速方案：各階段半徑 ÷ 地圖半徑（最後一個是 0 = 最終圈縮到一個點）")
            .defineList("fast_ratios_v3", List.of(0.45, 0.18, 0.0), PGConfig::isRatio);
        brFastInterval = builder.comment("快速方案：每階段的等待時間與最短縮圈時間（秒）")
            .defineInRange("fast_interval_seconds_v3", 30, 5, 600);
        brFastSpeed = builder.comment("快速方案：圈移動速度上限 = BR 疾跑速度 × 此倍率（1.0 以下都追不上全速疾跑的玩家）")
            .defineInRange("fast_speed_factor_v3", 0.95, 0.1, 2.0);
        brFastFirstWait = builder.comment("快速方案：開場第一次縮圈前額外多等幾秒")
            .defineInRange("fast_first_wait_seconds_v3", 45, 0, 600);
        brStandardRatios = builder.comment("標準方案：各階段半徑 ÷ 地圖半徑")
            .defineList("standard_ratios_v3", List.of(0.60, 0.35, 0.15, 0.0), PGConfig::isRatio);
        brStandardInterval = builder.comment("標準方案：每階段的等待時間與最短縮圈時間（秒）")
            .defineInRange("standard_interval_seconds_v3", 75, 5, 600);
        brStandardSpeed = builder.comment("標準方案：圈移動速度上限 = BR 疾跑速度 × 此倍率")
            .defineInRange("standard_speed_factor_v3", 0.6, 0.1, 2.0);
        brStandardFirstWait = builder.comment("標準方案：開場第一次縮圈前額外多等幾秒")
            .defineInRange("standard_first_wait_seconds_v3", 90, 0, 600);
        brSlowRatios = builder.comment("慢速方案：各階段半徑 ÷ 地圖半徑")
            .defineList("slow_ratios_v3", List.of(0.70, 0.48, 0.28, 0.12, 0.0), PGConfig::isRatio);
        brSlowInterval = builder.comment("慢速方案：每階段的等待時間與最短縮圈時間（秒）")
            .defineInRange("slow_interval_seconds_v3", 112, 5, 600);
        brSlowSpeed = builder.comment("慢速方案：圈移動速度上限 = BR 疾跑速度 × 此倍率")
            .defineInRange("slow_speed_factor_v3", 0.5, 0.1, 2.0);
        brSlowFirstWait = builder.comment("慢速方案：開場第一次縮圈前額外多等幾秒")
            .defineInRange("slow_first_wait_seconds_v3", 90, 0, 600);
        brDamageBase = builder.comment("圈外傷害基礎值（HP/秒）。BR 最大血量 100：預設第 1~4 階段每秒 1、2.5、4、5.5")
            .defineInRange("damage_base", 1.0, 0.0, 100.0);
        brDamagePerStage = builder.comment("圈外傷害每階段增加量（HP/秒）。傷害 = 基礎 + 每階段 × 階段索引(0 起算)")
            .defineInRange("damage_per_stage", 1.5, 0.0, 100.0);
        builder.pop();

        builder.push("plane");
        brPlaneHeight = builder.comment("飛機飛行高度（Y）")
            .defineInRange("height", 220, 100, 310);
        brPlaneSpeed = builder.comment("飛機速度（格/秒）")
            .defineInRange("speed", 25.0, 5.0, 100.0);
        boardLockSeconds = builder.comment("登機後幾秒內不能跳傘（避免誤觸）")
            .defineInRange("board_lock_seconds", 5, 0, 60);
        jumpInvisibleSeconds = builder.comment("跳出飛機後維持隱形與無敵幾秒")
            .defineInRange("jump_invisible_seconds", 2, 0, 30);
        builder.pop();

        builder.comment("跳傘：空白鍵跳出飛機後用原版鞘翅滑翔；按空白鍵或離地夠近時開傘。開傘後按空白鍵自由落體、再按開傘，可以反覆切換（回不到鞘翅）。").push("skydive");
        skydiveAutoChuteHeight = builder.comment("離地面幾格時自動開傘")
            .defineInRange("auto_chute_height", 50.0, 5.0, 300.0);
        glidePitchUpMax = builder.comment("鞘翅滑翔時視角從「正下方」最多能往上抬幾度（75 = 最高只到水平線下 15 度，不能拉高；90 = 可抬到水平；180 = 原版可抬到正上方）")
            .defineInRange("glide_pitch_up_max", 75.0, 0.0, 180.0);
        chuteSink = builder.comment("開傘後的下降速度（格/秒）")
            .defineInRange("chute_sink", 5.0, 0.5, 50.0);
        chuteHorizontal = builder.comment("開傘後用移動鍵操控的最大水平速度（格/秒）")
            .defineInRange("chute_horizontal", 12.0, 0.0, 50.0);
        builder.pop();

        builder.comment("快捷欄規則：第 1、2 格大型槍，第 3 格手槍，第 4 格補品，第 5 格投擲物（只對 BR 進行中的參賽者生效）。").push("hotbar");
        hotbarRulesEnabled = builder.comment("是否啟用快捷欄規則")
            .define("hotbar_rules_enabled", true);
        wheelHoldSeconds = builder.comment("長按快捷欄 4、5 開啟補品／投擲物輪盤的門檻（秒），短按維持原版選格")
            .defineInRange("wheel_hold_seconds", 0.25, 0.05, 2.0);
        randomMelee = builder.comment("開局時沒有自己帶近戰武器的玩家，從這個清單隨機給一把（放第 3 格）。格式同 /give：物品ID{NBT}",
                "預設：lrtactical 的 3 把與 CS2 刀具包的 10 把")
            .defineListAllowEmpty("random_melee", java.util.List.of(
                "lrtactical:melee{MeleeWeaponId:\"lrtactical:dagger\"}", "lrtactical:melee{MeleeWeaponId:\"lrtactical:karambit\"}",
                "lrtactical:melee{MeleeWeaponId:\"lrtactical:baseball_bat\"}", "lrtactical:melee{MeleeWeaponId:\"cs2_wt:bayonet\"}",
                "lrtactical:melee{MeleeWeaponId:\"cs2_wt:butterfly\"}", "lrtactical:melee{MeleeWeaponId:\"cs2_wt:css\"}",
                "lrtactical:melee{MeleeWeaponId:\"cs2_wt:karambit\"}", "lrtactical:melee{MeleeWeaponId:\"cs2_wt:m9\"}",
                "lrtactical:melee{MeleeWeaponId:\"cs2_wt:push\"}", "lrtactical:melee{MeleeWeaponId:\"cs2_wt:skeleton\"}",
                "lrtactical:melee{MeleeWeaponId:\"cs2_wt:stiletto\"}", "lrtactical:melee{MeleeWeaponId:\"cs2_wt:tactical\"}",
                "lrtactical:melee{MeleeWeaponId:\"cs2_wt:talon\"}"), o -> o instanceof String);
        builder.pop();

        builder.comment("背包：一級開主背包最下列、二級加中間列、三級加最上列（沒有背包 = 全鎖，只對 BR 參賽者生效）。").push("backpack");
        backpackColumns = builder.comment("每一列從左邊開放幾格（9 = 整列）。預設 7：一級 7 格、二級 14 格、三級 21 格")
            .defineInRange("backpack_columns", 7, 1, 9);
        builder.pop();

        builder.comment("本場數據：MVP 分數 = 擊殺×kill ＋ 助攻×assist ＋ 傷害÷damage_per_point ＋ 扶起×revive ＋ 祭壇復活×altar ＋（獲勝 ＋ win_bonus）").push("result");
        mvpKill = builder.defineInRange("mvp_kill", 3.0, 0.0, 100.0);
        mvpAssist = builder.defineInRange("mvp_assist", 1.5, 0.0, 100.0);
        mvpDamagePerPoint = builder.comment("每多少傷害算 1 分").defineInRange("mvp_damage_per_point", 100.0, 1.0, 100000.0);
        mvpRevive = builder.defineInRange("mvp_revive", 2.0, 0.0, 100.0);
        mvpAltar = builder.defineInRange("mvp_altar", 3.0, 0.0, 100.0);
        mvpWinBonus = builder.defineInRange("mvp_win_bonus", 5.0, 0.0, 100.0);
        builder.pop();

        builder.comment("祭壇復活落地後發的物品（皮革全套之外）。格式同 /give：物品ID{NBT} 數量。槍會自動附一份它用的子彈。",
            "物品依快捷欄規則放置（手槍第 3 格、補品第 4 格）。日後換成自製物品時只改這裡。").push("respawn_kit");
        respawnKitItems = builder.defineListAllowEmpty("items",
            java.util.List.of("tacz:modern_kinetic_gun{GunId:\"tacz:p320\",GunFireMode:\"SEMI\"} 1", "lrtactical:consumable{ConsumableId:\"lrtactical:ai2\"} 2"),
            o -> o instanceof String);
        builder.pop();

        builder.push("movement");
        brSpeedMultiplier = builder.comment("BR 內移動速度倍率（走路與疾跑都套用；速度藥水照原版相乘）")
            .defineInRange("speed_multiplier", 0.8, 0.1, 2.0);
        brMeleeSpeedMultiplier = builder.comment("BR 內手上拿著近戰武器（lrtactical 近戰）時的移動速度倍率（1.0 = 原版速度）")
            .defineInRange("melee_speed_multiplier", 1.0, 0.1, 2.0);
        brJumpHeight = builder.comment("BR 內無藥水時的跳躍高度（格）。原版約 1.25；跳躍藥水依同比例縮小")
            .defineInRange("jump_height", 1.10, 0.5, 3.0);
        builder.pop();

        builder.push("loot");
        lootSpawnChance = builder.comment("每個點位生成箱子的機率（0.65 = 65% 的生成點會有箱子）")
            .defineInRange("spawn_chance", 0.65, 0.0, 1.0);
        lootWeightLow = builder.comment("生成時抽中木箱（低階）的權重（占位）")
            .defineInRange("weight_low", 60, 0, 1000);
        lootWeightMid = builder.comment("生成時抽中鐵箱（中階）的權重（占位）")
            .defineInRange("weight_mid", 30, 0, 1000);
        lootWeightHigh = builder.comment("生成時抽中金箱（高階）的權重（占位）")
            .defineInRange("weight_high", 10, 0, 1000);
        lootPointsPerTick = builder.comment("清理/刷新時每 tick 處理的點位數（避免卡頓）")
            .defineInRange("points_per_tick", 50, 1, 1000);
        builder.pop();

        builder.push("airdrop");
        brAirdropTwoChance = builder.comment("本局掉 2 個空投的機率（其餘掉 1 個）（占位 50%）")
            .defineInRange("two_chance", 0.5, 0.0, 1.0);
        brAirdropMinGapSeconds = builder.comment("兩個空投之間至少間隔幾秒（占位）")
            .defineInRange("min_gap_seconds", 30, 0, 600);
        brAirdropRadiusFactor = builder.comment("落點範圍：當前圈半徑的幾倍以內（占位 0.7）")
            .defineInRange("radius_factor", 0.7, 0.0, 1.0);
        brAirdropHeight = builder.comment("空投開始掉落的高度（Y）")
            .defineInRange("spawn_height", 260, 100, 318);
        brAirdropFallSpeed = builder.comment("空投下降速度（格/秒）")
            .defineInRange("fall_speed", 8.0, 1.0, 60.0);
        builder.pop();

        builder.push("health");
        brMaxHealth = builder.comment("BR 中玩家的最大血量（原版是 20）。開局、祭壇復活都會補滿到這個值；回大廳恢復原本血量。")
            .defineInRange("max_health", 100.0, 1.0, 1024.0);
        brNaturalRegen = builder.comment("BR 中是否自然回血。BR 不使用飢餓度（飽食度固定、飢餓條隱藏），回血只靠補血道具時設 false")
            .define("natural_regen", false);
        builder.pop();

        builder.push("downed");
        brDownedStyle = builder.comment("倒地姿勢：CRAWL = 趴下且緩速移動（預設）；SIT = 坐下且不能移動")
            .defineEnum("downed_style", DownedStyle.CRAWL);
        brBleedPerSecond = builder.comment("倒地後每秒流失的血量，扣到 0 就死亡（有人正在扶時暫停）。倒地 100 血、每秒 1 血 = 100 秒")
            .defineInRange("bleed_per_second", 1.0, 0.05, 100.0);
        brDownedHealth = builder.comment("倒地時的血量（超過最大血量時以最大血量為準）。會隨時間流血，敵人也可以直接打掉")
            .defineInRange("downed_health", 100.0, 1.0, 1024.0);
        brDownedSpeedPenalty = builder.comment("倒地時移動速度減少的比例（0.8 = 減少 80%）（占位）")
            .defineInRange("speed_penalty", 0.8, 0.0, 1.0);
        brReviveSeconds = builder.comment("隊友蹲下扶起需要的秒數")
            .defineInRange("revive_seconds", 10, 1, 60);
        brReviveRange = builder.comment("扶起距離（格）")
            .defineInRange("revive_range", 2.0, 1.0, 6.0);
        brReviveHealth = builder.comment("被隊友扶起後的血量（超過最大血量時以最大血量為準）")
            .defineInRange("revive_health", 30.0, 1.0, 1024.0);
        builder.pop();

        builder.push("revive");
        brCardGroundSeconds = builder.comment("死者屍體旁掉落的復活牌可以被撿的秒數（revive_card_ground_seconds）")
            .defineInRange("revive_card_ground_seconds", 300, 10, 3600);
        brAltarCooldownSeconds = builder.comment("祭壇使用後的冷卻秒數（占位，沿用舊版 90 秒）")
            .defineInRange("altar_cooldown_seconds", 90, 0, 3600);
        brAltarActivateSeconds = builder.comment("啟動祭壇需要蹲下或按住右鍵的秒數")
            .defineInRange("altar_activate_seconds", 10, 1, 60);
        brAltarRange = builder.comment("啟動祭壇的距離（格）")
            .defineInRange("altar_range", 3.0, 1.0, 8.0);
        brAltarRespawnHeight = builder.comment("祭壇復活後重新跳傘的高度（Y）（占位）")
            .defineInRange("altar_respawn_height", 250, 100, 318);
        builder.pop();

        builder.pop(); // battle_royale

        builder.push("progression");
        xpWin = builder.comment("勝場 XP").defineInRange("xp_win", 50, 0, 10000);
        xpKill = builder.comment("擊殺 XP").defineInRange("xp_kill", 10, 0, 10000);
        xpAssist = builder.comment("助攻 XP").defineInRange("xp_assist", 5, 0, 10000);
        xpCompetitiveMultiplier = builder.comment("競技模式 XP 倍率（四捨五入為整數）")
            .defineInRange("xp_competitive_multiplier", 1.35, 1.0, 10.0);
        levelBaseXp = builder.comment("升到 Lv2 所需累計 XP")
            .defineInRange("level_base_xp", 100, 1, 1000000);
        levelStepXp = builder.comment("之後每級多需要的累計 XP（Lv N 門檻 = base + step × (N−2)）")
            .defineInRange("level_step_xp", 50, 1, 1000000);
        builder.pop();

        builder.push("tdm");
        tdmKillsToWin = builder.comment("TDM 勝利所需擊殺數").defineInRange("kills_to_win", 30, 5, 200);
        tdmTimeLimitSeconds = builder.comment("TDM 時間限制（秒）").defineInRange("time_limit_seconds", 300, 60, 1800);
        builder.pop();

        builder.push("dom");
        domScoreToWin = builder.comment("DOM 勝利分數").defineInRange("score_to_win", 200, 50, 1000);
        domCaptureTimeSeconds = builder.comment("佔點所需秒數").defineInRange("capture_time_seconds", 10, 3, 60);
        builder.pop();

        builder.push("gk");
        gkRoundTimeSeconds = builder.comment("GK 每回合時間（秒）").defineInRange("round_time_seconds", 120, 30, 600);
        builder.pop();

        builder.comment("防彈：被 TACZ 子彈打中時，依身上原版防具額外減傷。",
                "數值是「穿全套」的減傷比例。只減子彈的一般部分，穿甲部分（依每把槍的穿甲率）不減。",
                "爆頭只看頭盔；身體中彈看胸甲、護腿、靴子（每件 1/3）。",
                "原版護甲值本來就會再擋一次一般部分（TACZ 原本的行為）。")
            .push("bulletproof");
        // 2026-11-23：槍械平衡後子彈不再吃原版護甲值，防彈係數重新設定（鍵名 _v3，舊設定檔的數值不會蓋掉新預設）
        bulletIgnoresVanillaArmor = builder.comment("子彈傷害不吃原版護甲值（只算下面的防彈係數），穿什麼甲慢多少對每把槍都一致。改了要重開伺服器").define("bullet_ignores_vanilla_armor", true);
        bulletproofEnabled = builder.comment("是否啟用防彈").define("enabled", true);
        armorDurabilityMultiplier = builder.comment("所有防具的最大耐久度倍率（2.0 = 原版兩倍，例如鐵頭盔 165 → 330）。登入時同步到客戶端")
            .defineInRange("armor_durability_multiplier", 2.0, 0.1, 10.0);
        bulletproofHeadBodySplit = builder.comment("爆頭只看頭盔、身體只看其他三件；false = 不分部位，四件各占 1/4")
            .define("head_body_split", true);
        bulletproofLeather = builder.comment("皮革全套減傷").defineInRange("leather_v3", 0.12, 0.0, 0.95);
        bulletproofChainmail = builder.comment("鎖鏈全套減傷（占位）").defineInRange("chainmail_v3", 0.18, 0.0, 0.95);
        bulletproofIron = builder.comment("鐵全套減傷").defineInRange("iron_v3", 0.25, 0.0, 0.95);
        bulletproofGold = builder.comment("金全套減傷（占位）").defineInRange("gold_v3", 0.18, 0.0, 0.95);
        bulletproofDiamond = builder.comment("鑽石全套減傷").defineInRange("diamond_v3", 0.40, 0.0, 0.95);
        bulletproofNetherite = builder.comment("獄髓全套減傷").defineInRange("netherite_v3", 0.50, 0.0, 0.95);
        bulletproofTurtle = builder.comment("海龜殼（只有頭盔）全套減傷（占位）").defineInRange("turtle_v3", 0.12, 0.0, 0.95);
        builder.pop();

        builder.comment("槍械平衡：不改槍械包，依每把槍的射速重新決定每發傷害，讓同類型的槍打死人的時間差不多。",
                "目標 TTK（秒）= 沒穿甲、近距離、全部打身體；穿全套鐵甲約再慢 25%（例：2.0 → 2.5 秒）。").push("gun_balance");
        gunBalanceEnabled = builder.comment("是否啟用槍械平衡（false = 照槍械包原本的數值）").define("enabled", true);
        weaponModeThrowables = builder.comment("槍械模式「投擲物」：槍換成這些投擲物（格式同 /give）")
            .defineListAllowEmpty(List.of("weapon_mode_throwables"), () -> List.of(
                "lrtactical:throwable{ThrowableId:\"lrtactical:m67\"}", "lrtactical:throwable{ThrowableId:\"lrtactical:rgn\"}",
                "lrtactical:throwable{ThrowableId:\"lrtactical:molotov\"}", "lrtactical:throwable{ThrowableId:\"lrtactical:flash_grenade\"}",
                "lrtactical:throwable{ThrowableId:\"lrtactical:smoke_grenade\"}", "lrtactical:throwable{ThrowableId:\"suffuse:m24\"}",
                "lrtactical:throwable{ThrowableId:\"suffuse:m24_cluster\"}"), o -> o instanceof String);
        autoTtk = builder.comment("步槍、衝鋒槍、機槍、半自動狙擊的目標 TTK（秒）").defineInRange("auto_ttk", 2.0, 0.2, 20.0);
        pistolTtk = builder.comment("手槍的目標 TTK（秒）").defineInRange("pistol_ttk", 2.4, 0.2, 20.0);
        shotgunShotDamage = builder.comment("霰彈槍每一發（所有彈丸加起來、近距離）的傷害；50 = 近距離 2 發").defineInRange("shotgun_shot_damage", 50.0, 1.0, 1000.0);
        sniperBoltRpm = builder.comment("射速不超過這個值的狙擊槍算栓動狙擊（其餘照步槍算）").defineInRange("sniper_bolt_rpm", 200, 1, 2000);
        sniperBodyShots = builder.comment("栓動狙擊打身體幾發死（沒穿甲）").defineInRange("sniper_body_shots", 2, 1, 20);
        sniperHeadMultiplier = builder.comment("栓動狙擊的爆頭倍率至少多少（2.0 = 沒穿甲一發爆頭）").defineInRange("sniper_head_multiplier", 2.0, 1.0, 10.0);
        rifleFalloffStart = builder.comment("步槍、機槍：幾格內全額傷害").defineInRange("rifle_falloff_start", 30.0, 0.0, 500.0);
        rifleFalloffEnd = builder.comment("步槍、機槍：到幾格時降到最低").defineInRange("rifle_falloff_end", 60.0, 0.0, 500.0);
        rifleFalloffMin = builder.comment("步槍、機槍：最遠的傷害比例").defineInRange("rifle_falloff_min", 0.7, 0.0, 1.0);
        smgFalloffStart = builder.comment("衝鋒槍、手槍：幾格內全額傷害").defineInRange("smg_falloff_start", 20.0, 0.0, 500.0);
        smgFalloffEnd = builder.comment("衝鋒槍、手槍：到幾格時降到最低").defineInRange("smg_falloff_end", 40.0, 0.0, 500.0);
        smgFalloffMin = builder.comment("衝鋒槍、手槍：最遠的傷害比例").defineInRange("smg_falloff_min", 0.6, 0.0, 1.0);
        shotgunFalloffStart = builder.comment("霰彈槍：幾格內全額傷害").defineInRange("shotgun_falloff_start", 5.0, 0.0, 500.0);
        shotgunFalloffEnd = builder.comment("霰彈槍：到幾格時降到最低").defineInRange("shotgun_falloff_end", 15.0, 0.0, 500.0);
        shotgunFalloffMin = builder.comment("霰彈槍：最遠的傷害比例").defineInRange("shotgun_falloff_min", 0.3, 0.0, 1.0);
        builder.pop();

        builder.comment("場地選擇（M 選單 →「場地選擇」）。一行一個場地：ID|名稱|旗標|x y z; x y z; …（多個座標隨機選一個）",
                "旗標：safe 安全區（玩家打不到彼此）、pvp 可以互打、ammo 給無限彈藥箱、clear 清槍與配件、clear_all 清所有戰鬥物品、respawn 死掉在場內重生、hidden 不顯示在選單",
                "預設值是舊版資料包的座標（大廳、訓練場、迷你死鬥 13 個出生點、跑庫塔）").push("venues");
        venueList = builder.defineListAllowEmpty(List.of("list"), () -> List.of(
                "lobby|大廳|safe,clear_all|-3.51 32 -0.17",
                "training|訓練場|safe,ammo|61.49 14 18.63",
                "minidm|迷你死鬥|pvp,ammo,respawn|123.36 14 15.68; 117.42 16 0.59; 123.63 16 0.61; 147.39 14 9.37; 158.35 14 0.67; 166.58 14 5.55; "
                    + "170.63 14 14.61; 128.32 14 24.36; 151.47 14 29.44; 175.62 14 23.38; 140.59 14 38.43; 132.37 14 33.41; 181.62 16 38.38",
                "parkour|跑庫塔|clear|-27.43 -62 416.55"), o -> o instanceof String s && s.contains("|"));
        venueMaxHealth = builder.comment("在場地裡的最大血量（舊版是 100）").defineInRange("max_health", 100.0, 1.0, 1024.0);
        venueAmmoBox = builder.comment("旗標 ammo 給的無限彈藥箱（格式同 /give）").define("ammo_box", "tacz:ammo_box{AllTypeCreative:1b}");
        builder.pop();

        builder.comment("管理員工具物品（浮空文字、箱子生成點）").push("admin_tools");
        adminTextDeleteRange = builder.comment("清除浮空文字：找最近一個的範圍（格）").defineInRange("text_delete_range", 16.0, 1.0, 256.0);
        adminTextUndoMax = builder.comment("復原刪除文字：最多保留幾筆刪除紀錄").defineInRange("text_undo_max", 10, 1, 100);
        adminSpawnViewRange = builder.comment("顯示箱子生成點：只畫玩家周圍幾格內的點").defineInRange("spawn_view_range", 64.0, 8.0, 512.0);
        builder.pop();

        builder.comment("中彈效果：被 TACZ 子彈打中時的擊退與畫面抖動。").push("combat");
        bulletKnockback = builder.comment("被子彈打中會不會被擊退（false = 完全不擊退）")
            .define("bullet_knockback", false);
        bulletHurtShake = builder.comment("被子彈打中畫面會不會晃動（false = 不晃；玩家端在登入後才會讀到這個設定）")
            .define("bullet_hurt_shake", false);
        builder.pop();

        builder.comment("趴下：玩家按鍵（預設 C）趴下／站起來。").push("prone");
        proneEnabled = builder.comment("是否允許玩家自己趴下").define("enabled", true);
        proneCooldownSeconds = builder.comment("趴下後多久才能站起來、站起來後多久才能再趴下（秒）")
            .defineInRange("cooldown_seconds", 1.0, 0.0, 10.0);
        builder.pop();

        builder.comment("移動方式：按方向鍵自動奔跑；按「靜走」鍵（預設 Shift）慢慢走、腳步聲小；蹲下（預設 Ctrl）。按鍵都可以在按鍵設定改。",
            "腳步聲：奔跑比原版大聲（也傳得比較遠），靜走比較小聲；不同材質的地面聲音差異加大，比較好分辨。").push("movement");
        autoSprint = builder.comment("按方向鍵（往前）時自動奔跑").define("auto_sprint", true);
        preciseHitbox = builder.comment("精準命中：子彈要打到人物模型（頭、身體、手、腳）才算數，不再是比模型大的方柱").define("precise_hitbox", true);
        hitboxTolerance = builder.comment("精準命中：每個部位往外放寬幾格（護甲厚度、網路延遲；0.06 約 1 像素）").defineInRange("hitbox_tolerance", 0.06, 0.0, 0.5);
        quietWalkSpeed = builder.comment("靜走速度：一般走路速度的幾倍").defineInRange("quiet_walk_speed", 0.6, 0.1, 1.0);
        stepVolumeSprint = builder.comment("奔跑腳步聲音量倍率（大於 1 也會傳得比較遠；原版 = 1）").defineInRange("step_volume_sprint", 2.0, 0.0, 8.0);
        stepVolumeWalk = builder.comment("一般走路腳步聲音量倍率").defineInRange("step_volume_walk", 1.0, 0.0, 8.0);
        stepVolumeQuiet = builder.comment("靜走腳步聲音量倍率").defineInRange("step_volume_quiet", 0.3, 0.0, 8.0);
        stepMaterialContrast = builder.comment("不同材質地面的聲音差異（0 = 原版，1 = 預設加強，越大差越多）").defineInRange("step_material_contrast", 1.0, 0.0, 3.0);
        builder.pop();

        builder.comment("小地圖發現敵人：要「真的看到」才會標在小地圖上（BR）").push("spotting");
        spotRange = builder.comment("幾格內看得到站著的敵人").defineInRange("spot_range", 64.0, 1.0, 512.0);
        spotRangeCrouch = builder.comment("幾格內看得到蹲著的敵人").defineInRange("spot_range_crouch", 40.0, 1.0, 512.0);
        spotRangeProne = builder.comment("幾格內看得到趴下或倒地的敵人").defineInRange("spot_range_prone", 24.0, 1.0, 512.0);
        spotAngle = builder.comment("敵人要在準星幾度以內").defineInRange("spot_angle", 25.0, 1.0, 90.0);
        spotCloseRange = builder.comment("近距離（幾格內）角度放寬").defineInRange("spot_close_range", 16.0, 0.0, 512.0);
        spotCloseAngle = builder.comment("近距離時敵人要在準星幾度以內").defineInRange("spot_close_angle", 45.0, 1.0, 90.0);
        spotDelayTicks = builder.comment("要連續看到幾 tick 才標記（20 tick = 1 秒；檢查每 5 tick 一次）").defineInRange("spot_delay_ticks", 10, 0, 200);
        builder.pop();

        builder.comment("彈藥分類：把各槍械包的子彈歸成 5 大類，槍改吃對應的大類彈藥。",
                "大類彈藥由槍械包 provinggrounds_ammo 提供（伺服器與玩家的 tacz 資料夾都要放）。",
                "清單裡是原本的子彈 ID（命名空間:名稱）；不在任何清單裡的子彈維持原樣。",
                "修改後要重新進入伺服器才會在玩家端生效。")
            .push("ammo");
        ammoEnabled = builder.comment("是否啟用彈藥分類").define("enabled", true);
        ammoLight = ammoList(builder, "light", "輕型彈藥（手槍、衝鋒槍）",
            "tacz:9mm", "tacz:45acp", "tacz:57x28", "tacz:762x25", "tacz:46x30", "tacz:22wmr", "suffuse:.22wmr",
            "suffuse:7.65x20mm", "suffuse:6x35mm", "tacz:357mag", "suffuse:44mag", "tacz:50ae", "tacz:500mag");
        ammoMedium = ammoList(builder, "medium", "中型彈藥（突擊步槍、輕機槍）",
            "tacz:556x45", "tacz:545x39", "suffuse:545x39", "tacz:762x39", "tacz:58x42", "suffuse:6.8tvcm", "tacz:68x51fury");
        ammoHeavy = ammoList(builder, "heavy", "重型彈藥（戰鬥步槍、精確射手步槍、狙擊槍）",
            "tacz:308", "tacz:30_06", "tacz:338", "suffuse:338nm", "suffuse:.408ct", "tacz:50bmg", "tacz:762x54",
            "tacz:792x57", "tacz:45_70", "suffuse:14.5x114mm", "suffuse:12.7x55");
        ammoShotgun = ammoList(builder, "shotgun", "霰彈",
            "tacz:12g", "suffuse:23mm", "suffuse:boomstickshot");
        ammoHeavyWeapon = ammoList(builder, "heavy_weapon", "重火力彈藥（榴彈、火箭）",
            "tacz:40mm", "suffuse:35x32mm", "tacz:rpg_rocket", "suffuse:120mm");
        builder.pop();

        special = new com.provinggrounds.special.SpecialConfig(builder); // 特殊物品（計畫書 4）
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends String>> ammoList(
            ForgeConfigSpec.Builder builder, String key, String comment, String... defaults) {
        return builder.comment(comment + " → provinggrounds:" + key)
            .defineListAllowEmpty(List.of(key), () -> List.of(defaults), o -> o instanceof String s && s.contains(":"));
    }

    private static boolean isRatio(Object o) {
        return o instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() < 1; // 0 = 縮到一個點（最終圈）
    }

    /** BR 內疾跑速度（格/秒）。 */
    public double brSprintSpeed() {
        return VANILLA_SPRINT_SPEED * brSpeedMultiplier.get();
    }
}
