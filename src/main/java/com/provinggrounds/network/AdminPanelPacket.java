package com.provinggrounds.network;

import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.data.BRMapGenerator;
import com.provinggrounds.data.BRMapPresets;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.data.MapRelightJob;
import com.provinggrounds.data.MapRenderJob;
import com.provinggrounds.data.MarkerScanJob;
import com.provinggrounds.modes.arena.ArenaMapGenerator;
import com.provinggrounds.modes.arena.ArenaMapRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: AdminPanelPacket.java
 * 目的: 管理員控制台工具（AdminToolItem.Tool.ADMIN_PANEL）雙向封包：
 *   - 伺服器 → 客戶端（open = true）：傳送目前大逃殺地圖清單、團隊場地清單與背景作業狀態並開啟 UI；
 *   - 客戶端 → 伺服器（open = false）：傳送操作代碼與參數，由伺服器端重新驗證權限並安全執行對應管理功能。
 */
public record AdminPanelPacket(
    boolean open,
    String activeArenaId,
    String jobStatus,
    List<MapPickPacket.Entry> brMaps,
    List<MenuDataPacket.ArenaMapEntry> arenaMaps,
    String action,
    String arg
) {

    public static AdminPanelPacket open(MinecraftServer server) {
        List<MapPickPacket.Entry> br = new ArrayList<>();
        for (MapRegistry.BRMap m : MapRegistry.get(server).all()) {
            br.add(new MapPickPacket.Entry(m.id, m.name == null ? "" : m.name, m.lootPoints.size()));
        }
        ArenaMapRegistry areg = ArenaMapRegistry.get(server);
        List<MenuDataPacket.ArenaMapEntry> arenas = new ArrayList<>();
        arenas.add(new MenuDataPacket.ArenaMapEntry(ArenaMapRegistry.DEFAULT_ID, "經典場地（設定檔）"));
        for (var am : areg.customMaps()) {
            arenas.add(new MenuDataPacket.ArenaMapEntry(am.id(), am.name()));
        }
        String status = BRMapGenerator.isRunning() ? BRMapGenerator.statusText()
            : MapRelightJob.isRunning() ? MapRelightJob.statusText()
            : MapRenderJob.isRunning() ? "正在繪製大地圖俯視圖…"
            : MarkerScanJob.isRunning() ? "正在掃描轉換舊箱子標記…"
            : ArenaMapGenerator.isRunning() ? ArenaMapGenerator.statusText()
            : "無背景作業（閒置）";
        return new AdminPanelPacket(true, areg.activeId(), status, List.copyOf(br), List.copyOf(arenas), "", "");
    }

    public static AdminPanelPacket run(String action, String arg) {
        return new AdminPanelPacket(false, "", "", List.of(), List.of(), action, arg == null ? "" : arg);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeUtf(activeArenaId == null ? "" : activeArenaId, 64);
        buf.writeUtf(jobStatus == null ? "" : jobStatus, 160);
        buf.writeVarInt(brMaps.size());
        for (var e : brMaps) {
            buf.writeVarInt(e.id());
            buf.writeUtf(e.name(), 64);
            buf.writeVarInt(e.points());
        }
        buf.writeVarInt(arenaMaps.size());
        for (var a : arenaMaps) {
            buf.writeUtf(a.id(), 64);
            buf.writeUtf(a.name(), 64);
        }
        buf.writeUtf(action == null ? "" : action, 64);
        buf.writeUtf(arg == null ? "" : arg, 64);
    }

    public static AdminPanelPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        String activeArena = buf.readUtf(64);
        String jobStatus = buf.readUtf(160);
        int bn = Math.min(buf.readVarInt(), 128);
        List<MapPickPacket.Entry> br = new ArrayList<>(bn);
        for (int i = 0; i < bn; i++) br.add(new MapPickPacket.Entry(buf.readVarInt(), buf.readUtf(64), buf.readVarInt()));
        int an = Math.min(buf.readVarInt(), 64);
        List<MenuDataPacket.ArenaMapEntry> arenas = new ArrayList<>(an);
        for (int i = 0; i < an; i++) arenas.add(new MenuDataPacket.ArenaMapEntry(buf.readUtf(64), buf.readUtf(64)));
        String action = buf.readUtf(64);
        String arg = buf.readUtf(64);
        return new AdminPanelPacket(open, activeArena, jobStatus, List.copyOf(br), List.copyOf(arenas), action, arg);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (open) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.provinggrounds.ui.AdminPanelScreen.open(this));
                return;
            }
            ServerPlayer p = c.getSender();
            if (p == null) return;
            if (!AdminTools.allowed(p)) {
                p.sendSystemMessage(AdminTools.err("沒有權限（只有 OP 或創造模式能使用）。"));
                return;
            }
            CommandSourceStack src = p.createCommandSourceStack().withPermission(2);
            if ("br_regen_layers".equals(action)) {
                handleRegenLayers(p, src, arg);
                return;
            }
            if ("br_tp".equals(action)) {
                handleBrTp(p, arg);
                return;
            }
            if ("br_preview".equals(action)) {
                handleBrPreview(p, arg);
                return;
            }
            if ("sync_chunks".equals(action)) {
                handleSyncChunks(p, arg);
                return;
            }
            if (handleToolAction(p, action, arg)) {
                return;
            }
            String cmd = resolveCommand(action, arg);
            if (cmd == null) {
                p.sendSystemMessage(AdminTools.err("未知管理動作：" + action));
                return;
            }
            p.server.getCommands().performPrefixedCommand(src, cmd);
        });
        c.setPacketHandled(true);
    }

    /**
     * 獨立的「修復無形方塊（幽靈方塊／空氣牆）」：瞬間將選定地圖範圍內（或玩家周圍 12 區塊）
     * 所有已載入區塊的完整方塊與光照重新推播給觀看該區塊的玩家，不重算光照引擎。
     */
    private static void handleSyncChunks(ServerPlayer sender, String arg) {
        var level = sender.serverLevel();
        var chunkSource = level.getChunkSource();
        var light = chunkSource.getLightEngine();
        int sentChunks = 0;
        int sentPackets = 0;

        MapRegistry.BRMap map = null;
        if (arg != null && arg.trim().matches("\\d+")) {
            map = MapRegistry.get(sender.server).getMap(Integer.parseInt(arg.trim()));
        }

        if (map != null && level == sender.server.overworld()) {
            int reach = (int) Math.ceil(map.radius) + 16;
            int minCX = ((int) Math.floor(map.centerX) - reach) >> 4;
            int maxCX = ((int) Math.ceil(map.centerX) + reach) >> 4;
            int minCZ = ((int) Math.floor(map.centerZ) - reach) >> 4;
            int maxCZ = ((int) Math.ceil(map.centerZ) + reach) >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    var chunk = chunkSource.getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    var cp = chunk.getPos();
                    List<ServerPlayer> watchers = chunkSource.chunkMap.getPlayers(cp, false);
                    if (watchers.isEmpty()) continue;
                    var pkt = new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(chunk, light, null, null);
                    for (ServerPlayer wp : watchers) {
                        wp.connection.send(pkt);
                        sentPackets++;
                    }
                    sentChunks++;
                }
            }
            sender.sendSystemMessage(AdminTools.ok(String.format(
                "已修復地圖 #%d「%s」的無形方塊：重新同步 %d 個已載入區塊（%d 份封包）。",
                map.id, map.name, sentChunks, sentPackets)));
            return;
        }

        int pcx = sender.blockPosition().getX() >> 4;
        int pcz = sender.blockPosition().getZ() >> 4;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                var chunk = chunkSource.getChunkNow(pcx + dx, pcz + dz);
                if (chunk == null) continue;
                var cp = chunk.getPos();
                List<ServerPlayer> watchers = chunkSource.chunkMap.getPlayers(cp, false);
                if (watchers.isEmpty()) continue;
                var pkt = new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(chunk, light, null, null);
                for (ServerPlayer wp : watchers) {
                    wp.connection.send(pkt);
                    sentPackets++;
                }
                sentChunks++;
            }
        }
        sender.sendSystemMessage(AdminTools.ok(String.format(
            "已修復周圍無形方塊：重新同步 %d 個已載入區塊（%d 份封包）。", sentChunks, sentPackets)));
    }

    /** 同步指定大逃殺地圖的俯視圖、祭壇位置、大區域名稱與地圖座標給管理員，供控制台直接預覽大地圖。 */
    private static void handleBrPreview(ServerPlayer p, String arg) {
        int mapId;
        try {
            mapId = Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            return;
        }
        MapRegistry.BRMap map = MapRegistry.get(p.server).getMap(mapId);
        if (map == null) {
            p.sendSystemMessage(AdminTools.err("找不到地圖 #" + mapId + "。"));
            return;
        }
        PGNetwork.sendTo(p, MenuRequestPacket.build(p));
        com.provinggrounds.data.MapImageStore.MapImage img = com.provinggrounds.data.MapImageStore.get(p.server, map.id);
        if (img != null) MapImagePacket.sendTo(p, img);
        else PGNetwork.sendTo(p, new MapImagePacket(map.id, 0, 0, 0, 0, 1, 0, 1, new byte[0]));
        MapLabelsPacket.sendTo(p, map);
    }

    /** 整合既有 10 種管理工具：不需準星指方塊的直接觸發，需預覽框／準星對準的可單獨領取到背包。 */
    private static boolean handleToolAction(ServerPlayer p, String action, String arg) {
        switch (action) {
            case "tool_float_text" -> PGNetwork.sendTo(p, FloatTextPacket.openScreen());
            case "tool_text_eraser" -> p.sendSystemMessage(com.provinggrounds.admin.FloatText.deleteNearest(p));
            case "tool_text_undo" -> p.sendSystemMessage(com.provinggrounds.admin.FloatText.undo(p.server));
            case "tool_spawn_viewer" -> p.sendSystemMessage(com.provinggrounds.admin.SpawnPointView.toggle(p));
            case "tool_crate_converter" -> AdminTools.openMapPicker(p, com.provinggrounds.item.AdminToolItem.Tool.CRATE_CONVERTER);
            case "tool_map_painter" -> AdminTools.openMapPicker(p, com.provinggrounds.item.AdminToolItem.Tool.MAP_PAINTER);
            case "tool_building_pick" -> {
                String id = com.provinggrounds.admin.build.BuildingCatalog.selectedId(p.getUUID());
                PGNetwork.sendTo(p, BuildingPacket.openUi(id, com.provinggrounds.admin.build.BuildingCatalog.undoCount()));
            }
            case "tool_building_undo" -> p.sendSystemMessage(com.provinggrounds.admin.build.BuildingCatalog.undo(p));
            case "tool_give_one" -> {
                String clean = arg.replaceAll("[^a-zA-Z0-9_]", "");
                for (com.provinggrounds.item.AdminToolItem.Tool t : com.provinggrounds.item.AdminToolItem.Tool.values()) {
                    if (t.id.equals(clean)) {
                        net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(
                            com.provinggrounds.registry.PGBlocks.ADMIN_TOOLS.get(t).get());
                        if (!p.getInventory().add(st)) p.drop(st, false);
                        p.sendSystemMessage(AdminTools.ok("已領取工具：" + st.getHoverName().getString() + "。"));
                        return true;
                    }
                }
                p.sendSystemMessage(AdminTools.err("找不到工具：" + clean));
            }
            default -> { return false; }
        }
        return true;
    }

    /** 傳送管理員至指定大逃殺地圖中央（創造／旁觀停在安全高空俯瞰，其餘安全落於地表上方並附短暫緩降）。 */
    private static void handleBrTp(ServerPlayer p, String arg) {
        int mapId;
        try {
            mapId = Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            p.sendSystemMessage(AdminTools.err("無效的地圖編號：" + arg));
            return;
        }
        MapRegistry.BRMap map = MapRegistry.get(p.server).getMap(mapId);
        if (map == null) {
            p.sendSystemMessage(AdminTools.err("找不到地圖 #" + mapId + "。"));
            return;
        }
        var overworld = p.server.overworld();
        int bx = (int) Math.floor(map.centerX);
        int bz = (int) Math.floor(map.centerZ);
        double ty;
        if (p.isCreative() || p.isSpectator()) {
            ty = 120.0;
        } else {
            overworld.getChunk(bx >> 4, bz >> 4);
            int sy = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            ty = Math.max(66, sy + 1);
        }
        p.teleportTo(overworld, map.centerX, ty, map.centerZ, p.getYRot(), p.getXRot());
        p.sendSystemMessage(AdminTools.ok("已傳送至地圖 #" + map.id + "「" + map.name + "」中央 (" + bx + ", " + (int) ty + ", " + bz + ")。"));
    }

    /** arg 格式："<mapId|all>:<layerMask>"，例如 "2:6" 表示只重製地圖 #2 的小區域＋野區。 */
    private static void handleRegenLayers(ServerPlayer p, CommandSourceStack src, String arg) {
        String[] parts = arg.split(":");
        String target = parts.length > 0 ? parts[0].trim() : "";
        int mask = BRMapGenerator.LAYER_ALL;
        if (parts.length > 1) {
            try {
                mask = Integer.parseInt(parts[1].trim()) & BRMapGenerator.LAYER_ALL;
            } catch (NumberFormatException ignored) {}
        }
        if (mask == 0) {
            p.sendSystemMessage(AdminTools.err("請至少勾選一個要重製的區域（大區域／小區域／野區／小掩體）。"));
            return;
        }
        String err;
        if ("all".equalsIgnoreCase(target)) {
            err = BRMapGenerator.startRegenAllLayers(p.server, mask, src);
        } else {
            int mapId;
            try {
                mapId = Integer.parseInt(target);
            } catch (NumberFormatException e) {
                p.sendSystemMessage(AdminTools.err("無效的地圖編號：" + target));
                return;
            }
            MapRegistry.BRMap map = MapRegistry.get(p.server).getMap(mapId);
            if (map == null) {
                p.sendSystemMessage(AdminTools.err("找不到地圖 #" + mapId + "。"));
                return;
            }
            BRMapPresets.Preset preset = BRMapGenerator.matchPreset(map);
            if (preset == null) {
                p.sendSystemMessage(AdminTools.err("地圖 #" + mapId + "「" + map.name + "」無法對應到內建主題預設，請用指令指定預設：/pg map generate regen " + mapId + " <預設>。"));
                return;
            }
            err = BRMapGenerator.startRegenLayers(p.server, mapId, preset, mask, src);
        }
        if (err != null) {
            p.sendSystemMessage(AdminTools.err(err));
        }
    }

    private static String resolveCommand(String action, String arg) {
        String cleanWord = arg.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return switch (action) {
            case "br_gen_all" -> "pg map generate all";
            case "br_gen_regen" -> "pg map generate regen";
            case "br_gen_one" -> cleanWord.isEmpty() ? null : "pg map generate " + cleanWord;
            case "br_relight" -> "pg map relight";
            case "br_relight_one" -> cleanWord.matches("\\d+") ? "pg map relight " + cleanWord : "pg map relight";
            case "br_render" -> cleanWord.matches("\\d+") ? "pg map render " + cleanWord : null;
            case "br_delete" -> cleanWord.matches("\\d+") ? "pg map remove " + cleanWord : null;
            case "br_cancel" -> {
                if (BRMapGenerator.isRunning()) yield "pg map generate cancel";
                if (MapRelightJob.isRunning()) yield "pg map relight cancel";
                if (MapRenderJob.isRunning()) yield "pg map render cancel";
                if (MarkerScanJob.isRunning()) yield "pg loot markers cancel";
                if (ArenaMapGenerator.isRunning()) yield "pg arena map cancel";
                yield "pg map generate status";
            }
            case "arena_gen" -> "pg arena map generate";
            case "arena_regen" -> "pg arena map regen";
            case "arena_select" -> cleanWord.isEmpty() ? null : "pg arena map select " + cleanWord;
            case "arena_tp" -> cleanWord.isEmpty() ? "pg arena map tp" : "pg arena map tp " + cleanWord;
            case "arena_info" -> "pg arena info";
            case "glass_restore" -> "pg glass restore";
            case "reload" -> "pg reload";
            case "reset" -> "pg reset";
            case "status" -> "pg status";
            case "give_tools" -> "pg give tool all";
            default -> null;
        };
    }
}
