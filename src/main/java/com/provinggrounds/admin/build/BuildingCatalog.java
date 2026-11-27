package com.provinggrounds.admin.build;

import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: BuildingCatalog.java
 * 目的: 匯整 8 種風格共 52 棟大型／超大型建築藍圖，並管理放置、自動登記大逃殺物資箱點位、
 *       以及最多 8 筆一鍵復原紀錄（復原方塊＋移除剛剛新增的物資箱點位）。
 */
public final class BuildingCatalog {

    /** 存在建築生成工具物品上的選中建築 ID NBT 鍵。 */
    public static final String TOOL_NBT = "pg_build_id";

    private static final List<BuildingBlueprint> ALL = new ArrayList<>();
    private static final Map<String, BuildingBlueprint> BY_ID = new LinkedHashMap<>();
    private static final Map<BuildingStyle, List<BuildingBlueprint>> BY_STYLE = new EnumMap<>(BuildingStyle.class);

    /** 每位管理員目前選中的建築 ID。 */
    private static final Map<UUID, String> SELECTED = new HashMap<>();

    private record UndoRecord(
        String buildingName,
        ServerLevel level,
        Map<BlockPos, BlockState> previousBlocks,
        @Nullable Integer mapId,
        List<BlockPos> addedLootPoints
    ) {}

    private static final Deque<UndoRecord> UNDO_STACK = new ArrayDeque<>();
    private static final int MAX_UNDO = 8;

    static {
        for (BuildingStyle s : BuildingStyle.values()) BY_STYLE.put(s, new ArrayList<>());
        ModernBuildings.register(BuildingCatalog::add);
        IndustrialBuildings.register(BuildingCatalog::add);
        JapaneseBuildings.register(BuildingCatalog::add);
        ScifiBuildings.register(BuildingCatalog::add);
        CivicBuildings.register(BuildingCatalog::add);
        MedievalBuildings.register(BuildingCatalog::add);
        DesertBuildings.register(BuildingCatalog::add);
        MaritimeBuildings.register(BuildingCatalog::add);
    }

    private BuildingCatalog() {}

    private static void add(BuildingBlueprint bp) {
        ALL.add(bp);
        BY_ID.put(bp.id(), bp);
        BY_STYLE.get(bp.style()).add(bp);
    }

    public static List<BuildingBlueprint> all() { return Collections.unmodifiableList(ALL); }

    public static List<BuildingBlueprint> byStyle(BuildingStyle style) {
        return Collections.unmodifiableList(BY_STYLE.getOrDefault(style, List.of()));
    }

    @Nullable
    public static BuildingBlueprint byId(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static BuildingBlueprint first() { return ALL.get(0); }

    public static String selectedId(UUID playerId) {
        String id = SELECTED.get(playerId);
        return (id != null && BY_ID.containsKey(id)) ? id : first().id();
    }

    public static void select(UUID playerId, String id) {
        if (BY_ID.containsKey(id)) SELECTED.put(playerId, id);
    }

    public static int undoCount() { return UNDO_STACK.size(); }

    public static void clearUndo() { UNDO_STACK.clear(); }

    /**
     * 在玩家前方放置指定建築，並自動把建築內的物資箱生成點登記進目前所在的大逃殺地圖。
     */
    public static Component place(ServerPlayer p, String buildingId) {
        if (BattleRoyaleMode.get().isRunning()) {
            return AdminTools.err("大逃殺進行中，不能放置建築。");
        }
        BuildingBlueprint bp = byId(buildingId);
        if (bp == null) return AdminTools.err("找不到建築款式：" + buildingId);

        ServerLevel level = p.serverLevel();
        BlockPos origin = BuildingCanvas.anchorFor(p);
        Direction facing = p.getDirection();

        BuildingCanvas canvas = new BuildingCanvas(level, origin, facing);
        bp.builder().build(canvas);
        canvas.finalizeShapes();

        // 自動登記所有物資箱生成點到目前所在的大逃殺地圖
        MapRegistry reg = MapRegistry.get(p.server);
        MapRegistry.BRMap map = reg.findAt(origin.getX() + 0.5, origin.getZ() + 0.5);
        List<BlockPos> addedLoot = new ArrayList<>();
        if (map != null) {
            for (BlockPos lp : canvas.lootPoints()) {
                if (map.contains(lp.getX() + 0.5, lp.getZ() + 0.5) && reg.addPoint(map, lp)) {
                    addedLoot.add(lp);
                }
            }
        }
        for (BlockPos lp : canvas.lootPoints()) {
            level.sendParticles(p, ParticleTypes.END_ROD, true,
                lp.getX() + 0.5, lp.getY() + 0.5, lp.getZ() + 0.5, 8, 0.15, 0.25, 0.15, 0.01);
        }

        UNDO_STACK.addFirst(new UndoRecord(bp.name(), level, canvas.previousBlocks(), map != null ? map.id : null, addedLoot));
        while (UNDO_STACK.size() > MAX_UNDO) UNDO_STACK.removeLast();

        level.playSound(null, origin, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7f, 1.1f);
        if (map != null) {
            return AdminTools.ok(String.format("已生成【%s】「%s」（%d 格方塊），並自動在地圖 #%d %s 登記 %d 個物資箱點位（共 %d 個）。",
                bp.style().displayName, bp.name(), canvas.previousBlocks().size(), map.id, map.name, addedLoot.size(), map.lootPoints.size()));
        }
        return AdminTools.ok(String.format("已生成【%s】「%s」（%d 格方塊）；此處不在大逃殺地圖範圍內，未登記物資箱點位。",
            bp.style().displayName, bp.name(), canvas.previousBlocks().size()));
    }

    /** 復原最近一次放置的建築（還原所有被覆蓋的方塊＋移除那次新增的物資箱點位）。 */
    public static Component undo(ServerPlayer p) {
        if (BattleRoyaleMode.get().isRunning()) {
            return AdminTools.err("大逃殺進行中，不能復原建築。");
        }
        UndoRecord rec = UNDO_STACK.pollFirst();
        if (rec == null) return AdminTools.err("沒有可以復原的建築紀錄。");

        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(rec.previousBlocks().entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            var e = entries.get(i);
            rec.level().setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (var e : entries) {
            BlockState st = rec.level().getBlockState(e.getKey());
            rec.level().sendBlockUpdated(e.getKey(), st, st, Block.UPDATE_ALL);
        }

        int removedLoot = 0;
        if (rec.mapId() != null && !rec.addedLootPoints().isEmpty()) {
            MapRegistry reg = MapRegistry.get(p.server);
            MapRegistry.BRMap map = reg.getMap(rec.mapId());
            if (map != null) {
                for (BlockPos lp : rec.addedLootPoints()) {
                    if (reg.removeNearestPoint(map, lp, 0.5) != null) removedLoot++;
                }
            }
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8f, 0.8f);
        return AdminTools.ok(String.format("已復原「%s」（還原 %d 格方塊、移除 %d 個物資箱點位；剩餘可復原 %d 筆）。",
            rec.buildingName(), entries.size(), removedLoot, UNDO_STACK.size()));
    }
}
