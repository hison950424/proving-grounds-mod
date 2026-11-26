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
    private static Method fromLiving, getDrawCoolDown, cancelReload, fromLocal, isAim, aim, shoot, drawGun;
    private static KeyMapping aimKey;

    private ClientGunAim() {}

    /** 走火詛咒（計畫書 9）：客戶端強制開火（透過 TACZ 客戶端操作介面反射呼叫，不在 compat/ 以外引用 TACZ）。 */
    public static void forceShoot(LocalPlayer p) {
        if (p == null || !init() || shoot == null) return;
        try {
            Object cop = fromLocal.invoke(null, p);
            if (cop != null) shoot.invoke(cop);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    /** 開始翻爬時取消開鏡與換彈（避免攀爬期間還維持開鏡視角或換彈讀條）。 */
    public static void cancelForClimb(LocalPlayer p) {
        if (p == null || !init()) return;
        try {
            Object cop = fromLocal.invoke(null, p);
            if (cop != null) aim.invoke(cop, false);
            Object op = fromLiving.invoke(null, p);
            if (op != null && cancelReload != null) cancelReload.invoke(op);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    /** 爬完牆重新拿出主手槍枝時播放 TACZ 切槍舉起動畫。 */
    public static void triggerDraw(LocalPlayer p) {
        if (p == null || !init() || drawGun == null) return;
        try {
            Object cop = fromLocal.invoke(null, p);
            if (cop != null) drawGun.invoke(cop, net.minecraft.world.item.ItemStack.EMPTY);
            reInitGunRenderer(p);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static Class<?> animateRendererClass;
    private static Method tryInitRenderer;
    private static boolean rendererApiFailed;

    private static void reInitGunRenderer(LocalPlayer p) {
        if (rendererApiFailed) return;
        net.minecraft.world.item.ItemStack stack = p.getMainHandItem();
        if (stack.isEmpty()) return;
        try {
            if (animateRendererClass == null) {
                animateRendererClass = Class.forName("com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer");
                tryInitRenderer = animateRendererClass.getMethod("tryInit",
                    net.minecraft.world.item.ItemStack.class, net.minecraft.world.entity.player.Player.class, float.class);
            }
            var ext = net.minecraftforge.client.extensions.common.IClientItemExtensions.of(stack.getItem());
            Object renderer = ext.getCustomRenderer();
            if (animateRendererClass.isInstance(renderer)) {
                tryInitRenderer.invoke(renderer, stack, p, Minecraft.getInstance().getFrameTime());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            rendererApiFailed = true;
        }
    }

    private static boolean init() {
        if (ready) return true;
        if (failed || !ModList.get().isLoaded("tacz")) { failed = true; return false; }
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            fromLiving = op.getMethod("fromLivingEntity", LivingEntity.class);
            getDrawCoolDown = op.getMethod("getSynDrawCoolDown");
            cancelReload = op.getMethod("cancelReload");
            Class<?> cop = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            fromLocal = cop.getMethod("fromLocalPlayer", LocalPlayer.class);
            isAim = cop.getMethod("isAim");
            aim = cop.getMethod("aim", boolean.class);
            shoot = cop.getMethod("shoot");
            drawGun = cop.getMethod("draw", net.minecraft.world.item.ItemStack.class);
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

    /** 1. 滑鼠開鏡鍵：切槍中或攀爬中直接取消，TACZ 收不到。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || event.getAction() != GLFW.GLFW_PRESS || !init()) return;
        InputConstants.Key key = aimKey.getKey();
        if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != event.getButton()) return;
        if (drawing(p) || com.provinggrounds.core.WallClimb.isClientClimbing(p.getId())) event.setCanceled(true);
    }

    /** 2、3. 每個 tick：切槍中或攀爬中就關掉開鏡；切槍結束時還按著開鏡鍵就開鏡。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !init()) return;
        boolean draw = drawing(p);
        boolean climbing = com.provinggrounds.core.WallClimb.isClientClimbing(p.getId());
        try {
            Object cop = fromLocal.invoke(null, p);
            if ((draw || climbing) && Boolean.TRUE.equals(isAim.invoke(cop))) aim.invoke(cop, false);
            if (!climbing && wasDrawing && !draw && mc.screen == null && physicallyDown(mc, aimKey.getKey())) {
                aimKey.setDown(true); // 一直按著右鍵：動畫跑完就開鏡（按住模式）
                aim.invoke(cop, true);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 取不到就不處理
        }
        wasDrawing = draw;
    }

    /** 現在是不是正在開鏡（互動提示在開鏡時不顯示）。 */
    public static boolean aiming() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !init()) return false;
        try {
            return Boolean.TRUE.equals(isAim.invoke(fromLocal.invoke(null, mc.player)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 點狀準心（計畫書 5 的 B4）：拿槍時的準星由 TACZ 畫（含命中標記），所以把 TACZ 的準星種類設成 DOT_1。
     * 只在第一次套用；之後玩家在 TACZ 設定改成別的會保留。crosshair_dot 關掉時不動 TACZ。
     */
    /** 隱藏 TACZ 持槍時畫面中間的「按 [鍵] 互動」字樣（我們有自己的 F 互動提示）。每次啟動都設定。 */
    public static void hideTaczInteractText() {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Object value = Class.forName("com.tacz.guns.config.client.RenderConfig").getField("DISABLE_INTERACT_HUD_TEXT").get(null);
            if (Boolean.TRUE.equals(value.getClass().getMethod("get").invoke(value))) return;
            value.getClass().getMethod("set", Object.class).invoke(value, Boolean.TRUE);
            value.getClass().getMethod("save").invoke(value);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            ProvingGroundsMod.LOGGER.warn("Could not hide TACZ interact text: {}", e.toString());
        }
    }

    /**
     * 拿槍時的點狀準心改由本模組自己畫（大小、黑框跟空手一致，見 ClientInteract.drawDot）：
     * 把 TACZ 的準星種類設成 EMPTY（不畫準星，命中標記照常）。只做一次（crosshair_dot_tacz_applied_v2）：
     * TACZ 目前是 DOT_1（舊版設的）或還沒被舊版設定過才改；玩家自己選了別的準星就不動。
     */
    public static void applyDotCrosshairOnce() {
        var cfg = com.provinggrounds.config.PGClientConfig.CLIENT;
        if (!cfg.crosshairDot.get() || cfg.crosshairDotTaczV2Applied.get() || !ModList.get().isLoaded("tacz")) return;
        try {
            Object value = Class.forName("com.tacz.guns.config.client.RenderConfig").getField("CROSSHAIR_TYPE").get(null);
            Class<?> type = Class.forName("com.tacz.guns.client.renderer.crosshair.CrosshairType");
            Object now = value.getClass().getMethod("get").invoke(value);
            String name = now instanceof Enum<?> e ? e.name() : "";
            if (name.equals("DOT_1") || !cfg.crosshairDotTaczApplied.get()) {
                value.getClass().getMethod("set", Object.class).invoke(value, type.getField("EMPTY").get(null));
                value.getClass().getMethod("save").invoke(value);
                ProvingGroundsMod.LOGGER.info("Set TACZ crosshair to EMPTY (Proving Grounds draws the dot).");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            ProvingGroundsMod.LOGGER.warn("Could not set TACZ crosshair: {}", e.toString());
        }
        cfg.crosshairDotTaczApplied.set(true);
        cfg.crosshairDotTaczApplied.save();
        cfg.crosshairDotTaczV2Applied.set(true);
        cfg.crosshairDotTaczV2Applied.save();
    }

    private static Object crosshairValue;
    private static boolean crosshairFailed;

    /** TACZ 的準星種類目前是不是 EMPTY（是的話拿槍時由本模組畫點狀準心）。 */
    public static boolean taczCrosshairEmpty() {
        if (crosshairFailed || !ModList.get().isLoaded("tacz")) return false;
        try {
            if (crosshairValue == null) crosshairValue = Class.forName("com.tacz.guns.config.client.RenderConfig").getField("CROSSHAIR_TYPE").get(null);
            Object now = ((net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?>) crosshairValue).get();
            return now instanceof Enum<?> e && e.name().equals("EMPTY");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            crosshairFailed = true;
            return false;
        }
    }

    private static boolean physicallyDown(Minecraft mc, InputConstants.Key key) {

        long win = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(win, key.getValue());
        return false;
    }
}
