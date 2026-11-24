package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.data.BlockLedger;
import com.provinggrounds.network.MeleeGlassPacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientMeleeGlass.java
 * 目的: 拿近戰武器左鍵打玻璃 → 送 MeleeGlassPacket 給伺服器打破（計畫書 6 A6；只在客戶端）。
 *   冒險模式不能挖方塊，原版不會送挖方塊的封包，所以在按下攻擊鍵時自己判斷準星指著的是不是玻璃。
 *   receiveCanceled：lrtactical 可能取消攻擊鍵事件改用自己的近戰動作，取消了也要收到。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientMeleeGlass {

    private static long lastSent;

    private ClientMeleeGlass() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.getMainHandItem().is(GunCompat.MELEE)) return;
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return;
        if (!BlockLedger.isGlass(mc.level.getBlockState(bh.getBlockPos()))) return;
        long now = System.currentTimeMillis();
        if (now - lastSent < 150) return; // 連點時不要灌封包
        lastSent = now;
        PGNetwork.sendToServer(new MeleeGlassPacket(bh.getBlockPos()));
    }
}
