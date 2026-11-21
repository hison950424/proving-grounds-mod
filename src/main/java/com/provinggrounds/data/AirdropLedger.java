package com.provinggrounds.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 檔案: AirdropLedger.java
 * 目的: 空投記錄簿（存於 world/data/provinggrounds_airdrops.dat）。
 *       記下每個空投的落點（掉落中時是預計落點），遊戲結束時照記錄清除空投箱、
 *       掉落中的展示實體，並解除強制載入的區塊。
 *       伺服器中途當機也不會留下殘骸：下一局開始時會先照記錄清一次。
 */
public class AirdropLedger extends SavedData {

    public static final String FILE = "provinggrounds_airdrops";

    private final LinkedHashSet<BlockPos> entries = new LinkedHashSet<>();

    public static AirdropLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(AirdropLedger::load, AirdropLedger::new, FILE);
    }

    private static AirdropLedger load(CompoundTag tag) {
        AirdropLedger l = new AirdropLedger();
        for (long packed : tag.getLongArray("entries")) l.entries.add(BlockPos.of(packed));
        return l;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("entries", entries.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    public Set<BlockPos> entries() { return Collections.unmodifiableSet(entries); }

    public void add(BlockPos pos) {
        if (entries.add(pos.immutable())) setDirty();
    }

    public void remove(BlockPos pos) {
        if (entries.remove(pos)) setDirty();
    }

    public void clear() {
        if (!entries.isEmpty()) {
            entries.clear();
            setDirty();
        }
    }
}
