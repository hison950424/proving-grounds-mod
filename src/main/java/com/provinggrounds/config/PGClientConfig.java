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
    public final ForgeConfigSpec.BooleanValue compassEnabled;
    public final ForgeConfigSpec.DoubleValue compassScale;
    public final ForgeConfigSpec.IntValue compassYOffset;
    public final ForgeConfigSpec.BooleanValue compatHighlightEnabled;
    public final ForgeConfigSpec.ConfigValue<String> compatHighlightColor;
    public final ForgeConfigSpec.DoubleValue compatHighlightOpacity;
    public final ForgeConfigSpec.BooleanValue damageIndicatorEnabled;
    public final ForgeConfigSpec.DoubleValue damageIndicatorSeconds;

    private PGClientConfig(ForgeConfigSpec.Builder builder) {
        builder.push("ui_walk");
        uiWalkEnabled = builder.comment("開著任何介面（物品欄、箱子、地圖、選單…）時仍可用前後左右移動鍵走動")
            .define("enabled_v2", true);
        uiWalkExcludedScreens = builder.comment(
                "不接管移動鍵的畫面類別名稱（可寫完整名稱或只寫類別名稱）。",
                "有輸入框正在輸入、按鍵設定、聊天、死亡、載入、斷線畫面本來就不會接管。")
            .defineListAllowEmpty(List.of("ui_walk_excluded_screens"), List::of, o -> o instanceof String);
        builder.pop();

        builder.comment("遊戲畫面中的縮圈牆（不影響小地圖、大地圖上畫的圈）").push("circle");
        circleWallOpacity = builder.comment("目前圈的牆（白色）不透明度，0.15 = 15%")
            .defineInRange("circle_wall_opacity", 0.15, 0.0, 1.0);
        circleNextOpacity = builder.comment("下一圈的環（淺藍色）不透明度，0.075 = 7.5%")
            .defineInRange("circle_next_opacity", 0.075, 0.0, 1.0);
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
    }
}
