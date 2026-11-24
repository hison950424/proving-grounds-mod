package com.provinggrounds.mixin;

import com.provinggrounds.core.BlockProtection;
import com.provinggrounds.data.BlockLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 檔案: LevelSetBlockMixin.java
 * 目的: 世界裡任何方塊被改之前：
 *   1. BlockProtection：大逃殺以外不能被子彈打破、不能放火 → 直接取消這次改動（回傳 false）。
 *   2. BlockLedger（方塊還原）：記下原本的樣子。
 *   原版所有改方塊的路徑（玩家破壞、放置、爆炸、液體、火、掉落方塊、活塞、其他模組）最後都會呼叫
 *   Level.setBlock(pos, state, flags, depth)，所以攔這一個就全部抓得到。
 *   沒有比賽時兩個檢查第一行就返回，幾乎不花時間。
 *   方法名稱同時寫開發環境名稱與 SRG 名稱（setBlock = m_6933_），不需要 refmap。
 */
@Mixin(Level.class)
public abstract class LevelSetBlockMixin {

    @Inject(method = {
        "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void provinggrounds$beforeSetBlock(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        if (BlockProtection.blocks(self, pos, state)) { cir.setReturnValue(false); return; }
        if (BlockLedger.recording()) BlockLedger.beforeSet(self, pos, state);
    }
}
