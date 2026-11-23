package com.provinggrounds.client;

import com.provinggrounds.network.ArenaStatePacket;

/**
 * 檔案: ClientArenaState.java
 * 目的: 客戶端保存的團隊死鬥／佔點／槍王之王狀態（HUD、商店介面用）。
 */
public final class ClientArenaState {

    private static volatile ArenaStatePacket state = ArenaStatePacket.NONE;
    private static volatile int version;

    private ClientArenaState() {}

    public static void set(ArenaStatePacket p) {
        state = p;
        version++;
    }

    public static ArenaStatePacket get() { return state; }

    public static int version() { return version; }

    /** 正在進行團隊死鬥／佔點／槍王之王。 */
    public static boolean active() { return state.kind() != 0; }

    public static void clear() { set(ArenaStatePacket.NONE); }
}
