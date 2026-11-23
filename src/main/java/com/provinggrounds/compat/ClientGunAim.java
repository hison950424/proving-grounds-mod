package com.provinggrounds.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/**
 * 檔案: ClientGunAim.java
 * 目的: 切槍動畫跑完前不能開鏡（只在客戶端）。TACZ 原本在切槍動畫還沒跑完時按右鍵開鏡，會直接取消切槍的後搖。
 *   做法（用反射呼叫 TACZ，不在編譯期依賴）：
 *     1. 切槍冷卻中（IGunOperator.getSynDrawCoolDown() > 0）按下開鏡鍵（滑鼠）→ 在 TACZ 收到之前取消；
 *     2. 每個 tick 再檢查一次：切槍中已經在開鏡（例如鍵盤綁定、其他途徑）就關掉；
 *     3. 切槍動畫跑完時如果還按著開鏡鍵，自動開鏡（不用再按一次）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGunAim {

    private static boolean ready, failed, wasDrawing;
    private static Method fromLiving, getDrawCoolDown, fromLocal, isAim, aim;
    private static KeyMapping aimKey;

    private ClientGunAim() {}

    private static boolean init() {
        if (ready) return true;
        if (failed || !ModList.get().isLoaded("tacz")) { failed = true; return false; }
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            fromLiving = op.getMethod("fromLivingEntity", LivingEntity.class);
            getDrawCoolDown = op.getMethod("getSynDrawCoolDown");
            Class<?> cop = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            fromLocal = cop.getMethod("fromLocalPlayer", LocalPlayer.class);
            isAim = cop.getMethod("isAim");
            aim = cop.getMethod("aim", boolean.class);
            aimKey = (KeyMapping) Class.forName("com.tacz.guns.client.input.AimKey").getField("AIM_KEY").get(null);
            ready = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            ProvingGroundsMod.LOGGER.warn("TACZ draw-aim lock unavailable: {}", e.toString());
            return false;
        }
    }

    /** 切槍動畫還沒跑完。 */
    private static boolean drawing(LocalPlayer p) {
        try {
            Object op = fromLiving.invoke(null, p);
            return op != null && ((Number) getDrawCoolDown.invoke(op)).longValue() > 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** 1. 滑鼠開鏡鍵：切槍中直接取消，TACZ 收不到。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || event.getAction() != GLFW.GLFW_PRESS || !init()) return;
        InputConstants.Key key = aimKey.getKey();
        if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != event.getButton()) return;
        if (drawing(p)) event.setCanceled(true);
    }

    /** 2、3. 每個 tick：切槍中就關掉開鏡；切槍結束時還按著開鏡鍵就開鏡。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !init()) return;
        boolean draw = drawing(p);
        try {
            Object cop = fromLocal.invoke(null, p);
            if (draw && Boolean.TRUE.equals(isAim.invoke(cop))) aim.invoke(cop, false);
            if (wasDrawing && !draw && mc.screen == null && physicallyDown(mc, aimKey.getKey())) {
                aimKey.setDown(true); // 一直按著右鍵：動畫跑完就開鏡（按住模式）
                aim.invoke(cop, true);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 取不到就不處理
        }
        wasDrawing = draw;
    }

    private static boolean physicallyDown(Minecraft mc, InputConstants.Key key) {
        long win = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(win, key.getValue());
        return false;
    }
}
