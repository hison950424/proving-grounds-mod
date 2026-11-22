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
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * 檔案: GlassLedger.java
 * 目的: 被打破的玻璃（原版與模組）記錄簿（存於 world/data/provinggrounds_glass.dat），每局開始時全部修回去。
 *   記錄時機：子彈打到玻璃（TACZ 的 AmmoHitBlockEvent，在 TACZ 打破玻璃之前）、
 *             爆炸波及玻璃（ExplosionEvent.Detonate，在方塊被炸掉之前）。
 *   只記「原版」的玻璃類方塊：玻璃、染色玻璃、玻璃片、鐵柵欄，以及其他會被子彈打破的原版方塊
 *   （跟 TACZ 判斷可打破玻璃的條件相同）。模組方塊不記錄、也不會被修。
 *   修復時：只有那個位置現在是空氣才放回去（管理員之後在那裡蓋了別的東西就不會蓋掉）。
 *   記錄存在硬碟，伺服器中途當機重開也不會遺失。
 */
public class GlassLedger extends SavedData {

    public static final String FILE = "provinggrounds_glass";

    /** 位置 → 打破前的方塊狀態（含玻璃片的連接方向）。 */
    private final Map<Long, BlockState> broken = new LinkedHashMap<>();

    public static GlassLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(GlassLedger::load, GlassLedger::new, FILE);
    }

    private static GlassLedger load(CompoundTag tag) {
        GlassLedger l = new GlassLedger();
        ListTag list = tag.getList("broken", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), e.getCompound("state"));
            l.broken.put(e.getLong("pos"), state);
        }
        return l;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        broken.forEach((pos, state) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", pos);
            e.put("state", NbtUtils.writeBlockState(state));
            list.add(e);
        });
        tag.put("broken", list);
        return tag;
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

    /** 記錄一塊即將被打破的玻璃（同一位置只記第一次的狀態）。 */
    public void record(ServerLevel level, BlockPos pos, BlockState state) {
        if (level != level.getServer().overworld() || !isGlass(state)) return;
        if (broken.putIfAbsent(pos.asLong(), state) == null) setDirty();
    }

    public int size() { return broken.size(); }

    /** 把所有記錄的玻璃放回去（位置是空氣才放），回傳修復數量。依區塊排序，同區塊只載入一次。 */
    public int restoreAll(ServerLevel level) {
        if (broken.isEmpty()) return 0;
        List<Map.Entry<Long, BlockState>> entries = new ArrayList<>(broken.entrySet());
        entries.sort(Comparator.comparingLong(e -> {
            BlockPos p = BlockPos.of(e.getKey());
            return ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
        }));
        int restored = 0;
        for (Map.Entry<Long, BlockState> e : entries) {
            BlockPos pos = BlockPos.of(e.getKey());
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (!level.getBlockState(pos).isAir()) continue;
            // 2 = 通知客戶端；16 = 不更新鄰居形狀（保留打破前玻璃片的連接方向）
            level.setBlock(pos, e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            restored++;
        }
        int total = broken.size();
        broken.clear();
        setDirty();
        ProvingGroundsMod.LOGGER.info("Glass restore: {} / {} blocks put back.", restored, total);
        return restored;
    }
}
