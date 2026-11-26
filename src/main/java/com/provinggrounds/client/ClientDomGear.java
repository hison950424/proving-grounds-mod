package com.provinggrounds.client;

import com.provinggrounds.network.DomGearPacket;
import com.provinggrounds.special.DomGear;
import com.provinggrounds.special.SpecialType;

/**
 * 檔案: ClientDomGear.java
 * 目的: 客戶端保存的佔點整場特殊物品狀態（計畫書 8）：大廳選擇、比賽中的那一種、這回合用過沒。
 *   大廳選單、商店加價顯示、特殊物品格的「這回合用過」顯示都讀這裡。
 */
public final class ClientDomGear {

    private static volatile DomGearPacket state = new DomGearPacket(false, -1, -1, false);
    private static volatile int version;

    private ClientDomGear() {}

    public static void set(DomGearPacket p) {
        state = p;
        version++;
    }

    public static int version() { return version; }

    public static boolean inMatch() { return state.inMatch(); }

    public static boolean used() { return state.inMatch() && state.used(); }

    private static SpecialType type(int ordinal) {
        SpecialType[] all = SpecialType.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    /** 大廳選的那一種。 */
    public static SpecialType choice() { return type(state.choice()); }

    /** 目前佔點對局中的特殊道具（沒在對局中 = null）。 */
    public static SpecialType activeGear() { return inMatch() ? type(state.gear()) : null; }

    /** 商店價格（佔點依所選特殊道具的獨立加價 % 計算）。 */
    public static int price(int base) {
        SpecialType g = activeGear();
        return g == null ? base : DomGear.applyMarkup(base, g);
    }
}
