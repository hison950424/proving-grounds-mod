package com.provinggrounds.special.client;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.special.ActionLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientControlLock.java
 * 目的: 計畫書 6 E1／E2 的客戶端輸入鎖定（只在客戶端載入）。效果本身由伺服器套用，原版會把自己的效果同步給自己。
 *   洗腦（E1）：不能開物品欄（E 鍵、已開著的物品欄會被關掉）、不能按 F 互動（ClientInteract 另外檢查）、
 *               不能切換快捷欄（數字鍵、滑鼠滾輪、輪盤；ClientWheel 另外檢查）。
 *   定身（E2）：移動輸入清空、不能跳、不能衝刺；滑鼠轉視角不受影響。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientControlLock {

    private ClientControlLock() {}

    /** 自己現在被洗腦（客戶端用）。 */
    public static boolean brainwashed() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && ActionLock.brainwashed(p);
    }

    /** 在原版處理按鍵之前吃掉：物品欄鍵、數字鍵。 */
    @SubscribeEvent
    public static void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ActionLock.brainwashed(mc.player)) return;
        while (mc.options.keyInventory.consumeClick()) { /* 吃掉 */ }
        for (var key : mc.options.keyHotbarSlots) while (key.consumeClick()) { /* 吃掉 */ }
        if (mc.screen instanceof InventoryScreen) mc.player.closeContainer();
    }

    /** 洗腦：滑鼠滾輪不能換格。 */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && ActionLock.brainwashed(mc.player)) event.setCanceled(true);
    }

    /** 洗腦：其他方式打開物品欄也擋掉。 */
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof InventoryScreen && brainwashed()) event.setCanceled(true);
    }

    /** 定身：移動輸入清空（在其他模組處理完之後）。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer p) || !ActionLock.rooted(p)) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        if (p.isSprinting()) p.setSprinting(false);
        var v = p.getDeltaMovement();
        if (p.onGround()) p.setDeltaMovement(0, v.y, 0); // 原本的慣性也停掉
    }
}
