package com.provinggrounds.core;

import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 檔案: WeaponMode.java
 * 目的: 槍械模式（大廳設定，可以和特殊物品模式同時開）：這一場只出現某一類的槍。
 *   箱子、空投生成內容時，以及祭壇復活的基礎裝備，不是這一類的槍換成同類隨機一把（子彈照常自動配）。
 *   投擲物模式：槍全部換成投擲物（設定 weapon_mode_throwables 的清單），箱子裡的子彈與槍械配件拿掉。
 *   槍的類型來自槍械包（pistol、shotgun、smg、rifle、mg、sniper、rpg）；「爆炸性」= rpg（火箭筒、榴彈發射器）。
 */
public enum WeaponMode {
    ALL("全部", null),
    PISTOL("手槍", "pistol"),
    SHOTGUN("霰彈槍", "shotgun"),
    SMG("衝鋒槍", "smg"),
    RIFLE("步槍", "rifle"),
    MG("機槍", "mg"),
    SNIPER("狙擊槍", "sniper"),
    EXPLOSIVE("爆炸性", "rpg"),
    THROWABLE("投擲物", null);

    public final String displayName;
    /** 槍械包裡的類型；null = 不限（全部）或不出槍（投擲物）。 */
    public final String gunType;

    WeaponMode(String displayName, String gunType) {
        this.displayName = displayName;
        this.gunType = gunType;
    }

    public static WeaponMode byOrdinal(int i) {
        WeaponMode[] all = values();
        return i >= 0 && i < all.length ? all[i] : ALL;
    }

    /** 目前大廳選的模式。 */
    public static WeaponMode current() {
        return LobbyManager.get().weaponMode();
    }

    /** 箱子生成內容後呼叫（在補子彈之前）。 */
    public static void applyToContainer(Container c, RandomSource random) {
        WeaponMode mode = current();
        if (mode == ALL) return;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            if (mode == THROWABLE) {
                if (GunCompat.classify(s) != com.provinggrounds.compat.GunClass.NONE) c.setItem(i, randomThrowable(random));
                else if (s.is(GunCompat.AMMO) || GunCompat.isAttachment(s)) c.setItem(i, ItemStack.EMPTY); // 沒有槍：子彈、配件沒用
            } else {
                c.setItem(i, convert(s, mode, random));
            }
        }
    }

    /** 不是這一類的槍換成同類隨機一把；不是槍原樣回傳。 */
    public static ItemStack convert(ItemStack s, RandomSource random) {
        WeaponMode mode = current();
        if (mode == ALL) return s;
        if (mode == THROWABLE) return GunCompat.classify(s) != com.provinggrounds.compat.GunClass.NONE ? randomThrowable(random) : s;
        return convert(s, mode, random);
    }

    private static ItemStack convert(ItemStack s, WeaponMode mode, RandomSource random) {
        String type = GunCompat.gunType(s);
        if (type == null || type.equals(mode.gunType)) return s;
        List<ResourceLocation> pool = GunCompat.gunsOfType(mode.gunType);
        if (pool.isEmpty()) return s;
        ItemStack gun = GunCompat.createGun(pool.get(random.nextInt(pool.size())));
        return gun.isEmpty() ? s : gun;
    }

    private static ItemStack randomThrowable(RandomSource random) {
        List<? extends String> list = PGConfig.SERVER.weaponModeThrowables.get();
        if (list.isEmpty()) return ItemStack.EMPTY;
        ItemStack t = com.provinggrounds.modes.br.RespawnKit.parse(list.get(random.nextInt(list.size())));
        if (!t.isEmpty()) t.setCount(1 + random.nextInt(2));
        return t;
    }
}
