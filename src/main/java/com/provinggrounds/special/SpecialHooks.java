package com.provinggrounds.special;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: SpecialHooks.java
 * 目的: Mixin 呼叫的小工具（兩端共用，不碰任何只在客戶端的類別）。
 *   CLIENT_GLOWING：客戶端「現在看得到在發光」的實體編號（由 ClientSpecial 依 GlowSyncPacket 更新）。
 */
public final class SpecialHooks {

    public static final Set<Integer> CLIENT_GLOWING = ConcurrentHashMap.newKeySet();

    private SpecialHooks() {}

    /** 靜音靴：這個玩家的移動聲音要不要靜音。 */
    public static boolean silent(Entity e) {
        return e instanceof Player && e instanceof LivingEntity l && l.hasEffect(SpecialRegistry.SILENT.get());
    }
}
