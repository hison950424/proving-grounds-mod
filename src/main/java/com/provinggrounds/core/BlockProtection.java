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
 *   但有方塊實體的方塊（祭壇、墓碑…）不炸（物資箱可以炸，裡面的東西照常掉出、比賽結束不修復）、被炸掉的方塊不掉落；比賽結束由 BlockLedger 還原。
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

    public static final int BOOM_OFF = 0, BOOM_ALL = 1, BOOM_GUNS = 2, BOOM_THROWABLES = 3;

    /** 大廳設定「炸毀方塊」（伺服器設定 explosion_block_damage_mode_v1）：0 關、1 全部、2 只有槍械、3 只有投擲物。 */
    public static int explosionMode() {
        try { return com.provinggrounds.config.PGConfig.SERVER.brExplosionBlockDamage.get(); }
        catch (IllegalStateException e) { return BOOM_ALL; }
    }

    /**
     * 這次爆炸算哪一種（計畫書 6 D3）：TACZ 的子彈實體（爆炸彈、火箭、榴彈）= 槍械；lrtactical 的手榴彈爆炸 = 投擲物；
     * 其他（TNT、苦力怕、特殊物品…）只有「全部」時才炸毀方塊。只看類別名稱，不 import 槍械模組（建置規則）。
     */
    private static boolean explosionAllowedByMode(net.minecraft.world.level.Explosion ex) {
        int mode = explosionMode();
        if (mode == BOOM_ALL) return true;
        if (mode == BOOM_OFF) return false;
        net.minecraft.world.entity.Entity src = ex.getDirectSourceEntity();
        String srcName = src != null ? src.getClass().getName() : "";
        boolean gun = srcName.startsWith("com.tacz.");
        boolean throwable = ex.getClass().getName().startsWith("me.xjqsh.lrtactical.") || srcName.startsWith("me.xjqsh.lrtactical.");
        return mode == BOOM_GUNS ? gun : throwable;
    }

    /** 爆炸可不可以炸掉這一格：大逃殺範圍內、大廳設定允許這種爆炸。其他維度照原版。 */
    private static boolean allowsExplosion(Level level, BlockPos pos, net.minecraft.world.level.Explosion ex) {
        if (level.dimension() != Level.OVERWORLD) return true;
        return explosionAllowedByMode(ex) && allowsDamage(level, pos);
    }

    private static java.lang.reflect.Field interactionField;
    private static boolean interactionFailed;
    private static java.lang.reflect.Field destroyMultiplierField;
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
        if (level.dimension() != Level.OVERWORLD || !allowsExplosion(level, BlockPos.containing(ex.getPosition()), ex)) return;
        try {
            if (interactionField == null) {
                interactionField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.world.level.Explosion.class, "f_46010_");
            }
            if (interactionField.get(ex) == net.minecraft.world.level.Explosion.BlockInteraction.KEEP) {
                interactionField.set(ex, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
            }
            // lrtactical 的手榴彈爆炸（CustomExplosion）有自己的「破壞方塊威力倍率」，M67 設成 0 → 就算允許也炸不壞方塊（計畫書 6 A8）
            if (ex.getClass().getName().equals("me.xjqsh.lrtactical.util.CustomExplosion")) {
                if (destroyMultiplierField == null) {
                    destroyMultiplierField = ex.getClass().getDeclaredField("destroyMultiplier");
                    destroyMultiplierField.setAccessible(true);
                }
                if (destroyMultiplierField.getFloat(ex) <= 0f) destroyMultiplierField.setFloat(ex, 1f);
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
        // 物資箱可以被炸掉（裡面的東西照常噴出，比賽結束不修復物資箱，計畫書 6 A10）
        net.minecraft.world.level.Explosion ex = event.getExplosion();
        event.getAffectedBlocks().removeIf(pos -> !allowsExplosion(level, pos, ex)
            || (level.dimension() == Level.OVERWORLD && level.getBlockEntity(pos) != null && !isCrate(level, pos)));
        if (level.dimension() == Level.OVERWORLD) {
            for (BlockPos pos : event.getAffectedBlocks()) {
                if (!level.getBlockState(pos).isAir() && !isCrate(level, pos)) EXPLODED.add(pos.immutable());
            }
        }
        if (before > 0 && event.getAffectedBlocks().isEmpty()) ProvingGroundsMod.LOGGER.debug("Explosion block damage blocked ({} blocks).", before);
    }

    private static boolean isCrate(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof com.provinggrounds.block.CrateBlock;
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
