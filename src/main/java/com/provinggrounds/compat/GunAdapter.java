package com.provinggrounds.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Predicate;

/**
 * 檔案: GunAdapter.java
 * 目的: 槍械適配層介面。核心程式只認這個介面，不直接 import 任何槍械模組。
 *       目前實作：TaczGunAdapter（TACZ）。之後換成自製槍械時，新增一個實作即可。
 */
public interface GunAdapter {

    /** 適配的槍械系統名稱（記錄用）。 */
    String name();

    /**
     * 註冊攔截：當 blocked 對開槍者回傳 true 時，取消開槍、近戰、換彈。
     * @return 是否成功掛上
     */
    boolean registerBlocker(Predicate<LivingEntity> blocked);

    /** 子彈命中通知（伺服器端，在扣血之前）：被打的實體、子彈實體、是否爆頭。 */
    @FunctionalInterface
    interface HitListener {
        void onHit(Entity target, Entity bullet, boolean headshot);
    }

    /**
     * 註冊命中通知（防彈計算用來判斷爆頭）。
     * @return 是否成功掛上
     */
    boolean registerHitListener(HitListener listener);

    /** 命中過濾（伺服器端，在扣血之前）：回傳 false 代表「其實沒打中」，這次傷害取消。 */
    @FunctionalInterface
    interface HitFilter {
        boolean accept(Entity target, Entity bullet);
    }

    /**
     * 註冊命中過濾（精準命中判定用）。
     * @return 是否成功掛上
     */
    default boolean registerHitFilter(HitFilter filter) { return false; }

    /** 子彈打到方塊通知（伺服器端，在槍械模組處理之前，例如打破玻璃之前）。 */
    @FunctionalInterface
    interface BlockHitListener {
        void onBlockHit(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state);
    }

    /**
     * 註冊子彈打到方塊的通知（記錄被打破的玻璃，每局開始時修回去）。
     * @return 是否成功掛上
     */
    boolean registerBlockHitListener(BlockHitListener listener);

    /** 判斷物品是不是槍、是手槍還是大型槍（兩端都可呼叫）。 */
    default GunClass classify(net.minecraft.world.item.ItemStack stack) { return GunClass.NONE; }

    /** 這個物品是不是槍械配件（瞄具、槍口、握把、槍托、雷射、擴充彈匣…；子彈不算）。兩端都可呼叫。 */
    default boolean isAttachment(net.minecraft.world.item.ItemStack stack) { return false; }

    /** 這把槍能不能裝這個配件（只看能不能裝，不管槍上是不是已經裝了同類型）。兩端都可呼叫。 */
    default boolean canAttach(net.minecraft.world.item.ItemStack gun, net.minecraft.world.item.ItemStack attachment) { return false; }

    /**
     * 把配件裝到槍上（伺服器端；會改動 gun）。
     * @return 換下來的舊配件（沒有舊配件時是空物品）；裝不上時回傳 null
     */
    @javax.annotation.Nullable
    default net.minecraft.world.item.ItemStack installAttachment(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.item.ItemStack gun, net.minecraft.world.item.ItemStack attachment) { return null; }

    /** 槍的 ID（例如 tacz:ak47）；不是槍時回傳 null。 */
    @javax.annotation.Nullable
    default net.minecraft.resources.ResourceLocation gunId(net.minecraft.world.item.ItemStack stack) { return null; }

    /**
     * 在實體的位置播放這把槍的射擊聲（伺服器端呼叫，附近的玩家都聽得到；假槍聲用）。
     * @return 是否成功（失敗時呼叫端改用原版音效）
     */
    default boolean playGunSound(net.minecraft.world.entity.Entity at, net.minecraft.resources.ResourceLocation gunId) { return false; }
}
