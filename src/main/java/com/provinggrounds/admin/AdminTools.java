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
}
