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
    public final ForgeConfigSpec.IntValue brBleedSeconds;
    public final ForgeConfigSpec.DoubleValue brDownedHealth;
    public final ForgeConfigSpec.DoubleValue brDownedSpeedPenalty;
    public final ForgeConfigSpec.IntValue brReviveSeconds;
    public final ForgeConfigSpec.DoubleValue brReviveRange;
    public final ForgeConfigSpec.DoubleValue brReviveHealthRatio;

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

    private PGConfig(ForgeConfigSpec.Builder builder) {
        builder.push("lobby");
        lobbyCountdownSeconds = builder.comment("開始倒計時秒數（BR 在這段時間分批刷新戰利品）")
            .defineInRange("countdown_seconds", 30, 5, 300);
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
                "縮圈速度上限 = BR 疾跑速度 × 此倍率。1.0 = 圈永遠追不上全速疾跑的玩家",
                "（BR 疾跑速度 = 原版 5.612 格/秒 × speed_multiplier）")
            .defineInRange("max_speed_factor", 1.0, 0.1, 1.0);
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
        brDamageBase = builder.comment("圈外傷害基礎值（HP/秒，1 HP = 半顆心）")
            .defineInRange("damage_base", 0.5, 0.0, 100.0);
        brDamagePerStage = builder.comment("圈外傷害每階段增加量（HP/秒）。傷害 = 基礎 + 每階段 × 階段索引(0 起算)")
            .defineInRange("damage_per_stage", 1.5, 0.0, 100.0);
        builder.pop();

        builder.push("plane");
        brPlaneHeight = builder.comment("飛機飛行高度（Y）")
            .defineInRange("height", 220, 100, 310);
        brPlaneSpeed = builder.comment("飛機速度（格/秒）")
            .defineInRange("speed", 25.0, 5.0, 100.0);
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

        builder.push("downed");
        brDownedStyle = builder.comment("倒地姿勢：CRAWL = 趴下且緩速移動（預設）；SIT = 坐下且不能移動")
            .defineEnum("downed_style", DownedStyle.CRAWL);
        brBleedSeconds = builder.comment("倒地後幾秒沒被扶起就死亡（有人正在扶時暫停計時）")
            .defineInRange("bleed_seconds", 180, 5, 1800);
        brDownedHealth = builder.comment("倒地時的血量，敵人要再打掉這麼多才會死（占位）")
            .defineInRange("downed_health", 10.0, 1.0, 20.0);
        brDownedSpeedPenalty = builder.comment("倒地時移動速度減少的比例（0.8 = 減少 80%）（占位）")
            .defineInRange("speed_penalty", 0.8, 0.0, 1.0);
        brReviveSeconds = builder.comment("隊友蹲下扶起需要的秒數")
            .defineInRange("revive_seconds", 10, 1, 60);
        brReviveRange = builder.comment("扶起距離（格）")
            .defineInRange("revive_range", 2.0, 1.0, 6.0);
        brReviveHealthRatio = builder.comment("扶起後的血量比例（占位 50%）")
            .defineInRange("revive_health_ratio", 0.5, 0.05, 1.0);
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
    }

    private static boolean isRatio(Object o) {
        return o instanceof Number n && n.doubleValue() > 0 && n.doubleValue() < 1;
    }

    /** BR 內疾跑速度（格/秒）。 */
    public double brSprintSpeed() {
        return VANILLA_SPRINT_SPEED * brSpeedMultiplier.get();
    }
}
