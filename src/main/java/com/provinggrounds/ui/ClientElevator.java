package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.network.ElevatorPacket;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientElevator.java
 * 目的: 電梯方塊的客戶端按鍵偵測（計畫書 7 A5，只在客戶端載入）。
 *   站在電梯方塊上、沒開任何畫面時，跳躍鍵或蹲下鍵「從沒按變成按下」的那一下送 ElevatorPacket 給伺服器；
 *   按住不放不會重複觸發，自動跳躍、走上台階也不會觸發（那些不是按鍵）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientElevator {

    private static boolean jumpWas, sneakWas;

    private ClientElevator() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean jump = mc.options.keyJump.isDown(), sneak = mc.options.keyShift.isDown();
        boolean jumpPressed = jump && !jumpWas, sneakPressed = sneak && !sneakWas;
        jumpWas = jump;
        sneakWas = sneak;
        if (p == null || mc.screen != null || p.isSpectator() || p.isPassenger()) return;
        if (!jumpPressed && !sneakPressed) return;
        if (!onElevator(p)) return;
        if (jumpPressed) PGNetwork.sendToServer(new ElevatorPacket(true));
        else PGNetwork.sendToServer(new ElevatorPacket(false));
    }

    /** 腳下（起跳那一下可能已經離地一點點）是電梯方塊。 */
    private static boolean onElevator(LocalPlayer p) {
        for (double dy : new double[] { 0.2, 0.5 }) {
            BlockPos pos = BlockPos.containing(p.getX(), p.getY() - dy, p.getZ());
            if (p.level().getBlockState(pos).is(PGBlocks.ELEVATOR.get()) && p.getY() - (pos.getY() + 1) < 0.6) return true;
        }
        return false;
    }
}
