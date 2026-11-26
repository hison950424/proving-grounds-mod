package com.provinggrounds.core;

import com.provinggrounds.config.PGConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: Footsteps.java
 * 目的: 移動方式與腳步聲（所有模式通用，兩端共用）。
 *   靜走：玩家按住「靜走」鍵（預設 Shift）時，客戶端放慢移動（ClientMovement），並告訴伺服器（QuietWalkPacket）。
 *   腳步聲（EntitySpecialMixin 攔截原版 playStepSound 後呼叫這裡）：
 *     音量 = 原版 × 模式倍率（奔跑 step_volume_sprint、一般 step_volume_walk、靜走 step_volume_quiet）× 材質倍率；
 *     音量大於 1 時原版會讓聲音傳得更遠（約 16 格 × 音量）。
 *     材質：金屬、玻璃比較響、音調高；木頭低沉；草、沙、雪比較悶；羊毛幾乎沒聲音。差異大小用 step_material_contrast 調。
 *   玩家自己聽到的與別人聽到的一樣（原版的播法：本人在客戶端播，伺服器播給其他人）。
 */
public final class Footsteps {

    /** 伺服器：正在靜走的玩家。 */
    private static final Set<UUID> QUIET = ConcurrentHashMap.newKeySet();
    /** 客戶端：自己是否正在靜走（ClientMovement 每 tick 更新）。 */
    public static volatile boolean localQuiet;

    private Footsteps() {}

    public static void setQuiet(UUID id, boolean quiet) {
        if (quiet) QUIET.add(id); else QUIET.remove(id);
    }

    public static boolean quiet(Player p) {
        if (!p.isInWater() && p.getPose() == net.minecraft.world.entity.Pose.SWIMMING) return false;
        return p.level().isClientSide ? (p.isLocalPlayer() && localQuiet) : QUIET.contains(p.getUUID());
    }

    /**
     * 播放玩家的腳步聲（取代原版）。回傳 false = 交給原版處理（例如在液體裡）。
     */
    public static boolean play(Player p, BlockPos pos, BlockState state) {
        if (state.liquid()) return false;
        PGConfig c;
        double mode, contrast;
        try {
            c = PGConfig.SERVER;
            mode = p.isSprinting() ? c.stepVolumeSprint.get() : quiet(p) ? c.stepVolumeQuiet.get() : c.stepVolumeWalk.get();
            contrast = c.stepMaterialContrast.get();
        } catch (IllegalStateException e) {
            return false; // 設定還沒載入：原版
        }
        SoundType st = state.getSoundType(p.level(), pos, p);
        float[] m = material(st);
        float vol = (float) (st.getVolume() * 0.15f * mode * (1 + (m[0] - 1) * contrast));
        // 刺客：走路聲音降低 50%（伺服器端與客戶端自己都套用）
        if (isAssassin(p)) vol *= 0.5f;
        float pitch = (float) (st.getPitch() * (1 + (m[1] - 1) * contrast));
        if (vol <= 0.001f) return true;
        p.playSound(st.getStepSound(), vol, pitch);
        return true;
    }

    private static boolean isAssassin(Player p) {
        if (!p.level().isClientSide) {
            return p instanceof net.minecraft.server.level.ServerPlayer sp
                && com.provinggrounds.modes.arena.ClassCombatEvents.isClass(sp, com.provinggrounds.modes.arena.ArenaClass.ASSASSIN);
        }
        return p.isLocalPlayer()
            && com.provinggrounds.client.ClientArenaState.active()
            && com.provinggrounds.client.ClientArenaState.get().classes()
            && com.provinggrounds.client.ClientArenaState.get().classOrdinal() == com.provinggrounds.modes.arena.ArenaClass.ASSASSIN.ordinal();
    }

    /** 材質的 {音量倍率, 音調倍率}。 */
    private static float[] material(SoundType st) {
        if (st == SoundType.METAL || st == SoundType.ANVIL || st == SoundType.CHAIN || st == SoundType.COPPER
            || st == SoundType.NETHERITE_BLOCK || st == SoundType.LANTERN) return new float[] { 1.4f, 1.15f };
        if (st == SoundType.GLASS || st == SoundType.AMETHYST || st == SoundType.CALCITE) return new float[] { 1.3f, 1.3f };
        if (st == SoundType.WOOD || st == SoundType.BAMBOO_WOOD || st == SoundType.CHERRY_WOOD || st == SoundType.NETHER_WOOD
            || st == SoundType.LADDER || st == SoundType.SCAFFOLDING) return new float[] { 1.2f, 0.8f };
        if (st == SoundType.STONE || st == SoundType.DEEPSLATE || st == SoundType.DEEPSLATE_BRICKS || st == SoundType.POLISHED_DEEPSLATE
            || st == SoundType.TUFF || st == SoundType.NETHER_BRICKS || st == SoundType.MUD_BRICKS) return new float[] { 1.1f, 1.0f };
        if (st == SoundType.GRAVEL) return new float[] { 1.15f, 0.9f };
        if (st == SoundType.GRASS || st == SoundType.SAND || st == SoundType.SNOW || st == SoundType.POWDER_SNOW
            || st == SoundType.MOSS || st == SoundType.MOSS_CARPET || st == SoundType.ROOTED_DIRT || st == SoundType.MUD) return new float[] { 0.8f, 1.05f };
        if (st == SoundType.WOOL) return new float[] { 0.3f, 1.0f };
        return new float[] { 1f, 1f };
    }
}
