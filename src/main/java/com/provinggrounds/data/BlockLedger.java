package com.provinggrounds.data;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * 檔案: BlockLedger.java
 * 目的: 方塊還原（取代原本只修玻璃的 GlassLedger；存於 world/data/provinggrounds_glass.dat，沿用舊檔名，舊紀錄也讀得到）。
 *   比賽中：主世界裡任何方塊被改（玩家破壞或放置、爆炸、子彈、火、液體、其他模組…）之前，
 *     由 LevelSetBlockMixin 呼叫 beforeSet，記下這個位置「第一次被改之前」的方塊與方塊實體資料（箱子內容等）。
 *     範圍由模式決定：大逃殺 = 地圖圓圈外加 64 格；團隊死鬥／佔點／槍王之王 = 整個主世界。
 *   比賽外：只記錄被子彈或爆炸打破的玻璃（大廳的玻璃），管理員平常改建地圖不會被還原蓋掉。
 *   還原：比賽一結束就還原；下一場開始時再還原一次（萬一中途當機）。
 *     第一輪：照原樣放回所有方塊（不通知鄰居，避免火把、畫這類依附方塊先掉下來），再寫回方塊實體資料；
 *     第二輪：每個還原的位置重新計算自己和鄰居的連接形狀 → 玻璃片、鐵柵欄、柵欄、牆、樓梯都會重新接好。
 *     （舊版只修玻璃時不更新形狀，被打破那片的鄰居一直維持「縮回去」的樣子，所以會變成一條一條的玻璃。）
 *   記錄存在硬碟，伺服器中途當機重開也不會遺失。
 */
public class BlockLedger extends SavedData {

    public static final String FILE = "provinggrounds_glass";

    private record Entry(BlockState state, @Nullable CompoundTag blockEntity) {}

    /** 位置 → 第一次被改之前的方塊。 */
    private final Map<Long, Entry> original = new LinkedHashMap<>();

    /** 比賽中要記錄的範圍；null = 沒有比賽（只記玻璃）。 */
    @Nullable private static Predicate<BlockPos> scope;
    private static boolean restoring;
    @Nullable private static BlockLedger cached;

    public static BlockLedger get(MinecraftServer server) {
        BlockLedger l = server.overworld().getDataStorage().computeIfAbsent(BlockLedger::load, BlockLedger::new, FILE);
        cached = l;
        return l;
    }

    private static BlockLedger load(CompoundTag tag) {
        BlockLedger l = new BlockLedger();
        var lookup = BuiltInRegistries.BLOCK.asLookup();
        ListTag list = tag.getList("broken", Tag.TAG_COMPOUND); // 舊版（只有玻璃）的格式也讀得到
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            l.original.put(e.getLong("pos"), new Entry(NbtUtils.readBlockState(lookup, e.getCompound("state")),
                e.contains("be") ? e.getCompound("be") : null));
        }
        return l;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        original.forEach((pos, en) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", pos);
            e.put("state", NbtUtils.writeBlockState(en.state()));
            if (en.blockEntity() != null) e.put("be", en.blockEntity());
            list.add(e);
        });
        tag.put("broken", list);
        return tag;
    }

    // ── 記錄 ──

    /** 比賽開始：開始記錄這個範圍內的所有方塊變化。 */
    public static void beginMatch(MinecraftServer server, Predicate<BlockPos> inScope) {
        get(server);
        scope = inScope;
    }

    /** 比賽結束：停止記錄並立刻還原。回傳還原數量。 */
    public static int endMatch(MinecraftServer server) {
        scope = null;
        return get(server).restoreAll(server.overworld());
    }

    public static boolean recording() { return scope != null; }

    /** LevelSetBlockMixin：主世界的方塊即將被改。 */
    public static void beforeSet(Level level, BlockPos pos) {
        Predicate<BlockPos> s = scope;
        if (s == null || restoring || level.isClientSide || !(level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return;
        BlockLedger l = cached;
        if (l == null || !s.test(pos)) return;
        long key = pos.asLong();
        if (l.original.containsKey(key)) return;
        BlockEntity be = level.getBlockEntity(pos);
        l.original.put(key, new Entry(level.getBlockState(pos), be != null ? be.saveWithFullMetadata() : null));
        l.setDirty();
    }

    /**
     * 這個方塊是不是「會被打破、要記錄」的玻璃類方塊（原版與模組都算，例如 connectedglass 的玻璃）。
     * 判斷：玻璃類別（含子類別）、玻璃片、鐵柵欄、敲擊音色是「高帽鼓」（原版玻璃的特徵），或方塊 ID 含 glass。
     */
    public static boolean isGlass(BlockState state) {
        Block b = state.getBlock();
        if (BuiltInRegistries.BLOCK.getKey(b).getPath().contains("glass")) return true;
        return b instanceof AbstractGlassBlock || b instanceof StainedGlassPaneBlock || b instanceof IronBarsBlock
            || state.instrument() == NoteBlockInstrument.HAT;
    }

    /** 比賽外：記錄一塊即將被子彈或爆炸打破的玻璃（同一位置只記第一次）。 */
    public void recordGlass(ServerLevel level, BlockPos pos, BlockState state) {
        if (level != level.getServer().overworld() || !isGlass(state)) return;
        if (original.putIfAbsent(pos.asLong(), new Entry(state, null)) == null) setDirty();
    }

    public int size() { return original.size(); }

    // ── 還原 ──

    /** 把所有記錄的方塊放回原樣，回傳數量。依區塊排序，同區塊只載入一次。 */
    public int restoreAll(ServerLevel level) {
        if (original.isEmpty()) return 0;
        long t0 = System.nanoTime();
        List<Map.Entry<Long, Entry>> entries = new ArrayList<>(original.entrySet());
        entries.sort(Comparator.comparingLong(e -> {
            BlockPos p = BlockPos.of(e.getKey());
            return ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
        }));
        int restored = 0;
        restoring = true;
        try {
            // 第一輪：放回方塊（2 = 通知客戶端、16 = 先不更新鄰居形狀、32 = 被換掉的方塊不掉落物品）
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (Map.Entry<Long, Entry> e : entries) {
                BlockPos pos = BlockPos.of(e.getKey());
                level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                Entry en = e.getValue();
                if (level.getBlockState(pos) != en.state()) {
                    level.removeBlockEntity(pos); // 舊的方塊實體（例如被放上去的箱子）先拿掉，不掉內容物
                    level.setBlock(pos, en.state(), flags);
                    restored++;
                }
                if (en.blockEntity() != null) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) { be.load(en.blockEntity()); be.setChanged(); }
                }
            }
            // 第二輪：重新計算形狀，讓玻璃片、柵欄、牆和它們的鄰居重新接起來
            for (Map.Entry<Long, Entry> e : entries) {
                BlockPos pos = BlockPos.of(e.getKey());
                BlockState cur = level.getBlockState(pos);
                BlockState shaped = Block.updateFromNeighbourShapes(cur, level, pos);
                if (shaped != cur) level.setBlock(pos, shaped, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                shaped.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
                shaped.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
            }
        } finally {
            restoring = false;
        }
        int total = original.size();
        original.clear();
        setDirty();
        ProvingGroundsMod.LOGGER.info("Block restore: {} / {} blocks put back in {} ms.", restored, total, (System.nanoTime() - t0) / 1_000_000);
        return restored;
    }
}
