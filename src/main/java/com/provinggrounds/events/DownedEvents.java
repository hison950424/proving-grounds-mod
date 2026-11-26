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
 * 目的: 動作鎖定（ActionLock：倒地、隱形斗篷）時禁止的動作：右鍵、左鍵攻擊、放置／破壞方塊、開啟容器、使用物品；倒地不能回血。
 *       互動事件在客戶端與伺服器都會觸發，兩邊都取消才不會有手臂揮動或物品使用動畫。
 *       可以丟棄、吸取物品、切換快捷欄（計畫書 4 的 C 章）；開槍由槍械適配層（GunCompat）攔截。
 */
public class DownedEvents {

    /** 動作鎖定中（倒地或隱形斗篷）。 */
    private static boolean downed(net.minecraft.world.entity.Entity e) {
        return com.provinggrounds.special.ActionLock.blocksActions(e);
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!downed(event.getEntity())) return;
        event.getEntity().stopUsingItem();
        event.setCanceled(true);
    }

    /** 丟棄：倒地、斗篷都可以丟（計畫書 4 的 C 章）。只有擊退盾不能丟（穿戴中的盾）；Forge 取消丟棄時物品已經拿出來了，要放回原位。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p)) return;
        ItemStack stack = event.getEntity().getItem();
        if (!stack.is(com.provinggrounds.special.SpecialRegistry.SHIELD_HELD.get()) && !com.provinggrounds.item.ParachuteItem.is(stack)) return; // 擊退盾、降落傘不能丟
        event.setCanceled(true);
        int slot = p.getInventory().selected;
        if (p.getInventory().getItem(slot).isEmpty()) p.getInventory().setItem(slot, stack.copy());
        else if (!p.getInventory().add(stack.copy())) p.spawnAtLocation(stack.copy());
        p.containerMenu.broadcastChanges();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOpenContainer(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer p && downed(p)) p.closeContainer();
    }

    /** 倒地不會自然回血（食物、藥水都一樣）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && BRDowned.isDowned(p)) event.setCanceled(true);
    }

    /** 坐姿倒地（downed_style = SIT）時不能蹲下離開座椅。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMount(EntityMountEvent event) {
        if (!event.isDismounting() || !(event.getEntityMounting() instanceof ServerPlayer p)) return;
        if (BattleRoyaleMode.get().isDownedSeat(p, event.getEntityBeingMounted())) event.setCanceled(true);
        if (BattleRoyaleMode.get().isPlaneSeat(p, event.getEntityBeingMounted())) event.setCanceled(true); // 飛機上按 Shift 不能下機，要按空白鍵跳
    }
}
