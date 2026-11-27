package com.provinggrounds.modes.arena;

import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: ArenaMapRegistry.java
 * 目的: 團隊模式（團隊死鬥、佔點、槍王之王）多地圖登記表與目前啟用場地。
 *   存於 world/data/provinggrounds_arena_maps.dat。
 *   - 保留 "default"（「經典場地」：直接讀取伺服器設定檔 provinggrounds-server.toml 的 [arena] 與 gk.json）；
 *   - 支援指令生成並切換到模組內建室內地圖（例如 obsidian_bunker「黑曜地堡」），一鍵開打即套用全部重生點與據點。
 */
public class ArenaMapRegistry extends SavedData {

    public static final String FILE = "provinggrounds_arena_maps";
    public static final String DEFAULT_ID = "default";

    /** 佔點據點定義。floor 可為 null（代表不自動換鐵磚／金磚）。 */
    public record DomPointDef(String name, Vec3 center, int radius, @Nullable BlockPos[] floor) {}

    /** 方形重生保護區的四邊距離（相對重生點中心的 北 -Z、南 +Z、西 -X、東 +X 格數）。 */
    public record CampBounds(int north, int south, int west, int east) {
        public static int clamp(int v) { return Math.max(1, Math.min(128, v)); }

        public static CampBounds uniform(int r) {
            int c = clamp(r);
            return new CampBounds(c, c, c, c);
        }

        public CampBounds clamped() {
            return new CampBounds(clamp(north), clamp(south), clamp(west), clamp(east));
        }

        public String encodeCsv() {
            return north + "," + south + "," + west + "," + east;
        }

        public static CampBounds parseCsv(@Nullable String s, int fallback) {
            if (s == null || s.isBlank()) return uniform(fallback);
            String[] p = s.split(",");
            if (p.length < 4) return uniform(fallback);
            try {
                return new CampBounds(
                    clamp(Integer.parseInt(p[0].trim())),
                    clamp(Integer.parseInt(p[1].trim())),
                    clamp(Integer.parseInt(p[2].trim())),
                    clamp(Integer.parseInt(p[3].trim()))
                );
            } catch (NumberFormatException e) {
                return uniform(fallback);
            }
        }

        public boolean contains(double dx, double dz) {
            return dx >= -west && dx <= east && dz >= -north && dz <= south;
        }

        /** 帶正負號距離（< 0 在範圍內；>= 0 為距離最近邊界的格數）。 */
        public double signedDistance(double dx, double dz) {
            return Math.max(Math.max(-west - dx, dx - east), Math.max(-north - dz, dz - south));
        }
    }

    /** 自訂生成的一張團隊場地完整資料（所有座標為世界座標）。 */
    public record ArenaMapDef(
        String id,
        String name,
        BlockPos origin,
        int sizeX,
        int sizeY,
        int sizeZ,
        int baseRadius,
        int antiCampRadius,
        boolean antiCampSquare,
        CampBounds redCampBounds,
        CampBounds blueCampBounds,
        List<Vec3> redSpawns,
        List<Vec3> blueSpawns,
        List<BlockPos[]> barriers,
        List<Vec3> supplyChests,
        List<DomPointDef> domPoints,
        List<Vec3> gkSpawns
    ) {
        public ArenaMapDef(
            String id, String name, BlockPos origin, int sizeX, int sizeY, int sizeZ,
            int baseRadius, int antiCampRadius, boolean antiCampSquare,
            List<Vec3> redSpawns, List<Vec3> blueSpawns, List<BlockPos[]> barriers,
            List<Vec3> supplyChests, List<DomPointDef> domPoints, List<Vec3> gkSpawns
        ) {
            this(id, name, origin, sizeX, sizeY, sizeZ, baseRadius, antiCampRadius, antiCampSquare,
                CampBounds.uniform(antiCampRadius), CampBounds.uniform(antiCampRadius),
                redSpawns, blueSpawns, barriers, supplyChests, domPoints, gkSpawns);
        }

        public Vec3 redSpawn() {
            return redSpawns.isEmpty() ? Vec3.atBottomCenterOf(origin) : redSpawns.get(0);
        }

        public Vec3 blueSpawn() {
            return blueSpawns.isEmpty() ? Vec3.atBottomCenterOf(origin) : blueSpawns.get(0);
        }

        public ActiveArena toActive() {
            return new ActiveArena(id, name, baseRadius, antiCampRadius, antiCampSquare,
                redCampBounds, blueCampBounds, redSpawns, blueSpawns,
                barriers, supplyChests, domPoints, gkSpawns);
        }
    }

    /** 目前對戰實際使用的團隊場地參數（來自選中的自訂地圖或 "default" 設定檔）。 */
    public record ActiveArena(
        String id,
        String name,
        int baseRadius,
        int antiCampRadius,
        boolean antiCampSquare,
        CampBounds redCampBounds,
        CampBounds blueCampBounds,
        List<Vec3> redSpawns,
        List<Vec3> blueSpawns,
        List<BlockPos[]> barriers,
        List<Vec3> supplyChests,
        List<DomPointDef> domPoints,
        List<Vec3> gkSpawns
    ) {
        public Vec3 redSpawn() {
            return redSpawns.isEmpty() ? new Vec3(0.5, 64.0, 0.5) : redSpawns.get(0);
        }

        public Vec3 blueSpawn() {
            return blueSpawns.isEmpty() ? new Vec3(0.5, 64.0, 0.5) : blueSpawns.get(0);
        }

        public static ActiveArena fromDefault(ArenaConfig cfg) {
            List<Vec3> red = new ArrayList<>();
            Vec3 rMain = ArenaConfig.vec(cfg.redSpawn.get());
            if (rMain != null) red.add(rMain);
            for (String s : cfg.redSpawnsExtra.get()) {
                Vec3 v = ArenaConfig.vec(s);
                if (v != null) red.add(v);
            }

            List<Vec3> blue = new ArrayList<>();
            Vec3 bMain = ArenaConfig.vec(cfg.blueSpawn.get());
            if (bMain != null) blue.add(bMain);
            for (String s : cfg.blueSpawnsExtra.get()) {
                Vec3 v = ArenaConfig.vec(s);
                if (v != null) blue.add(v);
            }

            List<Vec3> chests = new ArrayList<>();
            for (String s : cfg.supplyChests.get()) {
                Vec3 v = ArenaConfig.vec(s);
                if (v != null) chests.add(v);
            }

            List<DomPointDef> points = new ArrayList<>();
            for (String line : cfg.domPoints.get()) {
                String[] parts = line.split("\\|");
                if (parts.length < 3) continue;
                Vec3 c = ArenaConfig.vec(parts[1]);
                if (c == null) continue;
                int r;
                try {
                    r = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                BlockPos[] floor = parts.length > 3 ? ArenaConfig.box(parts[3]) : null;
                points.add(new DomPointDef(parts[0].trim(), c, r, floor));
            }

            int campR = cfg.antiCampRadius.get();
            return new ActiveArena(
                DEFAULT_ID,
                "經典場地（設定檔）",
                cfg.baseRadius.get(),
                campR,
                "square".equalsIgnoreCase(cfg.antiCampShape.get()),
                CampBounds.parseCsv(cfg.antiCampRedBounds.get(), campR),
                CampBounds.parseCsv(cfg.antiCampBlueBounds.get(), campR),
                List.copyOf(red),
                List.copyOf(blue),
                ArenaConfig.boxes(cfg.barriers.get()),
                List.copyOf(chests),
                List.copyOf(points),
                ArenaData.gkSpawns()
            );
        }
    }

    private final Map<String, ArenaMapDef> maps = new LinkedHashMap<>();
    private String activeId = DEFAULT_ID;

    public static ArenaMapRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(ArenaMapRegistry::load, ArenaMapRegistry::new, FILE);
    }

    public String activeId() {
        return maps.containsKey(activeId) ? activeId : DEFAULT_ID;
    }

    public ActiveArena active(ArenaConfig cfg) {
        if (!DEFAULT_ID.equals(activeId)) {
            ArenaMapDef def = maps.get(activeId);
            if (def != null) return def.toActive();
        }
        return ActiveArena.fromDefault(cfg);
    }

    public boolean setActive(String id) {
        String key = id.trim().toLowerCase(Locale.ROOT);
        if (!DEFAULT_ID.equals(key) && !maps.containsKey(key)) return false;
        activeId = key;
        setDirty();
        return true;
    }

    public void put(ArenaMapDef def) {
        maps.put(def.id().toLowerCase(Locale.ROOT), def);
        setDirty();
    }

    /** 管理員自訂指定團隊地圖的重生點保護形狀（圓形／方形）、半徑與紅藍四邊獨立長度。 */
    public boolean updateAntiCamp(String id, boolean square, int radius, CampBounds redBounds, CampBounds blueBounds) {
        String key = id.trim().toLowerCase(Locale.ROOT);
        ArenaMapDef old = maps.get(key);
        if (old == null) return false;
        int r = Math.max(2, Math.min(128, radius));
        CampBounds rb = redBounds != null ? redBounds.clamped() : CampBounds.uniform(r);
        CampBounds bb = blueBounds != null ? blueBounds.clamped() : CampBounds.uniform(r);
        maps.put(key, new ArenaMapDef(old.id(), old.name(), old.origin(), old.sizeX(), old.sizeY(), old.sizeZ(),
            old.baseRadius(), r, square, rb, bb, old.redSpawns(), old.blueSpawns(), old.barriers(),
            old.supplyChests(), old.domPoints(), old.gkSpawns()));
        setDirty();
        return true;
    }

    @Nullable
    public ArenaMapDef getMap(String id) {
        return maps.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public Collection<ArenaMapDef> customMaps() {
        return Collections.unmodifiableCollection(maps.values());
    }

    // ── NBT 序列化 ──────────────────────────────────────────────────────────

    private static ArenaMapRegistry load(CompoundTag tag) {
        ArenaMapRegistry reg = new ArenaMapRegistry();
        if (tag.contains("active", Tag.TAG_STRING)) {
            reg.activeId = tag.getString("active");
        }
        ListTag list = tag.getList("maps", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                ArenaMapDef def = readMap(list.getCompound(i));
                if (def != null) reg.maps.put(def.id().toLowerCase(Locale.ROOT), def);
            } catch (Exception e) {
                ProvingGroundsMod.LOGGER.warn("Failed to load arena map entry {}", i, e);
            }
        }
        if (!DEFAULT_ID.equals(reg.activeId) && !reg.maps.containsKey(reg.activeId)) {
            reg.activeId = DEFAULT_ID;
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("active", activeId());
        ListTag list = new ListTag();
        for (ArenaMapDef def : maps.values()) {
            list.add(writeMap(def));
        }
        tag.put("maps", list);
        return tag;
    }

    private static CompoundTag writeMap(ArenaMapDef m) {
        CompoundTag t = new CompoundTag();
        t.putString("id", m.id());
        t.putString("name", m.name());
        t.putInt("ox", m.origin().getX());
        t.putInt("oy", m.origin().getY());
        t.putInt("oz", m.origin().getZ());
        t.putInt("sx", m.sizeX());
        t.putInt("sy", m.sizeY());
        t.putInt("sz", m.sizeZ());
        t.putInt("baseR", m.baseRadius());
        t.putInt("campR", m.antiCampRadius());
        t.putBoolean("campSq", m.antiCampSquare());
        t.putString("campRed", m.redCampBounds().encodeCsv());
        t.putString("campBlue", m.blueCampBounds().encodeCsv());
        t.put("red", writeVecList(m.redSpawns()));
        t.put("blue", writeVecList(m.blueSpawns()));
        t.put("chests", writeVecList(m.supplyChests()));
        t.put("gk", writeVecList(m.gkSpawns()));
        t.put("barriers", writeBoxList(m.barriers()));

        ListTag pts = new ListTag();
        for (DomPointDef p : m.domPoints()) {
            CompoundTag pt = new CompoundTag();
            pt.putString("name", p.name());
            pt.put("c", writeVec(p.center()));
            pt.putInt("r", p.radius());
            if (p.floor() != null) pt.put("floor", writeBox(p.floor()));
            pts.add(pt);
        }
        t.put("points", pts);
        return t;
    }

    @Nullable
    private static ArenaMapDef readMap(CompoundTag t) {
        String id = t.getString("id");
        String name = t.getString("name");
        if (id.isBlank()) return null;
        BlockPos origin = new BlockPos(t.getInt("ox"), t.getInt("oy"), t.getInt("oz"));
        int sx = t.getInt("sx"), sy = t.getInt("sy"), sz = t.getInt("sz");
        int baseR = Math.max(2, t.getInt("baseR"));
        int campR = Math.max(4, t.getInt("campR"));
        boolean campSq = t.contains("campSq") ? t.getBoolean("campSq") : ObsidianBunkerBlueprint.MAP_ID.equalsIgnoreCase(id);
        CampBounds redCamp = CampBounds.parseCsv(t.getString("campRed"), campR);
        CampBounds blueCamp = CampBounds.parseCsv(t.getString("campBlue"), campR);
        List<Vec3> red = readVecList(t.getList("red", Tag.TAG_COMPOUND));
        List<Vec3> blue = readVecList(t.getList("blue", Tag.TAG_COMPOUND));
        List<Vec3> chests = readVecList(t.getList("chests", Tag.TAG_COMPOUND));
        List<Vec3> gk = readVecList(t.getList("gk", Tag.TAG_COMPOUND));
        List<BlockPos[]> barriers = readBoxList(t.getList("barriers", Tag.TAG_COMPOUND));

        List<DomPointDef> pts = new ArrayList<>();
        ListTag ptTags = t.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < ptTags.size(); i++) {
            CompoundTag pt = ptTags.getCompound(i);
            Vec3 c = readVec(pt.getCompound("c"));
            BlockPos[] floor = pt.contains("floor", Tag.TAG_COMPOUND) ? readBox(pt.getCompound("floor")) : null;
            pts.add(new DomPointDef(pt.getString("name"), c, pt.getInt("r"), floor));
        }
        return new ArenaMapDef(id, name.isBlank() ? id : name, origin, sx, sy, sz, baseR, campR, campSq,
            redCamp, blueCamp,
            List.copyOf(red), List.copyOf(blue), List.copyOf(barriers), List.copyOf(chests),
            List.copyOf(pts), List.copyOf(gk));
    }

    private static ListTag writeVecList(List<Vec3> list) {
        ListTag out = new ListTag();
        for (Vec3 v : list) out.add(writeVec(v));
        return out;
    }

    private static List<Vec3> readVecList(ListTag list) {
        List<Vec3> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(readVec(list.getCompound(i)));
        return out;
    }

    private static CompoundTag writeVec(Vec3 v) {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", v.x);
        t.putDouble("y", v.y);
        t.putDouble("z", v.z);
        return t;
    }

    private static Vec3 readVec(CompoundTag t) {
        return new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
    }

    private static ListTag writeBoxList(List<BlockPos[]> list) {
        ListTag out = new ListTag();
        for (BlockPos[] b : list) if (b != null && b.length >= 2) out.add(writeBox(b));
        return out;
    }

    private static List<BlockPos[]> readBoxList(ListTag list) {
        List<BlockPos[]> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(readBox(list.getCompound(i)));
        return out;
    }

    private static CompoundTag writeBox(BlockPos[] b) {
        CompoundTag t = new CompoundTag();
        t.putInt("x0", b[0].getX());
        t.putInt("y0", b[0].getY());
        t.putInt("z0", b[0].getZ());
        t.putInt("x1", b[1].getX());
        t.putInt("y1", b[1].getY());
        t.putInt("z1", b[1].getZ());
        return t;
    }

    private static BlockPos[] readBox(CompoundTag t) {
        return new BlockPos[] {
            new BlockPos(t.getInt("x0"), t.getInt("y0"), t.getInt("z0")),
            new BlockPos(t.getInt("x1"), t.getInt("y1"), t.getInt("z1"))
        };
    }
}
