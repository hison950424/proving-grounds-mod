package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientBRState;
import com.provinggrounds.client.ClientProneData;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.core.Footsteps;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.QuietWalkPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 檔案: ClientMovement.java
 * 目的: 移動方式（只在客戶端）：
 *   - 自動奔跑：按方向鍵往前走就自動奔跑（伺服器設定 auto_sprint）。開槍、瞄準、使用物品、蹲下、靜走、趴下、倒地時不自動奔跑。
 *   - 靜走：按住「靜走」鍵（預設 Shift）慢慢走（速度 quiet_walk_speed），腳步聲比較小；開始／停止時告訴伺服器。
 *   - 蹲下用原版的「潛行」鍵（預設改成 Ctrl，ClientKeyDefaults 第一次開遊戲時套用）。原版「疾跑」鍵不再需要（不綁定）。
 *   按鍵都可以在 選項 → 控制 → 按鍵設定 修改。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientMovement {

    public static final KeyMapping QUIET_WALK = new KeyMapping("key.provinggrounds.quiet_walk", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, "key.categories.provinggrounds");

    private static boolean sentQuiet;
    private static KeyMapping taczAim;
    private static boolean aimLooked;

    private ClientMovement() {}

    private static boolean quietDown(Minecraft mc) {
        return mc.screen == null ? QUIET_WALK.isDown() : UiWalk.isQuietDown();
    }

    private static boolean isProne(LocalPlayer p) {
        return !p.isInWater() && (ClientProneData.isProne(p.getUUID())
            || ClientBRState.get().crawling().contains(p.getUUID())
            || p.getPose() == net.minecraft.world.entity.Pose.SWIMMING);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { Footsteps.localQuiet = false; sentQuiet = false; return; }
        // 趴下／倒地爬行時不能靜走
        boolean quiet = !isProne(p) && quietDown(mc);
        Footsteps.localQuiet = quiet;
        if (quiet != sentQuiet) {
            sentQuiet = quiet;
            PGNetwork.sendToServer(new QuietWalkPacket(quiet));
        }
    }

    /** 在原版算完輸入之後：趴下禁用蹲下／靜走、靜走放慢、自動奔跑。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getEntity() instanceof LocalPlayer p) || p != mc.player) return;
        var in = event.getInput();
        // 趴下時不可蹲下、不可靜走
        if (isProne(p)) {
            in.shiftKeyDown = false;
            if (p.isShiftKeyDown()) p.setShiftKeyDown(false);
            if (p.isSprinting()) p.setSprinting(false);
            return;
        }
        double quietSpeed = 0.6;
        boolean auto = true;
        try {
            quietSpeed = PGConfig.SERVER.quietWalkSpeed.get();
            auto = PGConfig.SERVER.autoSprint.get();
        } catch (IllegalStateException ignored) {
            // 還沒連上伺服器：用預設值
        }
        if (Footsteps.localQuiet) {
            in.forwardImpulse *= (float) quietSpeed;
            in.leftImpulse *= (float) quietSpeed;
            if (p.isSprinting()) p.setSprinting(false);
            return;
        }
        // 奔跑時開鏡強制切換回走路模式
        KeyMapping aim = taczAim();
        if ((aim != null && aim.isDown()) || com.provinggrounds.compat.ClientGunAim.aiming()) {
            if (p.isSprinting()) p.setSprinting(false);
            return;
        }
        if (!auto || in.forwardImpulse <= 1.0e-5f || in.shiftKeyDown || !canAutoSprint(mc, p)) return;
        if (!p.isSprinting()) p.setSprinting(true);
    }

    private static boolean canAutoSprint(Minecraft mc, LocalPlayer p) {
        if (p.isUsingItem() || p.isPassenger() || p.isFallFlying() || p.getAbilities().flying) return false;
        if (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()) return false; // 開槍、使用物品時不跑
        if (com.provinggrounds.compat.TaczInstantFire.recentlyShot()) return false; // 剛開槍 300ms 內維持擺正不切回歪槍奔跑
        KeyMapping aim = taczAim();
        if (aim != null && aim.isDown()) return false; // TACZ 瞄準時不跑
        if (ClientProneData.isProne(p.getUUID())) return false;
        int self = ClientBRState.get().selfState();
        if (self == ClientBRState.SELF_DOWNED) return false;
        return p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly;
    }

    /** TACZ 的瞄準鍵（沒裝 TACZ 時是 null；只找一次）。 */
    private static KeyMapping taczAim() {
        if (!aimLooked) {
            aimLooked = true;
            for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
                if ("key.tacz.aim.desc".equals(km.getName())) taczAim = km;
            }
        }
        return taczAim;
    }
}
