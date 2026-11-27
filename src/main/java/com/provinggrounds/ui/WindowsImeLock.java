package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGClientConfig;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.Locale;

/**
 * 檔案: WindowsImeLock.java
 * 目的: 在 Windows 上，除了聊天、告示牌、書本與有聚焦輸入框正在打字的時候，
 *       其餘時候自動關閉並停用遊戲視窗的 IME（強制維持英文輸入法），避免玩家在移動或按 Shift/Ctrl 時誤觸輸入法切換。
 *       可透過 config/provinggrounds-client.toml 的 [gui] ime_lock_enabled_v1 開關（預設開）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class WindowsImeLock {

    private static final int IACE_DEFAULT = 0x0010;
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
        .toLowerCase(Locale.ROOT).contains("win");

    private interface Imm32 extends StdCallLibrary {
        Pointer ImmGetContext(Pointer hWnd);
        boolean ImmReleaseContext(Pointer hWnd, Pointer hIMC);
        boolean ImmGetOpenStatus(Pointer hIMC);
        boolean ImmSetOpenStatus(Pointer hIMC, boolean fOpen);
        boolean ImmAssociateContextEx(Pointer hWnd, Pointer hIMC, int dwFlags);
    }

    private static Imm32 imm32;
    private static boolean initAttempted;
    private static boolean unsupported;
    private static boolean imeDisabled;
    private static int checkTick;

    private WindowsImeLock() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !IS_WINDOWS || unsupported) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        boolean enabled = isEnabled();
        boolean typing = !enabled || UiWalk.isTypingScreen(mc.screen);

        if (typing) {
            if (imeDisabled) {
                setImeDisabled(mc, false);
            }
            return;
        }

        // 非打字狀態：初次停用，並每隔 20 tick 檢查一次避免外部快速鍵重新喚醒 IME
        checkTick++;
        if (!imeDisabled || checkTick >= 20) {
            checkTick = 0;
            setImeDisabled(mc, true);
        }
    }

    private static boolean isEnabled() {
        try {
            return PGClientConfig.CLIENT.imeLockEnabled.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static void setImeDisabled(Minecraft mc, boolean disable) {
        Imm32 lib = loadImm32();
        if (lib == null) return;
        try {
            long glfwWin = mc.getWindow().getWindow();
            if (glfwWin == 0L) return;
            long hwndVal = GLFWNativeWin32.glfwGetWin32Window(glfwWin);
            if (hwndVal == 0L) return;
            Pointer hwnd = new Pointer(hwndVal);
            if (disable) {
                Pointer himc = lib.ImmGetContext(hwnd);
                if (himc != null) {
                    if (lib.ImmGetOpenStatus(himc)) {
                        lib.ImmSetOpenStatus(himc, false);
                    }
                    lib.ImmReleaseContext(hwnd, himc);
                }
                lib.ImmAssociateContextEx(hwnd, null, 0);
                imeDisabled = true;
            } else {
                lib.ImmAssociateContextEx(hwnd, null, IACE_DEFAULT);
                imeDisabled = false;
            }
        } catch (Throwable t) {
            unsupported = true;
            ProvingGroundsMod.LOGGER.debug("WindowsImeLock disabled due to native call error: {}", t.toString());
        }
    }

    private static Imm32 loadImm32() {
        if (initAttempted) return imm32;
        initAttempted = true;
        try {
            imm32 = Native.load("imm32", Imm32.class);
        } catch (Throwable t) {
            unsupported = true;
            ProvingGroundsMod.LOGGER.debug("WindowsImeLock unavailable (imm32 load failed): {}", t.toString());
        }
        return imm32;
    }
}
