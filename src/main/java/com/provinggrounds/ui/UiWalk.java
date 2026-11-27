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
 *   設定檔 ui_walk_excluded_screens 列出的畫面、客戶端 enabled_v2 關閉、伺服器 ui_walk_allowed_v2 關閉。
 *   /pgwalk 會在聊天欄說明目前會不會接管、不接管的原因。
 *   倒地、跳傘等移動規則照原本的邏輯處理（這裡只是把按鍵狀態還回去，不改移動規則）。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class UiWalk {

    private static boolean active; // 這一 tick 是否接管
    /** 這一 tick 實際按著的前、後、左、右、跳躍、蹲下與靜走。 */
    private static boolean up, down, left, right, jump, sneak, quiet;
    private static boolean proneWasDown;
    private static long lastUiProneMs;

    private UiWalk() {}

    /** 開著介面時是否正按著「靜走」鍵（供 ClientMovement 判斷）。 */
    public static boolean isQuietDown() {
        return active && quiet;
    }

    /** 目前畫面是否處於打字狀態（供 WindowsImeLock 判斷是否放行中文輸入法）。 */
    public static boolean isTypingScreen(@javax.annotation.Nullable Screen screen) {
        if (screen == null) return false;
        if (screen instanceof ChatScreen) return true;
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
            || screen instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen) return true;
        return hasActiveTextField(screen.children());
    }

    /**
     * 每 tick 開頭讀實際按鍵狀態。注意：Forge 把移動鍵設成「只在遊戲中有效」（KeyConflictContext.IN_GAME），
     * 開著畫面時 KeyMapping.isDown() 一律是 false，所以不能只靠 setDown；改在 MovementInputUpdateEvent 直接寫進移動輸入（計畫書 6 A12）。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (mc.player == null || screen == null || !shouldWalk(screen)) {
            active = false;
            sneak = false;
            quiet = false;
            proneWasDown = false;
            return;
        }
        long window = mc.getWindow().getWindow();
        up = isPhysicallyDown(window, mc.options.keyUp.getKey());
        down = isPhysicallyDown(window, mc.options.keyDown.getKey());
        left = isPhysicallyDown(window, mc.options.keyLeft.getKey());
        right = isPhysicallyDown(window, mc.options.keyRight.getKey());
        jump = isPhysicallyDown(window, mc.options.keyJump.getKey());
        sneak = isPhysicallyDown(window, mc.options.keyShift.getKey());
        quiet = isPhysicallyDown(window, ClientMovement.QUIET_WALK.getKey());
        // 開著介面時按趴下鍵（邊緣觸發，避免長按重複切換）
        boolean proneDown = isPhysicallyDown(window, ClientModEvents.PRONE.getKey());
        if (proneDown && !proneWasDown) {
            long now = System.currentTimeMillis();
            if (now - lastUiProneMs >= 250) {
                lastUiProneMs = now;
                com.provinggrounds.network.PGNetwork.sendToServer(new com.provinggrounds.network.ProneTogglePacket());
            }
        }
        proneWasDown = proneDown;
        active = true;
    }

    /** 原版算完移動輸入之後（開著畫面時是全部放開），把實際按著的方向鍵、跳躍鍵與蹲下鍵寫回去。 */
    @SubscribeEvent
    public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.screen == null || event.getEntity() != mc.player) return;
        net.minecraft.client.player.Input in = event.getInput();
        in.up = up;
        in.down = down;
        in.left = left;
        in.right = right;
        in.jumping = jump;
        in.shiftKeyDown = sneak;
        float slow = (sneak || mc.player.isMovingSlowly()) ? 0.3f : 1f; // 蹲下、趴下時跟原版一樣變慢
        in.forwardImpulse = (up == down ? 0f : up ? 1f : -1f) * slow;
        in.leftImpulse = (left == right ? 0f : left ? 1f : -1f) * slow;
    }

    private static boolean isPhysicallyDown(long window, InputConstants.Key key) {
        if (key == InputConstants.UNKNOWN) return false;
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    // 最近一個「不是聊天」的畫面的判斷結果（/pgwalk 用：打指令時開著的是聊天畫面，要回報的是你剛才開的那個畫面）
    private static String lastScreen = "（還沒開過任何畫面）";
    private static String lastReason = null;

    /** 伺服器設定是否已同步到客戶端。 */
    static boolean serverConfigSynced() {
        try {
            com.provinggrounds.config.PGConfig.SERVER.uiWalkAllowed.get();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * 伺服器是否允許開著介面移動（伺服器設定 [lobby] ui_walk_allowed_v2，會同步到客戶端）。
     * 還沒同步時視為允許：剛進伺服器那一下不會卡住，同步後再照伺服器的設定。
     */
    static boolean serverAllows() {
        try {
            return com.provinggrounds.config.PGConfig.SERVER.uiWalkAllowed.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    static boolean clientEnabled() {
        try {
            return PGClientConfig.CLIENT.uiWalkEnabled.get();
        } catch (IllegalStateException e) {
            return true; // 客戶端設定還沒載入：用預設（開）
        }
    }

    private static boolean shouldWalk(Screen screen) {
        String reason = whyNot(screen);
        if (!(screen instanceof ChatScreen)) {
            lastScreen = screen.getClass().getName();
            lastReason = reason;
        }
        return reason == null;
    }

    /** 不接管移動鍵的原因；會接管時回傳 null。 */
    static String whyNot(Screen screen) {
        if (!clientEnabled()) return "客戶端設定 enabled_v2 是關閉的（config/provinggrounds-client.toml）";
        if (!serverAllows()) return "伺服器不允許（伺服器設定 ui_walk_allowed_v2 = false）";
        try {
            List<? extends String> excluded = PGClientConfig.CLIENT.uiWalkExcludedScreens.get();
            String full = screen.getClass().getName(), simple = screen.getClass().getSimpleName();
            if (excluded.contains(full) || excluded.contains(simple)) return "這個畫面在排除清單 ui_walk_excluded_screens 裡";
        } catch (IllegalStateException ignored) {
            // 設定還沒載入：沒有排除清單
        }
        if (screen instanceof ChatScreen) return "聊天畫面（正在打字）";
        if (screen instanceof KeyBindsScreen) return "按鍵設定畫面";
        if (screen instanceof DeathScreen || screen instanceof ReceivingLevelScreen || screen instanceof ConnectScreen
            || screen instanceof DisconnectedScreen || screen instanceof ProgressScreen || screen instanceof LevelLoadingScreen
            || screen instanceof GenericDirtMessageScreen) return "死亡／載入／連線畫面";
        // 告示牌、書本編輯不是用輸入框元件，但一定在打字
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
            || screen instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen) return "告示牌／書本編輯（正在打字）";
        if (hasActiveTextField(screen.children())) return "有輸入框正在輸入";
        return null;
    }

    /** /pgwalk：把目前狀態印在聊天欄（只給自己看）。 */
    public static List<String> diagnose() {
        List<String> out = new java.util.ArrayList<>();
        out.add("── 介面中移動（/pgwalk）──");
        out.add("客戶端設定 enabled_v2：" + (clientEnabled() ? "開" : "關"));
        out.add("伺服器設定 ui_walk_allowed_v2：" + (serverConfigSynced() ? (serverAllows() ? "允許" : "不允許") : "還沒同步（視為允許）"));
        out.add("最近開的畫面：" + lastScreen);
        out.add(lastReason == null ? "結論：開著這個畫面時會接管移動鍵，可以走。" : "結論：不會接管移動鍵，原因：" + lastReason);
        return out;
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
