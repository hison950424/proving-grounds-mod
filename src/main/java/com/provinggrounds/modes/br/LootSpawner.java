package com.provinggrounds.modes.br;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.CrateBlock;
import com.provinggrounds.block.CrateBlockEntity;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 檔案: LootSpawner.java
 * 目的: 分批清理 / 刷新戰利品箱（每 tick 處理 points_per_tick 個點位，避免卡頓）。
 *       清理：取得方塊實體 wipe() 清空（不觸發 loot 生成）→ setBlock(AIR)，不產生掉落物。
 *       刷新：每個點位先擲是否生成，再依權重擲階級，用 setLootTable 設定，玩家第一次打開才生成內容。
 *       點位處理完後掃描一次地圖範圍，discard() 殘留的掉落物。
 */
public class LootSpawner {

    public enum Mode { SPAWN, CLEAR }

    public static final ResourceLocation TABLE_LOW     = new ResourceLocation("br", "crates/low");
    public static final ResourceLocation TABLE_MID     = new ResourceLocation("br", "crates/mid");
    public static final ResourceLocation TABLE_HIGH    = new ResourceLocation("br", "crates/high");
    public static final ResourceLocation TABLE_AIRDROP = new ResourceLocation("br", "crates/airdrop");

    private final ServerLevel level;
    private final MapRegistry.BRMap map;
    private final Mode mode;
    private final Deque<BlockPos> queue;
    private final RandomSource random;
    private boolean swept = false;

    private int spawnedLow, spawnedMid, spawnedHigh, removedItems;

    public LootSpawner(ServerLevel level, MapRegistry.BRMap map, Mode mode) {
        this.level = level;
        this.map = map;
        this.mode = mode;
        this.queue = new ArrayDeque<>(map.lootPoints);
        this.random = level.getRandom();
    }

    public boolean isDone() { return swept; }
    public int remaining()  { return queue.size(); }
    public int spawnedTotal() { return spawnedLow + spawnedMid + spawnedHigh; }

    /** 處理一批。全部完成時回傳 true。 */
    public boolean tick() {
        if (swept) return true;
        int budget = PGConfig.SERVER.lootPointsPerTick.get();
        while (budget-- > 0 && !queue.isEmpty()) {
            processPoint(queue.poll());
        }
        if (queue.isEmpty()) {
            sweepItems();
            swept = true;
            ProvingGroundsMod.LOGGER.info("Loot {} done on map {}: low={} mid={} high={} itemsRemoved={}",
                mode, map.id, spawnedLow, spawnedMid, spawnedHigh, removedItems);
        }
        return swept;
    }

    /** 同步跑完（管理員 /pg reset 時用，點位多時會卡一下）。 */
    public void runToCompletion() {
        while (!tick()) { /* 持續處理 */ }
    }

    private void processPoint(BlockPos pos) {
        // 載入點位所在區塊（只會載入當前地圖範圍內的點位）
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        clearAt(pos);
        if (mode == Mode.SPAWN && random.nextDouble() < PGConfig.SERVER.lootSpawnChance.get()) {
            spawnAt(pos);
        }
    }

    private void clearAt(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CrateBlock)) return; // 不動管理員放的其他方塊
        if (level.getBlockEntity(pos) instanceof CrateBlockEntity crate) crate.wipe();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void spawnAt(BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()) return; // 點位被其他方塊佔住就跳過
        PGConfig c = PGConfig.SERVER;
        int wLow = c.lootWeightLow.get(), wMid = c.lootWeightMid.get(), wHigh = c.lootWeightHigh.get();
        int total = wLow + wMid + wHigh;
        if (total <= 0) return;
        int roll = random.nextInt(total);
        Block block;
        ResourceLocation table;
        if (roll < wLow)              { block = PGBlocks.CRATE_LOW.get();  table = TABLE_LOW;  spawnedLow++; }
        else if (roll < wLow + wMid)  { block = PGBlocks.CRATE_MID.get();  table = TABLE_MID;  spawnedMid++; }
        else                          { block = PGBlocks.CRATE_HIGH.get(); table = TABLE_HIGH; spawnedHigh++; }
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CrateBlockEntity crate) {
            crate.setLootTable(table, random.nextLong());
        }
    }

    /** 掃描地圖範圍（中心 ± 半徑，整個高度）的掉落物並移除。只處理已載入的實體。 */
    private void sweepItems() {
        double r = map.radius + 16;
        AABB box = new AABB(map.centerX - r, level.getMinBuildHeight(), map.centerZ - r,
                            map.centerX + r, level.getMaxBuildHeight(), map.centerZ + r);
        for (Entity e : level.getEntities(EntityType.ITEM, box, e -> true)) {
            e.discard();
            removedItems++;
        }
    }

    /** 預期本局箱數 = 點位數 × 生成機率。 */
    public static double expectedCrates(MapRegistry.BRMap map) {
        return map.lootPoints.size() * PGConfig.SERVER.lootSpawnChance.get();
    }
}
