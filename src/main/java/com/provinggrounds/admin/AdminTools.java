package com.provinggrounds.admin;

import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * 檔案: AdminTools.java
 * 目的: 管理員工具（計畫書 B6～B10）與對應指令共用的程式：權限檢查、訊息樣式、新增箱子生成點。
 *   權限一律在伺服器端檢查：OP（權限 2 以上）或創造模式。
 */
public final class AdminTools {

    private static final String PREFIX = "[PG] ";

    private AdminTools() {}

    public static boolean allowed(Player p) {
        return p.isCreative() || p.hasPermissions(2);
    }

    public static MutableComponent ok(String s) {
        return Component.literal(PREFIX + s).withStyle(ChatFormatting.GREEN);
    }

    public static MutableComponent err(String s) {
        return Component.literal(PREFIX + s).withStyle(ChatFormatting.RED);
    }

    /**
     * 在 pos 新增箱子生成點（B8 物品與 /pg loot add 共用）。mapId = null 時依位置找地圖。
     * BR 進行中不允許新增（避免和刷新、清理流程打架）。同一個方塊座標已有點位時不重複新增。
     */
    public static Component addLootPoint(MinecraftServer server, BlockPos pos, @javax.annotation.Nullable Integer mapId) {
        if (BattleRoyaleMode.get().isRunning()) return err("大逃殺進行中，不能新增箱子生成點。");
        MapRegistry reg = MapRegistry.get(server);
        MapRegistry.BRMap m = mapId != null ? reg.getMap(mapId) : reg.findAt(pos.getX() + 0.5, pos.getZ() + 0.5);
        if (m == null) return err(mapId != null ? "找不到地圖 #" + mapId + "。" : "這個位置不在任何地圖範圍內。");
        if (!m.contains(pos.getX() + 0.5, pos.getZ() + 0.5)) {
            return err(String.format("這個位置在地圖 #%d 範圍外（中心 %.0f, %.0f，半徑 %.0f）。", m.id, m.centerX, m.centerZ, m.radius));
        }
        if (!reg.addPoint(m, pos)) {
            return err(String.format("此處已有箱子生成點（%d, %d, %d），未重複放置。", pos.getX(), pos.getY(), pos.getZ()));
        }
        return ok(String.format("已新增箱子生成點（地圖 #%d，%d, %d, %d），目前共 %d 個。",
            m.id, pos.getX(), pos.getY(), pos.getZ(), m.lootPoints.size()));
    }

    public static final double REMOVE_RANGE = 16;

    /**
     * 刪除離玩家最近的一個箱子生成點（所有地圖中找，16 格內）。被刪的位置閃紅框 3 秒並冒煙，讓管理員看得出是哪一個。
     * BR 進行中不允許（跟新增一樣）。
     */
    public static Component removeNearestLootPoint(net.minecraft.server.level.ServerPlayer p) {
        if (BattleRoyaleMode.get().isRunning()) return err("大逃殺進行中，不能刪除箱子生成點。");
        MapRegistry reg = MapRegistry.get(p.server);
        BlockPos here = p.blockPosition();
        MapRegistry.BRMap bestMap = null;
        BlockPos best = null;
        double bestSq = REMOVE_RANGE * REMOVE_RANGE;
        for (MapRegistry.BRMap m : reg.all()) {
            for (BlockPos pt : m.lootPoints) {
                double d = pt.distSqr(here);
                if (d <= bestSq) { bestSq = d; best = pt; bestMap = m; }
            }
        }
        if (best == null) return err("附近 " + (int) REMOVE_RANGE + " 格內沒有箱子生成點。");
        reg.removeNearestPoint(bestMap, best, 0.5);
        com.provinggrounds.network.PGNetwork.sendTo(p, new com.provinggrounds.network.SpawnPointFlashPacket(best));
        p.serverLevel().sendParticles(p, net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, true,
            best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
        return ok(String.format("已刪除箱子生成點（地圖 #%d，%d, %d, %d，距離 %.1f 格），地圖 #%d 剩 %d 個。",
            bestMap.id, best.getX(), best.getY(), best.getZ(), Math.sqrt(bestSq), bestMap.id, bestMap.lootPoints.size()));
    }

    /** 箱子轉換、地圖繪畫工具：把已登記的地圖清單送給玩家，開啟選擇畫面。 */
    public static void openMapPicker(net.minecraft.server.level.ServerPlayer p, com.provinggrounds.item.AdminToolItem.Tool tool) {
        java.util.List<com.provinggrounds.network.MapPickPacket.Entry> maps = new java.util.ArrayList<>();
        for (MapRegistry.BRMap m : MapRegistry.get(p.server).all()) {
            maps.add(new com.provinggrounds.network.MapPickPacket.Entry(m.id, m.name == null ? "" : m.name, m.lootPoints.size()));
        }
        if (maps.isEmpty()) {
            p.sendSystemMessage(err("還沒有登記任何地圖（先用 /pg map set 或 /pg map here 登記）。"));
            return;
        }
        maps.sort(java.util.Comparator.comparingInt(com.provinggrounds.network.MapPickPacket.Entry::id));
        com.provinggrounds.network.PGNetwork.sendTo(p, com.provinggrounds.network.MapPickPacket.open(tool, maps));
    }

    /**
     * 玩家在選擇畫面按了確認：重新檢查權限，然後用跟指令完全相同的程式執行
     * （箱子轉換 = /pg loot import <地圖>，地圖繪畫 = /pg map render <地圖>）。
     */
    public static void runMapTool(net.minecraft.server.level.ServerPlayer p, com.provinggrounds.item.AdminToolItem.Tool tool, int mapId) {
        if (!allowed(p)) {
            p.sendSystemMessage(err("沒有權限（只有 OP 或創造模式能使用）。"));
            return;
        }
        if (MapRegistry.get(p.server).getMap(mapId) == null) {
            p.sendSystemMessage(err("找不到地圖 #" + mapId + "。"));
            return;
        }
        String cmd = switch (tool) {
            case CRATE_CONVERTER -> "pg loot import " + mapId;
            case MAP_PAINTER -> "pg map render " + mapId;
            default -> null;
        };
        if (cmd == null) return;
        // 創造模式的非 OP 管理員也能用：以權限 2 執行（權限已在上面檢查過）
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2), cmd);
    }
}
