package com.provinggrounds.ui;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * 檔案: Tr.java
 * 目的: 介面文字一律從語言檔（assets/provinggrounds/lang/zh_tw.json）讀取，程式裡不寫死字串（計畫書 A3）。
 *       鍵名自動加上前綴 "provinggrounds."，參數用 %s。只在客戶端使用。
 */
public final class Tr {

    private Tr() {}

    /** 取得翻譯後的文字。 */
    public static String t(String key, Object... args) {
        return I18n.get("provinggrounds." + key, args);
    }

    /** 取得翻譯後的 Component（給按鈕、畫面標題用）。 */
    public static Component c(String key, Object... args) {
        return Component.literal(t(key, args));
    }
}
