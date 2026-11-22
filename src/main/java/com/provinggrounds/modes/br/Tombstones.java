package com.provinggrounds.modes.br;

import com.provinggrounds.block.TombstoneBlockEntity;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 檔案: Tombstones.java
 * 目的: 墓碑的建立與清除（BR 淘汰時）。
 *   建立：在屍體位置找一格空氣（往上找最多 8 格），放墓碑，把身上所有物品與背包欄收進去；
 *         找不到位置（例如掉進虛空）時才照原版掉落在地上。
 *   清除：每個墓碑的位置記在 world/data/provinggrounds_tombstones.dat；一局結束、強制停止、
 *         以及下一局開始時（處理當機殘留）照記錄清掉，內容直接消失。
 */
public final class Tombstones {

    private Tombstones() {}

    /** 把玩家身上所有東西收進墓碑。回傳是否成功（失敗時物品已照原版掉落）。 */
    public static boolean place(ServerPlayer p, Vec3 corpse) {
        ServerLevel level = p.serverLevel();
        BlockPos pos = findSpot(level, BlockPos.containing(corpse));
        if (pos != null) level.setBlockAndUpdate(pos, PGBlocks.TOMBSTONE.get().defaultBlockState());
        if (pos == null || !(level.getBlockEntity(pos) instanceof TombstoneBlockEntity t)) {
            Backpack.dropEquipped(p);
            p.getInventory().dropAll();
            return false;
        }
        t.setOwner(p.getGameProfile().getName());
        List<ItemStack> leftovers = new ArrayList<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.removeItemNoUpdate(i);
            if (!s.isEmpty() && !t.add(s)) leftovers.add(s);
        }
        ItemStack pack = Backpack.container(p).getItem(0).copy();
        Backpack.clear(p);
        if (!pack.isEmpty() && !t.add(pack)) leftovers.add(pack);
        for (ItemStack s : leftovers) HotbarRules.dropAtFeet(p, s); // 理論上不會發生（54 格 > 42 格）
        Ledger.get(level).add(pos);
        return true;
    }

    /** 往上找第一格可以放墓碑的位置（空氣或可取代的方塊，例如草、雪）。 */
    @Nullable
    private static BlockPos findSpot(ServerLevel level, BlockPos start) {
        if (start.getY() < level.getMinBuildHeight() || start.getY() >= level.getMaxBuildHeight() - 1) return null;
        BlockPos.MutableBlockPos m = start.mutable();
        for (int i = 0; i < 8 && m.getY() < level.getMaxBuildHeight() - 1; i++, m.move(0, 1, 0)) {
            BlockState state = level.getBlockState(m);
            if (state.isAir() || state.canBeReplaced()) return m.immutable();
        }
        return null;
    }

    /** 墓碑被拿空自動消失時：從記錄移除。 */
    public static void forget(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) Ledger.get(sl).remove(pos);
    }

    /** 清除所有記錄中的墓碑（內容直接消失）。 */
    public static void cleanupAll(ServerLevel level) {
        Ledger ledger = Ledger.get(level);
        for (BlockPos pos : new ArrayList<>(ledger.entries)) {
            level.getChunk(pos); // 區塊沒載入時先載入
            if (level.getBlockEntity(pos) instanceof TombstoneBlockEntity t) t.wipe();
            if (level.getBlockState(pos).is(PGBlocks.TOMBSTONE.get())) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        ledger.clear();
    }

    /** 墓碑位置記錄（伺服器當機也不會留下殘骸）。 */
    static final class Ledger extends SavedData {
        private static final String FILE = "provinggrounds_tombstones";
        final LinkedHashSet<BlockPos> entries = new LinkedHashSet<>();

        static Ledger get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(Ledger::load, Ledger::new, FILE);
        }

        static Ledger load(CompoundTag tag) {
            Ledger l = new Ledger();
            for (long packed : tag.getLongArray("entries")) l.entries.add(BlockPos.of(packed));
            return l;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("entries", entries.stream().mapToLong(BlockPos::asLong).toArray());
            return tag;
        }

        void add(BlockPos pos) { if (entries.add(pos.immutable())) setDirty(); }

        void remove(BlockPos pos) { if (entries.remove(pos)) setDirty(); }

        void clear() { if (!entries.isEmpty()) { entries.clear(); setDirty(); } }
    }
}
