package com.provinggrounds.core;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * 檔案: BlockProtection.java
 * 目的: 只有大逃殺會破壞方塊（伺服器端）。
 *   允許破壞：大逃殺進行中（準備、飛機、對戰、結算）而且位置在這場地圖的圓圈外加 64 格內。
 *   其他地方（大廳、迷你死鬥等場地、團隊死鬥／佔點／槍王之王）：
 *     1. 爆炸（RPG 等爆炸彈、手榴彈、TNT、苦力怕…）：ExplosionEvent.Detonate 時把要被炸掉的方塊清單清空，
 *        爆炸傷害與擊退照舊，只是不破壞方塊（爆炸產生的火也一起取消，因為火只會出現在被炸掉的位置）。
 *     2. 子彈打破玻璃（TACZ 的 DestroyGlassBlock）：子彈打到方塊的事件期間，LevelSetBlockMixin 擋掉那一格的改動。
 *     3. 放火（燃燒彈、打火石）：不能放火方塊，所以不會燒掉木頭。燃燒瓶是範圍效果雲，不是火方塊，照常有效。
 *   管理員在創造模式手動蓋、拆方塊不受影響。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class BlockProtection {

    /** 子彈打到方塊的事件期間，這一格不能被改（TACZ 打破玻璃）。只在伺服器主執行緒使用。 */
    @Nullable private static BlockPos guarded;

    private BlockProtection() {}

    /** 這個位置現在可不可以被爆炸、子彈、火破壞。 */
    public static boolean allowsDamage(Level level, BlockPos pos) {
        if (level.isClientSide) return true;
        if (level.dimension() != Level.OVERWORLD) return true; // 其他維度（地獄、終界）不管
        return BattleRoyaleMode.get().allowsBlockDamage(pos);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        int before = event.getAffectedBlocks().size();
        event.getAffectedBlocks().removeIf(pos -> !allowsDamage(level, pos));
        if (before > 0 && event.getAffectedBlocks().isEmpty()) ProvingGroundsMod.LOGGER.debug("Explosion block damage blocked ({} blocks).", before);
    }

    // ── 子彈（GunCompat 呼叫）──

    public static void beginBulletHit(Level level, BlockPos pos) {
        guarded = allowsDamage(level, pos) ? null : pos.immutable();
    }

    public static void endBulletHit() {
        guarded = null;
    }

    /** LevelSetBlockMixin：這次改方塊要不要擋掉。 */
    public static boolean blocks(Level level, BlockPos pos, BlockState newState) {
        if (level.isClientSide) return false;
        BlockPos g = guarded;
        if (g != null && g.equals(pos)) return true;
        return newState.getBlock() instanceof BaseFireBlock && !allowsDamage(level, pos);
    }
}
