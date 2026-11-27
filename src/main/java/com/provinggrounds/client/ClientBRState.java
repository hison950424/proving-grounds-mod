package com.provinggrounds.client;

import com.provinggrounds.network.BRStatePacket;
import com.provinggrounds.network.KillFeedPacket;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 檔案: ClientBRState.java
 * 目的: 客戶端收到的 BR 狀態快取，給 HUD、縮圈牆渲染（含幀間平滑插值）與跳躍修正讀取。
 *       只放純資料、不 import 任何客戶端類別，所以在專用伺服器上被載入也不會崩潰。
 */
public final class ClientBRState {

    public static final byte PHASE_NONE = 0, PHASE_PREPARING = 1, PHASE_PLANE = 2, PHASE_ACTIVE = 3, PHASE_ENDING = 4;
    public static final byte SELF_NONE = 0, SELF_PLANE = 1, SELF_FREEFALL = 2, SELF_CHUTE = 3, SELF_ALIVE = 4, SELF_DEAD = 5,
        SELF_DOWNED = 6, SELF_GLIDE = 7; // FREEFALL = 收傘後的自由落體、GLIDE = 鞘翅滑翔
    public static final byte PROGRESS_NONE = 0, PROGRESS_REVIVING = 1, PROGRESS_BEING_REVIVED = 2, PROGRESS_ALTAR = 3;
    public static final byte TIMER_NONE = 0, TIMER_BLEED = 1, TIMER_CARD_GROUND = 2, TIMER_CARD_HELD = 3, TIMER_FINAL = 4;

    public record SmoothCircle(double cx, double cz, double radius) {}

    private static final long DEFAULT_INTERP_NS = 100_000_000L;
    private static final long MIN_INTERP_NS = 60_000_000L;
    private static final long MAX_INTERP_NS = 320_000_000L;

    private static volatile BRStatePacket state = BRStatePacket.none();
    private static volatile long lastUpdateMs = 0;

    // 縮圈牆／地圖邊界幀間平滑插值狀態
    private static final Object CIRCLE_LOCK = new Object();
    private static byte lastCirclePhase = PHASE_NONE;
    private static double fromCx, fromCz, fromR = -1;
    private static double toCx, toCz, toR = -1;
    private static long interpStartNs = 0L;
    private static long interpDurationNs = DEFAULT_INTERP_NS;
    private static long lastCirclePacketNs = 0L;

    public record KillEntry(KillFeedPacket data, long timeMs) {}
    private static final Deque<KillEntry> KILLS = new ArrayDeque<>();
    public static final int MAX_KILLS = 5;
    public static final long KILL_SHOW_MS = 6000;

    private ClientBRState() {}

    public static void apply(BRStatePacket packet) {
        applyAt(packet, System.nanoTime());
    }

    static void applyAt(BRStatePacket packet, long nowNs) {
        state = packet;
        lastUpdateMs = System.currentTimeMillis();
        synchronized (CIRCLE_LOCK) {
            byte ph = packet.phase();
            double r = packet.radius();
            if (ph != PHASE_ACTIVE || r < 0) {
                lastCirclePhase = ph;
                fromCx = toCx = packet.cx();
                fromCz = toCz = packet.cz();
                fromR = toR = r;
                interpStartNs = nowNs;
                interpDurationNs = DEFAULT_INTERP_NS;
                lastCirclePacketNs = nowNs;
            } else if (lastCirclePhase != PHASE_ACTIVE || toR < 0
                    || Math.abs(r - toR) > 30.0
                    || Math.hypot(packet.cx() - toCx, packet.cz() - toCz) > 30.0) {
                lastCirclePhase = PHASE_ACTIVE;
                fromCx = toCx = packet.cx();
                fromCz = toCz = packet.cz();
                fromR = toR = r;
                interpStartNs = nowNs;
                interpDurationNs = DEFAULT_INTERP_NS;
                lastCirclePacketNs = nowNs;
            } else {
                double t = interpDurationNs <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (nowNs - interpStartNs) / (double) interpDurationNs));
                double curCx = fromCx + (toCx - fromCx) * t;
                double curCz = fromCz + (toCz - fromCz) * t;
                double curR = fromR + (toR - fromR) * t;
                long dt = nowNs - lastCirclePacketNs;
                long clampedDt = Math.max(MIN_INTERP_NS, Math.min(MAX_INTERP_NS, dt > 0 ? dt : DEFAULT_INTERP_NS));
                interpDurationNs = (long) (interpDurationNs * 0.65 + clampedDt * 0.35);
                fromCx = curCx;
                fromCz = curCz;
                fromR = curR;
                toCx = packet.cx();
                toCz = packet.cz();
                toR = r;
                interpStartNs = nowNs;
                lastCirclePacketNs = nowNs;
            }
        }
        // 背包鎖格（B3）只對 BR 進行中的參賽者生效：客戶端用來預測與畫遮罩
        byte s = packet.selfState();
        com.provinggrounds.modes.br.Backpack.clientRulesActive = (packet.phase() == PHASE_PLANE || packet.phase() == PHASE_ACTIVE)
            && s != SELF_NONE && s != SELF_DEAD;
        if (packet.phase() == PHASE_NONE) {
            synchronized (KILLS) { KILLS.clear(); }
        }
    }

    public static BRStatePacket get() {
        // 超過 5 秒沒收到更新（離開伺服器、伺服器卡住）就視為沒有遊戲，避免殘留 HUD 與跳躍修正
        if (System.currentTimeMillis() - lastUpdateMs > 5000) return BRStatePacket.none();
        return state;
    }

    /** 取得目前時間點經線性平滑插值後的安全區圓心與半徑（消弭 2~5 tick 同步間隔的跳格感）。 */
    public static SmoothCircle smoothCircle() {
        return smoothCircleAt(System.nanoTime());
    }

    static SmoothCircle smoothCircleAt(long nowNs) {
        BRStatePacket s = get();
        synchronized (CIRCLE_LOCK) {
            if (s.phase() != PHASE_ACTIVE || toR < 0 || lastCirclePhase != PHASE_ACTIVE) {
                return new SmoothCircle(s.cx(), s.cz(), s.radius());
            }
            double t = interpDurationNs <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (nowNs - interpStartNs) / (double) interpDurationNs));
            return new SmoothCircle(
                fromCx + (toCx - fromCx) * t,
                fromCz + (toCz - fromCz) * t,
                fromR + (toR - fromR) * t
            );
        }
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
        synchronized (CIRCLE_LOCK) {
            lastCirclePhase = PHASE_NONE;
            fromCx = fromCz = toCx = toCz = 0;
            fromR = toR = -1;
            interpStartNs = lastCirclePacketNs = 0L;
            interpDurationNs = DEFAULT_INTERP_NS;
        }
        com.provinggrounds.modes.br.Backpack.clearClient();
        com.provinggrounds.special.SpecialSlot.clearClient();
        com.provinggrounds.special.client.ClientSpecial.clear();
        synchronized (KILLS) { KILLS.clear(); }
    }
}
