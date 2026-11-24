package com.provinggrounds.events;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: InteractEvents.java
 * 目的: 右鍵互動規則（計畫書 5 的 B1、B2；客戶端與伺服器都會觸發，兩邊都擋才不會畫面閃動）。
 *   1. 只處理主手：副手的右鍵事件一律取消（原版會先試主手、再試副手）。
 *   2. 手上拿槍時，右鍵不和方塊、實體互動（右鍵只開鏡）；要互動按 F（互動鍵）。
 *      F 鍵的互動：客戶端設 CLIENT_BYPASS、伺服器收到 InteractKeyPacket.MARK 後 3 tick 內放行。
 *   3. 扶人、祭壇讀條的中斷：使用物品（補品、投擲物…）、開槍、特殊物品、受傷。
 *
 *   「開門兩次」的原因（讀原版程式確認）：按住使用鍵時原版每 4 tick 會再觸發一次右鍵互動，
 *   TACZ 開鏡也是按住右鍵，所以準星對著門開鏡時門會一直開關；物資箱、墓碑同理會被打開。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class InteractEvents {

    /** 客戶端：F 鍵正在執行原版互動（只在客戶端主執行緒設定）。 */
    public static volatile boolean CLIENT_BYPASS;

    private static final Map<UUID, Long> KEY_TICK = new ConcurrentHashMap<>();

    private InteractEvents() {}

    public static void markKey(ServerPlayer p) {
        KEY_TICK.put(p.getUUID(), p.level().getGameTime());
    }

    private static boolean byKey(Player p) {
        if (p.level().isClientSide) return CLIENT_BYPASS;
        Long t = KEY_TICK.get(p.getUUID());
        return t != null && p.level().getGameTime() - t <= 3;
    }

    /** F 鍵按完：準星指著倒地隊友 → 扶；沒有和東西互動 → 找附近的倒地隊友，再找祭壇。 */
    public static void onKeyDone(ServerPlayer p, boolean interacted, int entityId) {
        if (p.isSpectator()) return;
        Entity looked = entityId >= 0 ? p.level().getEntity(entityId) : null;
        BattleRoyaleMode br = BattleRoyaleMode.get();
        if (br.startReviveByKey(p, looked, !interacted)) return;
        if (!interacted) br.startAltarByKey(p);
    }

    /** 這次右鍵要不要擋：副手一律擋；主手拿槍、不是 F 鍵就擋。 */
    private static boolean block(Player p, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) return true;
        return GunCompat.holdsGun(p) && !byKey(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!block(event.getEntity(), event.getHand())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!block(event.getEntity(), event.getHand())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!block(event.getEntity(), event.getHand())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() == InteractionHand.OFF_HAND) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }
        // 拿槍按住右鍵是開鏡，不算使用物品
        if (event.getEntity() instanceof ServerPlayer sp && !GunCompat.holdsGun(sp) && !event.getItemStack().isEmpty()) {
            BattleRoyaleMode.get().interruptProgress(sp);
        }
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer sp) BattleRoyaleMode.get().interruptProgress(sp);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getAmount() > 0 && event.getEntity() instanceof ServerPlayer sp) BattleRoyaleMode.get().interruptProgress(sp);
    }

    /** 開槍：由 GunCompat 通知（只在伺服器端處理）。 */
    public static void register() {
        GunCompat.onShoot(shooter -> {
            if (shooter instanceof ServerPlayer sp) BattleRoyaleMode.get().interruptProgress(sp);
        });
    }
}
