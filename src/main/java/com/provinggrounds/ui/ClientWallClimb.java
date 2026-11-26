package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.client.ClientProneData;
import com.provinggrounds.compat.ClientGunAim;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.core.WallClimb;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.WallClimbPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * 檔案: ClientWallClimb.java
 * 目的: 客戶端柵欄／石牆攀爬輸入偵測、收槍／取槍表現與本地曲線推進（只在客戶端）：
 *   - 貼著柵欄／石牆且沒開介面、沒趴下、視野大致面向牆時剛按下空白鍵 → 攔截原本跳躍，觸發翻爬並送 WallClimbPacket。
 *   - 攀爬期間鎖定移動／跳躍並中斷持續使用物品，收起手中物品騰出雙手；爬完後重播切換到該格的舉起動畫。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientWallClimb {

    private static boolean jumpWas;
    private static Field fMainHeight, fOMainHeight, fOffHeight, fOOffHeight;
    private static boolean heightFieldsReady, heightFieldsFailed;

    private ClientWallClimb() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getEntity() instanceof LocalPlayer p) || p != mc.player) return;

        boolean jumpDown = mc.screen == null && mc.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !jumpWas;
        jumpWas = jumpDown;

        var in = event.getInput();
        if (WallClimb.isClientClimbing(p.getId())) {
            in.forwardImpulse = 0.0f;
            in.leftImpulse = 0.0f;
            in.jumping = false;
            in.shiftKeyDown = false;
            if (p.isUsingItem()) p.stopUsingItem();
            return;
        }

        if (!jumpPressed || mc.screen != null) return;
        if (ClientProneData.isProne(p.getUUID()) || ClientBRState.get().crawling().contains(p.getUUID())) return;

        WallClimb.ClimbTarget target = WallClimb.findClimbTarget(p);
        if (target == null) return;

        in.jumping = false;
        WallClimb.startClientLocal(p, target);
        ClientGunAim.cancelForClimb(p);
        PGNetwork.sendToServer(new WallClimbPacket(target.wallPos(), target.yaw()));
    }

    /** 攀爬中第一人稱手部：前段迅速往下收起，騰出雙手後隱藏，爬完再播放切換舉起動畫。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !WallClimb.isClientClimbing(mc.player.getId())) return;
        if (WallClimb.isHandsStowed(mc.player, event.getPartialTick())) {
            event.setCanceled(true);
            return;
        }
        float stow = WallClimb.stowProgress(mc.player, event.getPartialTick());
        if (stow > 0.0f) {
            event.getPoseStack().translate(0.0, -stow * 0.9, 0.0);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            jumpWas = false;
            return;
        }
        if (WallClimb.tickClient(mc.player) && mc.player != null) {
            triggerEquipRaise(mc, mc.player);
        }
    }

    private static void triggerEquipRaise(Minecraft mc, LocalPlayer p) {
        resetVanillaHandHeight(mc, p);
        if (GunCompat.holdsGun(p)) {
            ClientGunAim.triggerDraw(p);
        }
    }

    private static void resetVanillaHandHeight(Minecraft mc, LocalPlayer p) {
        if (!ensureHeightFields()) return;
        ItemInHandRenderer renderer = mc.gameRenderer.itemInHandRenderer;
        try {
            if (!p.getMainHandItem().isEmpty()) {
                fMainHeight.setFloat(renderer, 0.0f);
                fOMainHeight.setFloat(renderer, 0.0f);
                renderer.itemUsed(InteractionHand.MAIN_HAND);
            }
            if (!p.getOffhandItem().isEmpty()) {
                fOffHeight.setFloat(renderer, 0.0f);
                fOOffHeight.setFloat(renderer, 0.0f);
                renderer.itemUsed(InteractionHand.OFF_HAND);
            }
        } catch (IllegalAccessException | RuntimeException ignored) {}
    }

    private static boolean ensureHeightFields() {
        if (heightFieldsReady) return true;
        if (heightFieldsFailed) return false;
        try {
            fMainHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109302_");
            fOMainHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109303_");
            fOffHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109304_");
            fOOffHeight = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109305_");
            heightFieldsReady = true;
            return true;
        } catch (RuntimeException e) {
            heightFieldsFailed = true;
            ProvingGroundsMod.LOGGER.warn("ItemInHandRenderer height fields unavailable: {}", e.toString());
            return false;
        }
    }
}
