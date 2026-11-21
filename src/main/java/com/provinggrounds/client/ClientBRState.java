package com.provinggrounds.client;

import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.KillFeedPacket;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 檔案: ClientBRState.java
 * 目的: 客戶端收到的 BR 狀態快取，給 HUD、縮圈牆渲染與跳躍修正讀取。
 *       只放純資料、不 import 任何客戶端類別，所以在專用伺服器上被載入也不會崩潰。
 */
public final class ClientBRState {

    public static final byte PHASE_NONE = 0, PHASE_PREPARING = 1, PHASE_PLANE = 2, PHASE_ACTIVE = 3, PHASE_ENDING = 4;
    public static final byte SELF_NONE = 0, SELF_PLANE = 1, SELF_FREEFALL = 2, SELF_CHUTE = 3, SELF_ALIVE = 4, SELF_DEAD = 5,
        SELF_DOWNED = 6;
    public static final byte PROGRESS_NONE = 0, PROGRESS_REVIVING = 1, PROGRESS_BEING_REVIVED = 2, PROGRESS_ALTAR = 3;
    public static final byte TIMER_NONE = 0, TIMER_BLEED = 1, TIMER_CARD_GROUND = 2, TIMER_CARD_HELD = 3, TIMER_FINAL = 4;

    private static volatile BRStatePacket state = BRStatePacket.none();
    private static volatile long lastUpdateMs = 0;

    public record KillEntry(KillFeedPacket data, long timeMs) {}
    private static final Deque<KillEntry> KILLS = new ArrayDeque<>();
    public static final int MAX_KILLS = 5;
    public static final long KILL_SHOW_MS = 6000;

    private ClientBRState() {}

    public static void apply(BRStatePacket packet) {
        state = packet;
        lastUpdateMs = System.currentTimeMillis();
        if (packet.phase() == PHASE_NONE) {
            synchronized (KILLS) { KILLS.clear(); }
        }
    }

    public static BRStatePacket get() {
        // 超過 5 秒沒收到更新（離開伺服器、伺服器卡住）就視為沒有遊戲，避免殘留 HUD 與跳躍修正
        if (System.currentTimeMillis() - lastUpdateMs > 5000) return BRStatePacket.none();
        return state;
    }

    public static boolean active() {
        return get().phase() != PHASE_NONE;
    }

    public static void addKill(KillFeedPacket packet) {
        synchronized (KILLS) {
            KILLS.addFirst(new KillEntry(packet, System.currentTimeMillis()));
            while (KILLS.size() > MAX_KILLS) KILLS.removeLast();
        }
    }

    public static KillEntry[] kills() {
        synchronized (KILLS) {
            long now = System.currentTimeMillis();
            KILLS.removeIf(k -> now - k.timeMs() > KILL_SHOW_MS);
            return KILLS.toArray(new KillEntry[0]);
        }
    }

    public static void clear() {
        state = BRStatePacket.none();
        synchronized (KILLS) { KILLS.clear(); }
    }
}
