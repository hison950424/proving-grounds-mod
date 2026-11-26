package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.data.BlockLedger;
import com.provinggrounds.network.MeleeGlassPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 檔案: ClientMeleeGlass.java
 * 目的: 拿近戰武器左鍵或右鍵攻擊玻璃／玻璃片／鐵欄杆 → 送 MeleeGlassPacket 給伺服器打破（只在客戶端）。
 *   同時監聽 InteractionKeyMappingTriggered 與 MouseButton.Pre，確保就算 lrtactical 攔截右鍵事件也能正常破壞方塊。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientMeleeGlass {

    private static long lastSent;

    private ClientMeleeGlass() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        boolean left = event.isAttack();
        boolean right = event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND;
        if (!left && !right) return;
        trySend(Minecraft.getInstance(), right);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        trySend(mc, true);
    }

    private static void trySend(Minecraft mc, boolean heavy) {
        if (mc.player == null || mc.level == null || com.provinggrounds.special.ActionLock.blocksActions(mc.player)) return;
        if (!mc.player.getMainHandItem().is(GunCompat.MELEE)) return;
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return;
        if (!BlockLedger.isGlass(mc.level.getBlockState(bh.getBlockPos()))) return;
        long now = System.currentTimeMillis();
        if (now - lastSent < 150) return;
        lastSent = now;
        PGNetwork.sendToServer(new MeleeGlassPacket(bh.getBlockPos(), heavy));
    }
}
