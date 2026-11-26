package com.provinggrounds.special;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: SpecialHooks.java
 * 目的: Mixin 與跨端程式呼叫的小工具（兩端共用，不碰任何只在客戶端的類別）。
 */
public final class SpecialHooks {

    public static final Set<Integer> CLIENT_GLOWING = ConcurrentHashMap.newKeySet();
    /** 客戶端同步：中了「敵我不分」的玩家 UUID。 */
    public static final Set<UUID> CLIENT_DISGUISED = ConcurrentHashMap.newKeySet();
    /** 客戶端同步：進入「無實體化」的玩家 UUID。 */
    public static final Set<UUID> CLIENT_ETHEREAL = ConcurrentHashMap.newKeySet();
    /** 客戶端同步：正在「擬態」的玩家 UUID → 箱子類型（0 木箱、1 鐵箱、2 金箱）。 */
    public static final Map<UUID, Integer> CLIENT_MIMIC = new ConcurrentHashMap<>();

    private SpecialHooks() {}

    /** 靜音靴：這個玩家的移動聲音要不要靜音。 */
    public static boolean silent(Entity e) {
        return e instanceof Player && e instanceof LivingEntity l && l.hasEffect(SpecialRegistry.SILENT.get());
    }

    /** 敵我不分：任一方有此效果時強制開啟友軍誤傷。 */
    public static boolean forceFriendlyFire(Entity a, Entity b) {
        return (a instanceof LivingEntity la && la.hasEffect(SpecialRegistry.FRIEND_OR_FOE.get()))
            || (b instanceof LivingEntity lb && lb.hasEffect(SpecialRegistry.FRIEND_OR_FOE.get()));
    }

    /** 無實體化：兩端都能問（自己靠效果、別人靠 CLIENT_ETHEREAL）。 */
    public static boolean isEthereal(Entity e) {
        if (e instanceof LivingEntity l && l.hasEffect(SpecialRegistry.ETHEREAL.get())) return true;
        return e != null && e.level().isClientSide && CLIENT_ETHEREAL.contains(e.getUUID());
    }

    /** 客戶端：畫面上這名玩家是否要顯示為史蒂夫（當本地玩家也中了敵我不分，且對象也在敵我不分名單中）。 */
    public static boolean showAsSteve(Player viewer, Entity target) {
        if (viewer == null || target == null || viewer == target) return false;
        if (!viewer.hasEffect(SpecialRegistry.FRIEND_OR_FOE.get())) return false;
        return target instanceof Player && CLIENT_DISGUISED.contains(target.getUUID());
    }

    public static void clearClient() {
        CLIENT_GLOWING.clear();
        CLIENT_DISGUISED.clear();
        CLIENT_ETHEREAL.clear();
        CLIENT_MIMIC.clear();
    }
}
