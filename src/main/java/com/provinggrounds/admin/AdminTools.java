package com.provinggrounds.admin;

import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.modes.BattleRoyaleMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    public static final double TOOL_RAY_RANGE = 16.0;

    /** 準星射線判定放置位置：對準方塊表面（可替換方塊則直接放在該格，否則放在相鄰空氣格），最遠 16 格。 */
    @Nullable
    public static BlockPos raycastPlacePos(Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(TOOL_RAY_RANGE));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos hp = hit.getBlockPos();
        return p.level().getBlockState(hp).canBeReplaced() ? hp : hp.relative(hit.getDirection());
    }

    /** 準星射線判定要刪除的箱子生成點：考慮實心牆阻擋（不隔牆誤刪）與 0.18 格寬容命中盒，最遠 16 格。 */
    @Nullable
    public static BlockPos raycastLootPoint(Player p, Iterable<BlockPos> points) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 wallEnd = eye.add(look.scale(TOOL_RAY_RANGE));
        BlockHitResult wallHit = p.level().clip(new ClipContext(eye, wallEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        double maxDist = (wallHit.getType() == HitResult.Type.BLOCK)
            ? Math.min(TOOL_RAY_RANGE, wallHit.getLocation().distanceTo(eye) + 0.65)
            : TOOL_RAY_RANGE;
        Vec3 rayEnd = eye.add(look.scale(maxDist));

        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        double limitSq = (TOOL_RAY_RANGE + 2.0) * (TOOL_RAY_RANGE + 2.0);
        for (BlockPos pt : points) {
            if (pt.distToCenterSqr(eye) > limitSq) continue;
            AABB box = new AABB(pt).inflate(0.18);
            if (box.contains(eye)) continue;
            Optional<Vec3> hit = box.clip(eye, rayEnd);
            if (hit.isPresent()) {
                double dSq = hit.get().distanceToSqr(eye);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    best = pt;
                }
            }
        }
        return best;
    }

    /** 使用「箱子生成點放置工具」於準星指向處新增生成點。 */
    public static Component addLootPointAtCrosshair(ServerPlayer p) {
        if (BattleRoyaleMode.get().isRunning()) return err("大逃殺進行中，不能新增箱子生成點。");
        BlockPos target = raycastPlacePos(p);
        if (target == null) return err("準星 16 格內沒有指向方塊，請瞄準要放置的位置。");
        Component res = addLootPoint(p.server, target, null);
        if (res.getString().contains("已新增")) {
            p.serverLevel().sendParticles(p, net.minecraft.core.particles.ParticleTypes.END_ROD, true,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.01);
        }
        return res;
    }

    /** 使用「箱子生成點刪除工具」刪除準星對準的生成點。 */
    public static Component removeLootPointAtCrosshair(ServerPlayer p) {
        if (BattleRoyaleMode.get().isRunning()) return err("大逃殺進行中，不能刪除箱子生成點。");
        MapRegistry reg = MapRegistry.get(p.server);
        List<BlockPos> allPts = new ArrayList<>();
        for (MapRegistry.BRMap m : reg.all()) allPts.addAll(m.lootPoints);
        BlockPos target = raycastLootPoint(p, allPts);
        if (target == null) return err("準星 16 格內沒有指到任何箱子生成點，請對準黃色方框。");

        MapRegistry.BRMap bestMap = null;
        for (MapRegistry.BRMap m : reg.all()) {
            if (m.lootPoints.contains(target)) {
                bestMap = m;
                break;
            }
        }
        if (bestMap == null) return err("找不到該生成點所屬的地圖資料。");
        reg.removeNearestPoint(bestMap, target, 0.5);
        com.provinggrounds.network.PGNetwork.sendTo(p, new com.provinggrounds.network.SpawnPointFlashPacket(target));
        p.serverLevel().sendParticles(p, net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, true,
            target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
        double dist = Math.sqrt(target.distToCenterSqr(p.getEyePosition()));
        return ok(String.format("已刪除箱子生成點（地圖 #%d，%d, %d, %d，距離 %.1f 格），地圖 #%d 剩 %d 個。",
            bestMap.id, target.getX(), target.getY(), target.getZ(), dist, bestMap.id, bestMap.lootPoints.size()));
    }

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
