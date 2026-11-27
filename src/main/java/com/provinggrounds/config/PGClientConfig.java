package com.provinggrounds.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 檔案: PGClientConfig.java
 * 目的: 客戶端設定（每位玩家自己的 config/provinggrounds-client.toml），不會從伺服器同步。
 *   enabled_v2：開著介面時仍可用移動鍵走動（計畫書 A11，預設開啟）。
 *     原本的鍵叫 ui_walk_enabled（v0.11.0～v0.14.0 預設關閉）；Forge 不會覆蓋已存在設定檔裡的值，
 *     所以改名成新鍵：舊設定檔裡的 false 直接被忽略，新鍵寫入預設 true（第三份計畫書 E）。
 *   ui_walk_excluded_screens：不接管移動鍵的畫面類別（完整類別名稱或簡短名稱），
 *     遇到第三方模組畫面衝突時可以自己加進來，不用改程式。
 */
public final class PGClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final PGClientConfig CLIENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new PGClientConfig(builder);
        SPEC = builder.build();
    }

    public final ForgeConfigSpec.BooleanValue uiWalkEnabled;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> uiWalkExcludedScreens;
    public final ForgeConfigSpec.DoubleValue circleWallOpacity, circleNextOpacity;
    public final ForgeConfigSpec.DoubleValue smokeDissipateVolume;
    public final ForgeConfigSpec.BooleanValue defaultKeysApplied;
    public final ForgeConfigSpec.BooleanValue directionalAudioApplied;
    public final ForgeConfigSpec.BooleanValue compassEnabled;
    public final ForgeConfigSpec.DoubleValue compassScale;
    public final ForgeConfigSpec.IntValue compassYOffset;
    public final ForgeConfigSpec.BooleanValue compatHighlightEnabled;
    public final ForgeConfigSpec.ConfigValue<String> compatHighlightColor;
    public final ForgeConfigSpec.DoubleValue compatHighlightOpacity;
    public final ForgeConfigSpec.BooleanValue damageIndicatorEnabled;
    public final ForgeConfigSpec.DoubleValue damageIndicatorSeconds;
    // 計畫書 5：準星、互動提示、戰鬥提示
    public final ForgeConfigSpec.BooleanValue crosshairDot, crosshairDotTaczApplied, crosshairDotTaczV2Applied, defaultKeysV2Applied, defaultKeysV3Applied;
    public final ForgeConfigSpec.BooleanValue interactHint, killIconEnabled, killFeedEnabled, killSoundEnabled, domBeacon, antiCampWall;
    public final ForgeConfigSpec.DoubleValue hudScale, killSoundVolume;
    public final ForgeConfigSpec.BooleanValue scoreboardToggle;
    public final ForgeConfigSpec.BooleanValue forceGuiScale;
    public final ForgeConfigSpec.BooleanValue imeLockEnabled;

    private PGClientConfig(ForgeConfigSpec.Builder builder) {
        builder.push("ui_walk");
        uiWalkEnabled = builder.comment("開著任何介面（物品欄、箱子、地圖、選單…）時仍可用前後左右移動鍵走動")
            .define("enabled_v2", true);
        uiWalkExcludedScreens = builder.comment(
                "不接管移動鍵的畫面類別名稱（可寫完整名稱或只寫類別名稱）。",
                "有輸入框正在輸入、按鍵設定、聊天、死亡、載入、斷線畫面本來就不會接管。")
            .defineListAllowEmpty(List.of("ui_walk_excluded_screens"), List::of, o -> o instanceof String);
        builder.pop();

        builder.comment("介面大小").push("gui");
        forceGuiScale = builder.comment("固定把原版「介面大小」設成 3（本模組的介面都以 3 為基準排版）。",
                "視窗太小放不下 3 時會用能放下的最大值。關掉後可以自己在 選項 → 顯示設定 改")
            .define("force_gui_scale_v1", true);
        imeLockEnabled = builder.comment("在 Windows 上，除了聊天與輸入框打字時，其餘時候強制鎖定英文輸入法（防止誤觸輸入法切換）")
            .define("ime_lock_enabled_v1", true);
        builder.pop();

        builder.comment("遊戲畫面中的縮圈牆（不影響小地圖、大地圖上畫的圈）").push("circle");
        circleWallOpacity = builder.comment("目前圈的牆（白色）不透明度，0.15 = 15%")
            .defineInRange("circle_wall_opacity", 0.15, 0.0, 1.0);
        circleNextOpacity = builder.comment("下一圈的環（淺藍色）不透明度，0.075 = 7.5%")
            .defineInRange("circle_next_opacity", 0.075, 0.0, 1.0);
        builder.pop();

        builder.comment("按鍵").push("keys");
        defaultKeysApplied = builder.comment("是否已經套用過 Proving Grounds 的預設按鍵（特殊物品 Q、丟棄 G、切換射擊模式 B、倍鏡倍率 V、趴下 C、物品欄 Tab）。改成 false 會在下次開遊戲時再套用一次")
            .define("default_keys_applied_v1", false);
        directionalAudioApplied = builder.comment("是否已經幫你打開過原版的「方向性音訊」（HRTF，戴耳機時聲音方向更準）。改成 false 會在下次開遊戲時再打開一次")
            .define("directional_audio_applied_v1", false);
        defaultKeysV3Applied = builder.comment("內部用：是否已套用第三批預設按鍵（標點 X、槍托近戰改 Y）").define("default_keys_applied_v3", false);
        defaultKeysV2Applied = builder.comment("內部用：是否已套用第二批預設按鍵（互動 F、槍托近戰改 X）").define("default_keys_applied_v2", false);

        builder.pop();

        builder.comment("音效").push("sound");
        smokeDissipateVolume = builder.comment("煙霧彈消散聲（lrtactical 煙霧彈結束時的聲音）的音量倍率，0.5 = 原本的一半")
            .defineInRange("smoke_dissipate_volume", 0.5, 0.0, 1.0);
        builder.pop();

        builder.comment("大逃殺指北針（畫面上方中央的方位條，只在對局中顯示）").push("compass");
        compassEnabled = builder.comment("是否顯示指北針").define("compass_enabled", true);
        compassScale = builder.comment("大小倍率（1.0 = 預設大小）").defineInRange("compass_scale", 1.0, 0.5, 3.0);
        compassYOffset = builder.comment("往下移幾格（GUI 像素；和其他畫面元件重疊時可以調）").defineInRange("compass_y_offset", 0, 0, 200);
        builder.pop();

        builder.comment("配件相容指示器：滑鼠停在槍上時標出能裝的配件，停在配件上時標出能裝它的槍").push("compat_highlight");
        compatHighlightEnabled = builder.comment("是否啟用").define("compat_highlight_enabled", true);
        compatHighlightColor = builder.comment("顏色（#RRGGBB，預設亮橘黃 #FFB300）").define("compat_highlight_color", "#FFB300");
        compatHighlightOpacity = builder.comment("不透明度（0.4 = 40%）").defineInRange("compat_highlight_opacity", 0.4, 0.05, 1.0);
        builder.pop();

        builder.comment("受擊方向指示器：受傷時在螢幕邊緣對應攻擊方向的位置顯示紅色方塊（上 = 前方、下 = 後方）").push("damage_indicator");
        damageIndicatorEnabled = builder.comment("是否啟用").define("damage_indicator_enabled", true);
        damageIndicatorSeconds = builder.comment("顯示幾秒後淡出").defineInRange("damage_indicator_seconds", 1.5, 0.3, 10.0);
        builder.pop();

        builder.comment("戰鬥提示（計畫書 5）：所有提示都放在畫面邊緣、小字、短時間淡出，不擋視野").push("hud");
        hudScale = builder.comment("提示文字大小倍率（互動提示、擊殺圖示、擊殺紀錄、戰績面板）").defineInRange("hud_scale", 1.0, 0.5, 2.0);
        crosshairDot = builder.comment("點狀準心（拿槍、空手都是點；關掉 = 原版準星）").define("crosshair_dot", true);
        crosshairDotTaczApplied = builder.comment("內部用：是否已把 TACZ 的槍械準星設成點狀（只做一次，之後在 TACZ 設定改的會保留）")
            .define("crosshair_dot_tacz_applied_v1", false);
        crosshairDotTaczV2Applied = builder.comment("內部用：是否已把 TACZ 的槍械準星改成 EMPTY、改由本模組畫點狀準心（只做一次）")
            .define("crosshair_dot_tacz_applied_v2", false);
        interactHint = builder.comment("準星下方顯示「F 開啟」「F 扶起」等互動提示（拿槍時一定顯示；這裡關掉只影響空手）").define("interact_hint", true);
        killIconEnabled = builder.comment("擊殺時準星附近的小圖示").define("kill_icon", true);
        killFeedEnabled = builder.comment("右上角擊殺紀錄（團隊死鬥／佔點／槍王之王）").define("kill_feed", true);
        killSoundEnabled = builder.comment("擊殺音效").define("kill_sound", true);
        killSoundVolume = builder.comment("擊殺音效音量").defineInRange("kill_sound_volume", 0.8, 0.0, 1.0);
        domBeacon = builder.comment("佔點：據點光柱、地面範圍環、螢幕上的據點字母與距離").define("dom_beacon", true);
        antiCampWall = builder.comment("接近敵方重生區時顯示的半透明紅色邊界").define("anti_camp_wall", true);
        scoreboardToggle = builder.comment("戰績面板改成按一下開、再按一下關（Caps Lock 放開事件不可靠時用）").define("scoreboard_toggle", false);
        builder.pop();
    }
}
