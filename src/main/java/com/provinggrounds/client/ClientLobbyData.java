package com.provinggrounds.client;

import com.provinggrounds.network.LobbyStatePacket;

import javax.annotation.Nullable;

/**
 * 檔案: ClientLobbyData.java
 * 目的: 客戶端收到的大廳狀態快取（純資料，專用伺服器載入也不會崩潰）。
 *       version 每次更新 +1，選單據此判斷要不要重建按鈕。
 */
public final class ClientLobbyData {

    @Nullable private static volatile LobbyStatePacket data;
    private static volatile int version = 0;

    private ClientLobbyData() {}

    public static void set(LobbyStatePacket packet) {
        data = packet;
        version++;
    }

    @Nullable
    public static LobbyStatePacket get() { return data; }

    public static int version() { return version; }

    public static void clear() {
        data = null;
        version++;
    }
}
