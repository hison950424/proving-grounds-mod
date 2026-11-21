package com.provinggrounds.events;

import com.provinggrounds.modes.BattleRoyaleMode;
import com.provinggrounds.modes.br.BRDowned;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 檔案: DownedEvents.java
 * 目的: 倒地中的玩家禁止所有動作：右鍵、左鍵攻擊、放置／破壞方塊、丟棄、開啟容器、使用物品、回血。
 *       互動事件在客戶端與伺服器都會觸發，兩邊都取消才不會有手臂揮動或物品使用動畫。
 *       切換手持物由 BattleRoyaleMode 每 tick 鎖回原本的格子；開槍由槍械適配層（GunCompat）攔截。
 */
public class DownedEvents {

    private static boolean downed(net.minecraft.world.entity.Entity e) {
        return BRDowned.isDowned(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!downed(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!downed(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (downed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!downed(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!downed(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        if (downed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (downed(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() != null && downed(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (downed(event.getEntity())) event.setCanceled(true);
    }

    /** 丟棄：Forge 取消丟棄時物品已經從背包拿出來了，要放回去，否則會消失。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || !downed(p)) return;
        event.setCanceled(true);
        ItemStack stack = event.getEntity().getItem().copy();
        // 理論上不會滿（物品剛從背包拿出來）。萬一滿了用 spawnAtLocation 放地上，
        // 不能用 p.drop()，那會再觸發一次丟棄事件而無限循環。
        if (!p.getInventory().add(stack) && !stack.isEmpty()) p.spawnAtLocation(stack);
        p.containerMenu.broadcastChanges();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOpenContainer(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer p && downed(p)) p.closeContainer();
    }

    /** 倒地不會自然回血（食物、藥水都一樣）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && downed(p)) event.setCanceled(true);
    }

    /** 坐姿倒地（downed_style = SIT）時不能蹲下離開座椅。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMount(EntityMountEvent event) {
        if (!event.isDismounting() || !(event.getEntityMounting() instanceof ServerPlayer p)) return;
        if (BattleRoyaleMode.get().isDownedSeat(p, event.getEntityBeingMounted())) event.setCanceled(true);
    }
}
