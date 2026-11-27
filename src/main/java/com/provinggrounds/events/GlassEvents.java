package com.provinggrounds.events;

import com.provinggrounds.data.BlockLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 檔案: GlassEvents.java
 * 目的: 記錄被打破的玻璃（原版與模組，比賽外由 BlockLedger 記錄，每局開始時修回去；比賽中所有方塊由 LevelSetBlockMixin 記錄）。
 *   子彈打破：由槍械適配層（GunCompat）呼叫 onBulletHit。
 *   爆炸波及：ExplosionEvent.Detonate 在方塊被炸掉之前觸發，這時記下受影響的玻璃。
 */
public class GlassEvents {

    /** 子彈打到方塊（伺服器端，在 TACZ 打破玻璃之前）。 */
    public static void onBulletHit(Level level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel sl) {
            BlockLedger.get(sl.getServer()).recordGlass(sl, pos, state);
            if (BlockLedger.isGlass(state)) com.provinggrounds.modes.arena.ArenaManager.onGlassBroken(sl, pos, state);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockLedger ledger = BlockLedger.get(level.getServer());
        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState state = level.getBlockState(pos);
            if (BlockLedger.isGlass(state)) {
                ledger.recordGlass(level, pos, state);
                com.provinggrounds.modes.arena.ArenaManager.onGlassBroken(level, pos, state);
            }
        }
    }
}
