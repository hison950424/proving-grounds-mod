package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 檔案: UiWalk.java
 * 目的: 開著任何介面時仍可用前後左右移動鍵走動（計畫書 A11，只在客戶端執行）。
 *   原版開啟畫面時會放開所有按鍵，角色停住。這裡在每個客戶端 tick 開頭，直接讀鍵盤（或滑鼠鍵）的實際狀態，
 *   把「前、後、左、右」四個移動鍵（使用玩家自己的按鍵設定）還給遊戲。Shift、空白鍵、Ctrl、疾跑不接管。
 *   不接管的情況：輸入框正在輸入（聊天、告示牌、鐵砧改名、創造搜尋…）、按鍵設定畫面、死亡／載入／斷線畫面、
 *   設定檔 ui_walk_excluded_screens 列出的畫面、ui_walk_enabled 關閉。
 *   倒地、跳傘等移動規則照原本的邏輯處理（這裡只是把按鍵狀態還回去，不改移動規則）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class UiWalk {

    private static boolean active; // 上一 tick 是否有接管（離開時要把按鍵放開）

    private UiWalk() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        KeyMapping[] moves = { mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight };
        if (mc.player == null || screen == null || !shouldWalk(screen)) {
            if (active && screen != null) for (KeyMapping k : moves) k.setDown(false); // 剛停止接管：放開，避免一直往前走
            active = false;
            return;
        }
        long window = mc.getWindow().getWindow();
        for (KeyMapping k : moves) k.setDown(isPhysicallyDown(window, k.getKey()));
        active = true;
    }

    private static boolean isPhysicallyDown(long window, InputConstants.Key key) {
        if (key == InputConstants.UNKNOWN) return false;
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    private static boolean shouldWalk(Screen screen) {
        try {
            if (!PGClientConfig.CLIENT.uiWalkEnabled.get()) return false;
            List<? extends String> excluded = PGClientConfig.CLIENT.uiWalkExcludedScreens.get();
            String full = screen.getClass().getName(), simple = screen.getClass().getSimpleName();
            if (excluded.contains(full) || excluded.contains(simple)) return false;
        } catch (IllegalStateException e) {
            return false; // 設定還沒載入
        }
        if (screen instanceof ChatScreen || screen instanceof KeyBindsScreen || screen instanceof DeathScreen
            || screen instanceof ReceivingLevelScreen || screen instanceof ConnectScreen || screen instanceof DisconnectedScreen
            || screen instanceof ProgressScreen || screen instanceof LevelLoadingScreen || screen instanceof GenericDirtMessageScreen
            // 告示牌、書本編輯不是用輸入框元件，但一定在打字
            || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
            || screen instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen) {
            return false;
        }
        return !hasActiveTextField(screen.children());
    }

    /** 畫面上有沒有正在輸入的輸入框（包含巢狀元件）。 */
    private static boolean hasActiveTextField(List<? extends GuiEventListener> children) {
        for (GuiEventListener c : children) {
            if (c instanceof EditBox box && box.isFocused() && box.isVisible()) return true;
            if (c instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested
                && hasActiveTextField(nested.children())) return true;
        }
        return false;
    }
}
