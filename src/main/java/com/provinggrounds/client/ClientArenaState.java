package com.provinggrounds.client;

import com.provinggrounds.network.ArenaStatePacket;

/**
 * 檔案: ClientArenaState.java
 * 目的: 客戶端保存的團隊死鬥／佔點／槍王之王狀態（HUD、商店介面用）。
 */
public final class ClientArenaState {

    private static volatile ArenaStatePacket state = ArenaStatePacket.NONE;
    private static volatile int version;
    /** 剛進入開局 10 秒準備選角階段：由 ClientForgeEvents 自動彈出 LoadoutScreen。 */
    private static volatile boolean pendingOpenLoadout;

    private ClientArenaState() {}

    public static void set(ArenaStatePacket p) {
        boolean wasOpen = state.loadoutOpen();
        state = p;
        version++;
        if (p.kind() != 0 && p.classes() && p.loadoutOpen() && !wasOpen) {
            pendingOpenLoadout = true;
        }
        if (p.kind() == 0 || !p.loadoutOpen()) {
            pendingOpenLoadout = false;
        }
    }

    public static boolean consumePendingLoadout() {
        if (!pendingOpenLoadout) return false;
        pendingOpenLoadout = false;
        return true;
    }

    public static ArenaStatePacket get() { return state; }

    public static int version() { return version; }

    /** 正在進行團隊死鬥／佔點／槍王之王。 */
    public static boolean active() { return state.kind() != 0; }

    public static void clear() { set(ArenaStatePacket.NONE); }
}
