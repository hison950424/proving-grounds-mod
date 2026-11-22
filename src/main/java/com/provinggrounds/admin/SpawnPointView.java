package com.provinggrounds.admin;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.SpawnPointsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 檔案: SpawnPointView.java
 * 目的: 顯示／隱藏箱子生成點（計畫書 B9）。只有切換的那位管理員看得到（伺服器只把點位送給他）。
 *   點位有變動（B8 物品、/pg loot add／remove／clear／import）時 MapRegistry 呼叫 changed()，
 *   下一個 tick 把新的點位送給所有正在顯示的人。狀態只存在記憶體，登出就關閉，不寫進存檔。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID)
public final class SpawnPointView {

    private static final Set<UUID> viewers = new HashSet<>();
    private static boolean dirty;

    private SpawnPointView() {}

    /** 切換顯示。回傳給玩家的訊息。 */
    public static Component toggle(ServerPlayer p) {
        if (viewers.remove(p.getUUID())) {
            PGNetwork.sendTo(p, new SpawnPointsPacket(false, List.of()));
            return AdminTools.ok("已隱藏箱子生成點。");
        }
        viewers.add(p.getUUID());
        List<BlockPos> points = allPoints(p.server);
        PGNetwork.sendTo(p, new SpawnPointsPacket(true, points));
        return AdminTools.ok("已顯示 " + points.size() + " 個箱子生成點（再右鍵一次隱藏）。");
    }

    /** 點位變動：通知正在顯示的人更新。 */
    public static void changed() {
        dirty = true;
    }

    private static List<BlockPos> allPoints(MinecraftServer server) {
        List<BlockPos> out = new ArrayList<>();
        for (MapRegistry.BRMap m : MapRegistry.get(server).all()) out.addAll(m.lootPoints);
        return out;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !dirty) return;
        dirty = false;
        if (viewers.isEmpty()) return;
        List<BlockPos> points = allPoints(event.getServer());
        for (UUID id : viewers) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            if (p != null) PGNetwork.sendTo(p, new SpawnPointsPacket(true, points));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        viewers.remove(event.getEntity().getUUID());
    }

    public static void clear() {
        viewers.clear();
        dirty = false;
    }
}
