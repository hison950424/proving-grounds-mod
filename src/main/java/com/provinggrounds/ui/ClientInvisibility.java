package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ClientInvisibility.java
 * 目的: 隱形與視角（只在客戶端，計畫書 4 的 A2、A5）。
 *   - 隱形的玩家：原版只把身體變透明，護甲和手上的槍還看得到。這裡對「看不到他的人」（不是隊友）整個不畫，
 *     所以護甲、手持物品、插在身上的箭都看不到。隊友（隊伍設定 seeFriendlyInvisibles）照原版看到半透明的人和裝備。
 *     腳步聲照常（聲音不經過這裡）；跑步粒子由 EntitySprintMixin 擋掉。
 *   - 飛機上的玩家（坐在飛機座位 ItemDisplay 上）：所有人都看不到他的名條，跳傘後才看得到隊友名條。
 *   - 視角：飛機上所有人強制第三人稱；其他任何時候，非管理員（權限等級 < 2）固定第一人稱，按 F5 沒反應。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientInvisibility {

    private ClientInvisibility() {}

    /** 這一刻是不是我們把視角強制改成第三人稱（離開飛機時要改回第一人稱）。 */
    private static boolean forcedThird;

    @SubscribeEvent
    public static void onRenderPlayer(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player target)) return;
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null || target == me) return;
        if (target.isInvisible() && target.isInvisibleTo(me)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player target && target.getVehicle() instanceof Display) {
            event.setResult(Event.Result.DENY); // 飛機上的人不顯示名條
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { forcedThird = false; return; }
        if (ClientBRState.get().selfState() == ClientBRState.SELF_PLANE) {
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            forcedThird = true;
            return;
        }
        if (forcedThird) {
            forcedThird = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON); // 跳傘後回到第一人稱（管理員之後可以自己再切）
            return;
        }
        if (!p.hasPermissions(2) && mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }
}
