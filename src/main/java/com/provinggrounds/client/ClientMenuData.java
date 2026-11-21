package com.provinggrounds.client;

import com.provinggrounds.network.MenuDataPacket;

import javax.annotation.Nullable;

/**
 * 檔案: ClientMenuData.java
 * 目的: 客戶端收到的主選單資料快取。只放純資料、不 import 客戶端類別，
 *       所以在專用伺服器上被載入也不會崩潰。version 每次更新 +1，畫面據此判斷要不要重繪。
 */
public final class ClientMenuData {

    @Nullable private static volatile MenuDataPacket data;
    private static volatile int version = 0;

    private ClientMenuData() {}

    public static void set(MenuDataPacket packet) {
        data = packet;
        version++;
    }

    @Nullable
    public static MenuDataPacket get() { return data; }

    public static int version() { return version; }

    public static void clear() {
        data = null;
        version++;
    }
}
