package com.provinggrounds.client;

import com.provinggrounds.network.DomGearPacket;
import com.provinggrounds.special.DomGear;
import com.provinggrounds.special.SpecialType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 檔案: ClientDomGear.java
 * 目的: 客戶端保存的佔點特殊物品裝備狀態（計畫書 8）：大廳選擇、比賽中的裝備、冷卻（收到後自己倒數）、額外的。
 *   大廳選單、商店、長按 5 的輪盤、特殊物品格的冷卻顯示都讀這裡。
 */
public final class ClientDomGear {

    private static volatile DomGearPacket state = new DomGearPacket(false, new int[0], new int[0], new int[0], List.of(), 0);
    private static volatile long receivedAt; // 客戶端世界的遊戲時間
    private static volatile int version;

    private ClientDomGear() {}

    public static void set(DomGearPacket p) {
        state = p;
        receivedAt = gameTime();
        version++;
    }

    public static int version() { return version; }

    public static DomGearPacket get() { return state; }

    public static boolean inMatch() { return state.inMatch(); }

    private static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    private static SpecialType type(int ordinal) {
        SpecialType[] all = SpecialType.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    /** 大廳選的第 i 個。 */
    public static SpecialType choice(int i) {
        int[] c = state.choice();
        return i < c.length ? type(c[i]) : null;
    }

    /** 比賽中第 i 個裝備。 */
    public static SpecialType gear(int i) {
        int[] g = state.gear();
        return inMatch() && i < g.length ? type(g[i]) : null;
    }

    /** 第 i 個裝備剩餘冷卻（tick）。 */
    public static int cooldownLeft(int i) {
        int[] c = state.cooldownTicks();
        if (!inMatch() || i >= c.length) return 0;
        return (int) Math.max(0, c[i] - (gameTime() - receivedAt));
    }

    /** 第 i 個裝備的冷卻總長（tick）。 */
    public static int cooldownTotal(int i) {
        SpecialType t = gear(i);
        return t == null ? 1 : Math.max(1, DomGear.cooldownSeconds(t) * 20);
    }

    public static List<int[]> extras() { return state.extras(); }

    public static SpecialType extraType(int[] e) { return type(e[0]); }
}
