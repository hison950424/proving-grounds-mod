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
 *   大逃殺範圍內的爆炸：大廳「炸毀方塊」開著時會炸掉方塊（TACZ、lrtactical 自己的設定是不破壞，這裡逐次改成破壞），
 *   但有方塊實體的方塊（箱子、祭壇、墓碑…）不炸、被炸掉的方塊不掉落；比賽結束由 BlockLedger 還原。
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

    // ── 爆炸 ──

    /** 大廳設定「爆炸破壞方塊」（伺服器設定 explosion_block_damage_v1）。 */
    public static boolean explosionBlockDamageOn() {
        try { return com.provinggrounds.config.PGConfig.SERVER.brExplosionBlockDamage.get(); }
        catch (IllegalStateException e) { return true; }
    }

    /** 爆炸可不可以炸掉這一格：大逃殺範圍內、大廳開關開著。其他維度照原版。 */
    private static boolean allowsExplosion(Level level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return true;
        return explosionBlockDamageOn() && allowsDamage(level, pos);
    }

    private static java.lang.reflect.Field interactionField;
    private static boolean interactionFailed;
    /** 這個 tick 被爆炸炸掉的方塊（炸掉時掉出來的物品要取消，避免滿地方塊；每 tick 結束清空）。 */
    private static final java.util.Set<BlockPos> EXPLODED = new java.util.HashSet<>();

    /**
     * 爆炸開始：TACZ 爆炸彈與 lrtactical 手榴彈在它們自己的設定裡被設成「不破壞方塊」（KEEP），
     * 大逃殺範圍內而且大廳開關開著時，把這一次爆炸改成會破壞方塊（不改它們的設定檔，其他地方照舊不破壞）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        if (level.isClientSide || interactionFailed) return;
        net.minecraft.world.level.Explosion ex = event.getExplosion();
        if (level.dimension() != Level.OVERWORLD || !allowsExplosion(level, BlockPos.containing(ex.getPosition()))) return;
        try {
            if (interactionField == null) {
                interactionField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.world.level.Explosion.class, "f_46010_");
            }
            if (interactionField.get(ex) == net.minecraft.world.level.Explosion.BlockInteraction.KEEP) {
                interactionField.set(ex, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            interactionFailed = true;
            ProvingGroundsMod.LOGGER.warn("Explosion block damage unavailable: {}", e.toString());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        int before = event.getAffectedBlocks().size();
        // 不能炸的：大逃殺範圍外、大廳關掉；箱子、祭壇、墓碑等有內容的方塊（方塊實體）也不炸，避免物資噴出來或祭壇消失
        event.getAffectedBlocks().removeIf(pos -> !allowsExplosion(level, pos)
            || (level.dimension() == Level.OVERWORLD && level.getBlockEntity(pos) != null));
        if (level.dimension() == Level.OVERWORLD) {
            for (BlockPos pos : event.getAffectedBlocks()) if (!level.getBlockState(pos).isAir()) EXPLODED.add(pos.immutable());
        }
        if (before > 0 && event.getAffectedBlocks().isEmpty()) ProvingGroundsMod.LOGGER.debug("Explosion block damage blocked ({} blocks).", before);
    }

    /** 被炸掉的方塊不掉落（地圖比賽結束會由 BlockLedger 自動還原，掉一地方塊只會亂）。 */
    @SubscribeEvent
    public static void onItemJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (EXPLODED.isEmpty() || event.loadedFromDisk() || !(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)) return;
        if (EXPLODED.contains(BlockPos.containing(item.position()))) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && !EXPLODED.isEmpty()) EXPLODED.clear();
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
