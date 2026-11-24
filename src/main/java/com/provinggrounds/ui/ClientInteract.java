package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.AltarBlock;
import com.provinggrounds.block.CrateBlock;
import com.provinggrounds.block.TombstoneBlock;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.compat.ClientGunAim;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGClientConfig;
import com.provinggrounds.events.InteractEvents;
import com.provinggrounds.network.InteractKeyPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * 檔案: ClientInteract.java
 * 目的: 互動鍵與準星（只在客戶端；計畫書 5 的 B 章）。
 *   - 互動鍵（預設 F，可在按鍵設定改）：按一下就和準星指著的門、箱子、墓碑、祭壇…互動，或開始扶倒地隊友。
 *   - 拿槍時右鍵不和方塊、實體互動（只開鏡）；空手時右鍵照原版。
 *   - 準星下方小字提示「F 開啟」「F 扶起」…；開鏡時不顯示。
 *   - 點狀準心：空手時由這裡畫，拿槍時由 TACZ 畫（已把 TACZ 準星設成點，見 ClientGunAim）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientInteract {

    public static final KeyMapping INTERACT = new KeyMapping("key.provinggrounds.interact", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.provinggrounds");

    public static final IGuiOverlay HINT_OVERLAY = (gui, g, partialTick, w, h) -> renderHint(g, w, h);

    private ClientInteract() {}

    // ── 互動鍵 ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (INTERACT.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.gameMode != null) interact(mc, mc.player);
        }
    }

    private static void interact(Minecraft mc, LocalPlayer p) {
        if (p.isSpectator()) return;
        HitResult hit = mc.hitResult;
        PGNetwork.sendToServer(InteractKeyPacket.mark());
        InteractionResult res = InteractionResult.PASS;
        int entityId = -1;
        InteractEvents.CLIENT_BYPASS = true;
        try {
            if (hit instanceof EntityHitResult eh) {
                Entity e = eh.getEntity();
                entityId = e.getId();
                if (!(e instanceof Player)) {
                    res = mc.gameMode.interactAt(p, e, eh, InteractionHand.MAIN_HAND);
                    if (!res.consumesAction()) res = mc.gameMode.interact(p, e, InteractionHand.MAIN_HAND);
                }
            } else if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK
                && label(mc.level.getBlockState(bh.getBlockPos()), mc, bh.getBlockPos()) != null) {
                // 只對「會互動的方塊」做原版互動，避免 F 把手上的方塊放出去
                res = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
            }
        } finally {
            InteractEvents.CLIENT_BYPASS = false;
        }
        if (res.shouldSwing()) p.swing(InteractionHand.MAIN_HAND);
        PGNetwork.sendToServer(new InteractKeyPacket(true, res.consumesAction(), entityId));
    }

    /** 拿槍時右鍵（使用鍵）不和方塊、實體互動，只開鏡；副手一律不用。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean blocked = event.getHand() == InteractionHand.OFF_HAND
            || (GunCompat.holdsGun(mc.player) && mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS);
        if (!blocked) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    // ── 互動提示 ────────────────────────────────────────────────────────────

    /** 方塊的互動提示；不能互動回傳 null。 */
    @Nullable
    static String label(BlockState s, Minecraft mc, BlockPos pos) {
        Block b = s.getBlock();
        if (b instanceof AltarBlock) return "F 復活";
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return "F 開關";
        if (b instanceof ButtonBlock || b instanceof LeverBlock) return "F 使用";
        if (b instanceof TombstoneBlock || b instanceof CrateBlock || b instanceof ChestBlock || b instanceof BarrelBlock
            || b instanceof ShulkerBoxBlock || s.getMenuProvider(mc.level, pos) != null) return "F 開啟";
        return null;
    }

    /** 倒地的隊友（倒地是爬行或坐姿）。 */
    private static boolean downedAlly(LocalPlayer self, Entity e) {
        return e instanceof Player other && ClientBRState.active() && self.isAlliedTo(other)
            && (other.getPose() == Pose.SWIMMING || other.isPassenger());
    }

    @Nullable
    private static String currentHint(Minecraft mc, LocalPlayer p) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult eh) return downedAlly(p, eh.getEntity()) ? "F 扶起" : null;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) return label(mc.level.getBlockState(bh.getBlockPos()), mc, bh.getBlockPos());
        return null;
    }

    private static void renderHint(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || mc.screen != null || p.isSpectator() || mc.level == null) return;
        boolean gun = GunCompat.holdsGun(p);
        if (!gun && !PGClientConfig.CLIENT.interactHint.get()) return;
        if (gun && ClientGunAim.aiming()) return; // 開鏡時不顯示（按 F 仍然有效）
        String hint = currentHint(mc, p);
        if (hint == null) return;
        String key = INTERACT.getTranslatedKeyMessage().getString();
        if (!"F".equalsIgnoreCase(key)) hint = key + hint.substring(1); // 改過按鍵就顯示新的鍵
        float scale = (float) (0.8 * PGClientConfig.CLIENT.hudScale.get());
        int tw = mc.font.width(hint);
        g.pose().pushPose();
        g.pose().translate(w / 2f, h / 2f + 12, 0);
        g.pose().scale(scale, scale, 1);
        g.fill(-tw / 2 - 3, -1, tw / 2 + 3, 9, 0x60000000);
        g.drawString(mc.font, hint, -tw / 2, 0, 0xE0FFFFFF, false);
        g.pose().popPose();
    }

    // ── 點狀準心（空手時；拿槍時 TACZ 會自己畫並取消原版準星）──────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCrosshair(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type() || event.isCanceled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!PGClientConfig.CLIENT.crosshairDot.get() || mc.player == null || GunCompat.holdsGun(mc.player)) return;
        event.setCanceled(true);
        if (!mc.options.getCameraType().isFirstPerson() || mc.player.isSpectator() || mc.options.renderDebug) return;
        int cx = event.getWindow().getGuiScaledWidth() / 2, cy = event.getWindow().getGuiScaledHeight() / 2;
        GuiGraphics g = event.getGuiGraphics();
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0x70000000);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xF0FFFFFF);
    }
}
