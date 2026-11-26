package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.data.BlockLedger;
import com.provinggrounds.network.MeleeGlassPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
 * 目的: 非 lrtactical 的近戰武器（後備路徑）左／右鍵打玻璃／玻璃片／鐵欄杆時送 MeleeGlassPacket 給伺服器打破。
 *   lrtactical 刀械改由伺服器端 MeleeGlassMixin 在各自真正的揮刀命中幀打破，避免未完成拔刀或揮刀前就提早破玻璃。
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
        ItemStack held = mc.player.getMainHandItem();
        if (!held.is(GunCompat.MELEE) || GunCompat.isLrtacticalMelee(held)) return;
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return;
        if (!BlockLedger.isGlass(mc.level.getBlockState(bh.getBlockPos()))) return;
        long now = System.currentTimeMillis();
        if (now - lastSent < 150) return;
        lastSent = now;
        PGNetwork.sendToServer(new MeleeGlassPacket(bh.getBlockPos(), heavy));
    }
}
