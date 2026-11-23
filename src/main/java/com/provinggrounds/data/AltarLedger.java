package com.provinggrounds.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 檔案: AltarLedger.java
 * 目的: 復活祭壇位置記錄（存於 world/data/provinggrounds_altars.dat）。
 *       祭壇的方塊實體第一次載入時登記、被破壞時移除（區塊卸載不算），
 *       地圖（大地圖、小地圖）用這份記錄畫出所有祭壇，不限於目前載入的範圍。
 */
public class AltarLedger extends SavedData {

    public static final String FILE = "provinggrounds_altars";

    private final LinkedHashSet<BlockPos> entries = new LinkedHashSet<>();

    public static AltarLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(AltarLedger::load, AltarLedger::new, FILE);
    }

    private static AltarLedger load(CompoundTag tag) {
        AltarLedger l = new AltarLedger();
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
