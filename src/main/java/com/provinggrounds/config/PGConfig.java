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
    public final ForgeConfigSpec.DoubleValue brCircleMaxSpeedFactor;
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
    // 跳傘（計畫書 B1）
    public final ForgeConfigSpec.DoubleValue skydivePitchMin, skydivePitchMax, freefallMaxSink, freefallMinSink, freefallMaxHorizontal;
    public final ForgeConfigSpec.DoubleValue chuteSink, chuteHorizontal, skydiveToggleCooldown;
    public final ForgeConfigSpec.BooleanValue hotbarRulesEnabled;
    public final ForgeConfigSpec.DoubleValue wheelHoldSeconds;
    public final ForgeConfigSpec.IntValue backpackLevel1Slots, backpackLevel2Slots, backpackLevel3Slots;
    public final ForgeConfigSpec.DoubleValue mvpKill, mvpAssist, mvpDamagePerPoint, mvpRevive, mvpAltar, mvpWinBonus;
    public final ForgeConfigSpec.DoubleValue adminTextDeleteRange, adminSpawnViewRange;
    public final ForgeConfigSpec.IntValue adminTextUndoMax;
    public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> respawnKitItems;

    // ── BR：移動 ──────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.DoubleValue brSpeedMultiplier;
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

    // ── 防彈（所有模式通用） ───────────────────────────────────────────────────
    public final ForgeConfigSpec.DoubleValue armorDurabilityMultiplier;
    public final ForgeConfigSpec.BooleanValue bulletproofEnabled;
    public final ForgeConfigSpec.BooleanValue bulletproofHeadBodySplit;
    public final ForgeConfigSpec.DoubleValue bulletproofLeather;
    public final ForgeConfigSpec.DoubleValue bulletproofChainmail;
    public final ForgeConfigSpec.DoubleValue bulletproofIron;
    public final ForgeConfigSpec.DoubleValue bulletproofGold;
    public final ForgeConfigSpec.DoubleValue bulletproofDiamond;
    public final ForgeConfigSpec.DoubleValue bulletproofNetherite;
    public final ForgeConfigSpec.DoubleValue bulletproofTurtle;

    private PGConfig(ForgeConfigSpec.Builder builder) {
        builder.push("lobby");
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
        brCircleMaxSpeedFactor = builder.comment(
                "縮圈速度上限 = BR 疾跑速度 × 此倍率（預設 0.8 = 疾跑的 80%，約 3.4 格/秒）。1.0 以下圈都追不上全速疾跑的玩家",
                "（BR 疾跑速度 = 原版 5.612 格/秒 × speed_multiplier）")
            .defineInRange("max_speed_factor", 0.8, 0.1, 1.0);
        brStandardRatios = builder.comment("標準方案：各階段半徑 ÷ 地圖半徑（沿用舊 Datapack 800→460→240→100→10）")
            .defineList("standard_ratios", List.of(0.58, 0.30, 0.125, 0.01), PGConfig::isRatio);
        brStandardInterval = builder.comment("標準方案：階段間隔（秒）")
            .defineInRange("standard_interval_seconds", 60, 5, 600);
        brFastRatios = builder.comment("快速方案：各階段半徑比例（占位，未定案）")
            .defineList("fast_ratios", List.of(0.45, 0.15, 0.01), PGConfig::isRatio);
        brFastInterval = builder.comment("快速方案：階段間隔（秒）")
            .defineInRange("fast_interval_seconds", 30, 5, 600);
        brSlowRatios = builder.comment("慢速方案：各階段半徑比例（占位，未定案）")
            .defineList("slow_ratios", List.of(0.70, 0.45, 0.25, 0.10, 0.01), PGConfig::isRatio);
        brSlowInterval = builder.comment("慢速方案：階段間隔（秒）")
            .defineInRange("slow_interval_seconds", 90, 5, 600);
        brDamageBase = builder.comment("圈外傷害基礎值（HP/秒）。BR 最大血量 100：預設第 1~4 階段每秒 2、5、8、11")
            .defineInRange("damage_base", 2.0, 0.0, 100.0);
        brDamagePerStage = builder.comment("圈外傷害每階段增加量（HP/秒）。傷害 = 基礎 + 每階段 × 階段索引(0 起算)")
            .defineInRange("damage_per_stage", 3.0, 0.0, 100.0);
        builder.pop();

        builder.push("plane");
        brPlaneHeight = builder.comment("飛機飛行高度（Y）")
            .defineInRange("height", 220, 100, 310);
        brPlaneSpeed = builder.comment("飛機速度（格/秒）")
            .defineInRange("speed", 25.0, 5.0, 100.0);
        builder.pop();

        builder.comment("跳傘：空白鍵跳出飛機、開傘、收傘。自由落體的速度依俯角（往下看的角度）線性變化。").push("skydive");
        skydivePitchMin = builder.comment("自由落體時視角最多能抬到的俯角（度，0 = 水平，不能抬到水平以上）")
            .defineInRange("skydive_pitch_min", 0.0, 0.0, 90.0);
        skydivePitchMax = builder.comment("自由落體時視角最低的俯角（度，90 = 正下方）")
            .defineInRange("skydive_pitch_max", 90.0, 0.0, 90.0);
        freefallMaxSink = builder.comment("自由落體最大下沉速度（格/秒，頭朝正下方時）")
            .defineInRange("freefall_max_sink", 30.0, 1.0, 100.0);
        freefallMinSink = builder.comment("自由落體最低下沉速度（格/秒，抬到水平時仍保證下沉，不能滑翔）")
            .defineInRange("freefall_min_sink", 10.0, 1.0, 100.0);
        freefallMaxHorizontal = builder.comment("自由落體最大水平速度（格/秒，抬到水平時）")
            .defineInRange("freefall_max_horizontal", 15.0, 0.0, 100.0);
        chuteSink = builder.comment("開傘後的下降速度（格/秒）")
            .defineInRange("chute_sink", 5.0, 0.5, 50.0);
        chuteHorizontal = builder.comment("開傘後用移動鍵操控的最大水平速度（格/秒）")
            .defineInRange("chute_horizontal", 8.0, 0.0, 50.0);
        skydiveToggleCooldown = builder.comment("開傘、收傘切換的冷卻（秒）")
            .defineInRange("toggle_cooldown", 0.5, 0.0, 5.0);
        builder.pop();

        builder.comment("快捷欄規則：第 1、2 格大型槍，第 3 格手槍，第 4 格補品，第 5 格投擲物（只對 BR 進行中的參賽者生效）。").push("hotbar");
        hotbarRulesEnabled = builder.comment("是否啟用快捷欄規則")
            .define("hotbar_rules_enabled", true);
        wheelHoldSeconds = builder.comment("長按快捷欄 4、5 開啟補品／投擲物輪盤的門檻（秒），短按維持原版選格")
            .defineInRange("wheel_hold_seconds", 0.25, 0.05, 2.0);
        builder.pop();

        builder.comment("背包：裝在物品欄背包欄的背包等級決定主背包開放幾格（沒有背包 = 全鎖，只對 BR 參賽者生效）。").push("backpack");
        backpackLevel1Slots = builder.comment("一級背包開放的格數").defineInRange("backpack_level1_slots", 5, 0, 27);
        backpackLevel2Slots = builder.comment("二級背包開放的格數").defineInRange("backpack_level2_slots", 10, 0, 27);
        backpackLevel3Slots = builder.comment("三級背包開放的格數").defineInRange("backpack_level3_slots", 15, 0, 27);
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
            .defineInRange("speed_multiplier", 0.75, 0.1, 2.0);
        brJumpHeight = builder.comment("BR 內無藥水時的跳躍高度（格）。原版約 1.25；跳躍藥水依同比例縮小")
            .defineInRange("jump_height", 1.10, 0.5, 3.0);
        builder.pop();

        builder.push("loot");
        lootSpawnChance = builder.comment("每個點位生成箱子的機率（沿用舊系統 50%）")
            .defineInRange("spawn_chance", 0.5, 0.0, 1.0);
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
        bulletproofEnabled = builder.comment("是否啟用防彈").define("enabled", true);
        armorDurabilityMultiplier = builder.comment("所有防具的最大耐久度倍率（2.0 = 原版兩倍，例如鐵頭盔 165 → 330）。登入時同步到客戶端")
            .defineInRange("armor_durability_multiplier", 2.0, 0.1, 10.0);
        bulletproofHeadBodySplit = builder.comment("爆頭只看頭盔、身體只看其他三件；false = 不分部位，四件各占 1/4")
            .define("head_body_split", true);
        bulletproofLeather = builder.comment("皮革全套減傷").defineInRange("leather", 0.10, 0.0, 0.95);
        bulletproofChainmail = builder.comment("鎖鏈全套減傷（占位）").defineInRange("chainmail", 0.15, 0.0, 0.95);
        bulletproofIron = builder.comment("鐵全套減傷").defineInRange("iron", 0.20, 0.0, 0.95);
        bulletproofGold = builder.comment("金全套減傷（占位）").defineInRange("gold", 0.15, 0.0, 0.95);
        bulletproofDiamond = builder.comment("鑽石全套減傷").defineInRange("diamond", 0.30, 0.0, 0.95);
        bulletproofNetherite = builder.comment("獄髓全套減傷").defineInRange("netherite", 0.40, 0.0, 0.95);
        bulletproofTurtle = builder.comment("海龜殼（只有頭盔）全套減傷（占位）").defineInRange("turtle", 0.10, 0.0, 0.95);
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

        builder.comment("趴下：玩家按鍵（預設 Z）趴下／站起來。").push("prone");
        proneEnabled = builder.comment("是否允許玩家自己趴下").define("enabled", true);
        proneCooldownSeconds = builder.comment("趴下後多久才能站起來、站起來後多久才能再趴下（秒）")
            .defineInRange("cooldown_seconds", 1.0, 0.0, 10.0);
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
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends String>> ammoList(
            ForgeConfigSpec.Builder builder, String key, String comment, String... defaults) {
        return builder.comment(comment + " → provinggrounds:" + key)
            .defineListAllowEmpty(List.of(key), () -> List.of(defaults), o -> o instanceof String s && s.contains(":"));
    }

    private static boolean isRatio(Object o) {
        return o instanceof Number n && n.doubleValue() > 0 && n.doubleValue() < 1;
    }

    /** BR 內疾跑速度（格/秒）。 */
    public double brSprintSpeed() {
        return VANILLA_SPRINT_SPEED * brSpeedMultiplier.get();
    }
}
