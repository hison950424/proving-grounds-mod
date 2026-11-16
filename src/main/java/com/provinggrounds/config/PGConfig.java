package com.provinggrounds.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 伺服器端設定。可透過 /pg reload 熱重載，不需重啟伺服器。
 */
public class PGConfig {

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final PGConfig SERVER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SERVER = new PGConfig(builder);
        SERVER_SPEC = builder.build();
    }

    // ── 大廳 ──────────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue lobbyCountdownSeconds;
    public final ForgeConfigSpec.IntValue minPlayersToStart;

    // ── BR ──────────────────────────────────────────────────────────────────
    public final ForgeConfigSpec.IntValue brGameDurationSeconds;
    public final ForgeConfigSpec.DoubleValue brCircleShrinkRate;   // blocks/sec
    public final ForgeConfigSpec.IntValue brCircleWarnSeconds;     // 縮圈前警告秒數

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
        lobbyCountdownSeconds = builder.comment("開始倒計時秒數").defineInRange("countdown_seconds", 30, 5, 300);
        minPlayersToStart     = builder.comment("最少開始人數").defineInRange("min_players", 2, 1, 100);
        builder.pop();

        builder.push("battle_royale");
        brGameDurationSeconds = builder.comment("BR 最長局時（秒）").defineInRange("game_duration_seconds", 600, 60, 3600);
        brCircleShrinkRate    = builder.comment("縮圈速度（格/秒）").defineInRange("circle_shrink_rate", 0.5, 0.1, 10.0);
        brCircleWarnSeconds   = builder.comment("縮圈前警告秒數").defineInRange("circle_warn_seconds", 30, 5, 120);
        builder.pop();

        builder.push("tdm");
        tdmKillsToWin      = builder.comment("TDM 勝利所需擊殺數").defineInRange("kills_to_win", 30, 5, 200);
        tdmTimeLimitSeconds = builder.comment("TDM 時間限制（秒）").defineInRange("time_limit_seconds", 300, 60, 1800);
        builder.pop();

        builder.push("dom");
        domScoreToWin       = builder.comment("DOM 勝利分數").defineInRange("score_to_win", 200, 50, 1000);
        domCaptureTimeSeconds = builder.comment("佔點所需秒數").defineInRange("capture_time_seconds", 10, 3, 60);
        builder.pop();

        builder.push("gk");
        gkRoundTimeSeconds = builder.comment("GK 每回合時間（秒）").defineInRange("round_time_seconds", 120, 30, 600);
        builder.pop();
    }
}
