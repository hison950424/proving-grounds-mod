package com.provinggrounds.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 檔案: PGClientConfig.java
 * 目的: 客戶端設定（每位玩家自己的 config/provinggrounds-client.toml），不會從伺服器同步。
 *   ui_walk_enabled：開著介面時仍可用移動鍵走動（計畫書 A11）。
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

    private PGClientConfig(ForgeConfigSpec.Builder builder) {
        builder.push("ui_walk");
        uiWalkEnabled = builder.comment("開著任何介面（物品欄、箱子、地圖、選單…）時仍可用前後左右移動鍵走動")
            .define("ui_walk_enabled", true);
        uiWalkExcludedScreens = builder.comment(
                "不接管移動鍵的畫面類別名稱（可寫完整名稱或只寫類別名稱）。",
                "有輸入框正在輸入、按鍵設定、聊天、死亡、載入、斷線畫面本來就不會接管。")
            .defineListAllowEmpty(List.of("ui_walk_excluded_screens"), List::of, o -> o instanceof String);
        builder.pop();
    }
}
