package com.provinggrounds.events;

import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.provinggrounds.registry.PGBlocks;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 檔案: BREvents.java
 * 目的: 把 Forge 事件（死亡、受傷、摔落、登入、登出）轉給 BattleRoyaleMode。
 *       白話：遊戲發生這些事時，會先「通知」這裡，再由這裡交給大逃殺程式處理。
 */
public class BREvents {

    /** 參賽者死亡 → 改成 BR 淘汰流程（掉物品、變觀察者），取消原版死亡畫面。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (!br.isRunning()) return;
        if (br.onLethalDamage(victim, event.getSource())) event.setCanceled(true);
    }

    /** 記錄誰打過誰（助攻判定）。 */
    @SubscribeEvent
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (br.isRunning()) br.onDamaged(victim, event.getSource(), event.getAmount());
    }

    /** 跳傘中落地不受摔落傷害（不論有沒有開傘）。 */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (BattleRoyaleMode.get().isDropping(p.getUUID())) event.setCanceled(true);
    }

    /** 飛機上與剛跳機的幾秒內：不會受傷，也不能攻擊別人。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlaneAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (event.getEntity() instanceof ServerPlayer victim && (br.isProtected(victim.getUUID()) || br.isPreparingParticipant(victim.getUUID()))) {
            event.setCanceled(true); // 飛機上、剛跳機、上飛機前的準備時間：不會受傷
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && br.isProtected(attacker.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlaneMelee(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && BattleRoyaleMode.get().isProtected(p.getUUID())) event.setCanceled(true);
    }

    /** 鞘翅滑翔撞牆的傷害（原版 fly_into_wall）：跳傘中不受傷。 */
    @SubscribeEvent
    public void onElytraCrash(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL) && BattleRoyaleMode.get().isDropping(p.getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * 撿取：復活牌一律不進背包（同隊吸走、敵隊吸不到）；倒地中不能撿任何東西。
     * 這個事件在背包檢查之前觸發，所以背包滿也能吸取復活牌。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (!br.isRunning() && !event.getItem().getItem().is(PGBlocks.REVIVE_CARD.get())) return;
        if (br.onItemPickup(p, event.getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) BattleRoyaleMode.get().onLogin(p);
    }

    /** 優先度 HIGH：要在 PlayerEvents 把玩家移出 GameManager 之前先處理淘汰。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) BattleRoyaleMode.get().onLogout(p);
    }
}
