package com.provinggrounds.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: MapRegistry.java
 * 目的: BR 地圖登記表（中心、半徑、名稱）與每張地圖的戰利品點位。
 *       存於 world/data/provinggrounds_maps.dat。首次建立時預載舊 Datapack 的 5 張地圖。
 *       點位只存座標，不綁箱子類型，也不使用 marker 實體。
 */
public class MapRegistry extends SavedData {

    public static final String FILE = "provinggrounds_maps";

    public record RegionLabel(String name, double x, double z) {}

    public static final class BRMap {
        public final int id;
        public String name;
        public double centerX, centerZ, radius;
        public final LinkedHashSet<BlockPos> lootPoints = new LinkedHashSet<>();
        public final List<RegionLabel> labels = new ArrayList<>();

        BRMap(int id, String name, double cx, double cz, double radius) {
            this.id = id; this.name = name; this.centerX = cx; this.centerZ = cz; this.radius = radius;
        }

        public boolean contains(double x, double z) {
            double dx = x - centerX, dz = z - centerZ;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    private final TreeMap<Integer, BRMap> maps = new TreeMap<>();

    public static MapRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(MapRegistry::load, MapRegistry::createWithDefaults, FILE);
    }

    private static MapRegistry createWithDefaults() {
        MapRegistry r = new MapRegistry();
        // 來源：舊 Datapack gamemode/br/map/map1~5.mcfunction
        r.maps.put(1, new BRMap(1, "經典", 0, 0, 400));
        r.maps.put(2, new BRMap(2, "腐蝕之地", -5306, 308, 300));
        r.maps.put(3, new BRMap(3, "櫻峰之城", -86, 5037, 450));
        r.maps.put(4, new BRMap(4, "山吹城", 5002, 4, 300));
        r.maps.put(5, new BRMap(5, "普羅米修斯禁區", -4, -5001, 300));
        r.setDirty();
        return r;
    }

    // ── 查詢 ────────────────────────────────────────────────────────────────

    public Collection<BRMap> all() { return Collections.unmodifiableCollection(maps.values()); }

    @Nullable
    public BRMap getMap(int id) { return maps.get(id); }

    /** 回傳包含此座標的地圖（地圖彼此相距約 5000 格，不會重疊）。 */
    @Nullable
    public BRMap findAt(double x, double z) {
        for (BRMap m : maps.values()) if (m.contains(x, z)) return m;
        return null;
    }

    // ── 修改 ────────────────────────────────────────────────────────────────

    public BRMap setMap(int id, String name, double cx, double cz, double radius) {
        BRMap m = maps.get(id);
        if (m == null) {
            m = new BRMap(id, name, cx, cz, radius);
            maps.put(id, m);
        } else {
            if (name != null && !name.isEmpty()) m.name = name;
            m.centerX = cx; m.centerZ = cz; m.radius = radius;
        }
        setDirty();
        return m;
    }

    public boolean removeMap(int id) {
        boolean removed = maps.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }

    public boolean addPoint(BRMap map, BlockPos pos) {
        boolean added = map.lootPoints.add(pos.immutable());
        if (added) { setDirty(); com.provinggrounds.admin.SpawnPointView.changed(); }
        return added;
    }

    /** 移除距離 pos 最近、且在 maxDist 格內的點位。回傳被移除的點，沒有則 null。 */
    @Nullable
    public BlockPos removeNearestPoint(BRMap map, BlockPos pos, double maxDist) {
        BlockPos best = null;
        double bestSq = maxDist * maxDist;
        for (BlockPos p : map.lootPoints) {
            double d = p.distSqr(pos);
            if (d <= bestSq) { bestSq = d; best = p; }
        }
        if (best != null) { map.lootPoints.remove(best); setDirty(); com.provinggrounds.admin.SpawnPointView.changed(); }
        return best;
    }

    public int clearPoints(BRMap map) {
        int n = map.lootPoints.size();
        map.lootPoints.clear();
        if (n > 0) { setDirty(); com.provinggrounds.admin.SpawnPointView.changed(); }
        return n;
    }

    public void setLabels(BRMap map, List<RegionLabel> labels) {
        map.labels.clear();
        map.labels.addAll(labels);
        setDirty();
    }

    public void setLabel(BRMap map, String name, double x, double z) {
        String clean = name.trim();
        if (clean.isEmpty()) return;
        map.labels.removeIf(l -> l.name().equalsIgnoreCase(clean)
            || Math.hypot(l.x() - x, l.z() - z) < 12.0);
        map.labels.add(new RegionLabel(clean, x, z));
        setDirty();
    }

    public boolean removeLabel(BRMap map, String name) {
        boolean removed = map.labels.removeIf(l -> l.name().equalsIgnoreCase(name.trim()));
        if (removed) setDirty();
        return removed;
    }

    // ── NBT ─────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BRMap m : maps.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", m.id);
            t.putString("name", m.name);
            t.putDouble("cx", m.centerX);
            t.putDouble("cz", m.centerZ);
            t.putDouble("radius", m.radius);
            long[] pts = new long[m.lootPoints.size()];
            int i = 0;
            for (BlockPos p : m.lootPoints) pts[i++] = p.asLong();
            t.put("points", new LongArrayTag(pts));
            ListTag lbls = new ListTag();
            for (RegionLabel lb : m.labels) {
                CompoundTag lt = new CompoundTag();
                lt.putString("n", lb.name());
                lt.putDouble("x", lb.x());
                lt.putDouble("z", lb.z());
                lbls.add(lt);
            }
            t.put("labels", lbls);
            list.add(t);
        }
        tag.put("maps", list);
        return tag;
    }

    private static MapRegistry load(CompoundTag tag) {
        MapRegistry r = new MapRegistry();
        ListTag list = tag.getList("maps", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            BRMap m = new BRMap(t.getInt("id"), t.getString("name"),
                t.getDouble("cx"), t.getDouble("cz"), t.getDouble("radius"));
            for (long l : t.getLongArray("points")) m.lootPoints.add(BlockPos.of(l));
            ListTag lbls = t.getList("labels", Tag.TAG_COMPOUND);
            for (int j = 0; j < lbls.size(); j++) {
                CompoundTag lt = lbls.getCompound(j);
                m.labels.add(new RegionLabel(lt.getString("n"), lt.getDouble("x"), lt.getDouble("z")));
            }
            r.maps.put(m.id, m);
        }
        return r;
    }
}
