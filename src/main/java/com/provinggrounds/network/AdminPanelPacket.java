package com.provinggrounds.network;

import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.data.BRMapGenerator;
import com.provinggrounds.data.MapRegistry;
import com.provinggrounds.data.MapRelightJob;
import com.provinggrounds.data.MapRenderJob;
import com.provinggrounds.data.MarkerScanJob;
import com.provinggrounds.modes.arena.ArenaMapGenerator;
import com.provinggrounds.modes.arena.ArenaMapRegistry;
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
 *   - 客戶端 → 伺服器（open = false）：傳送操作代碼與參數，由伺服器端重新驗證權限並安全轉執行對應管理指令。
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
            String cmd = resolveCommand(action, arg);
            if (cmd == null) {
                p.sendSystemMessage(AdminTools.err("未知管理動作：" + action));
                return;
            }
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2), cmd);
        });
        c.setPacketHandled(true);
    }

    private static String resolveCommand(String action, String arg) {
        String cleanWord = arg.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return switch (action) {
            case "br_gen_all" -> "pg map generate all";
            case "br_gen_regen" -> "pg map generate regen";
            case "br_gen_one" -> cleanWord.isEmpty() ? null : "pg map generate " + cleanWord;
            case "br_relight" -> "pg map relight";
            case "br_render" -> cleanWord.matches("\\d+") ? "pg map render " + cleanWord : null;
            case "br_cancel" -> {
                if (BRMapGenerator.isRunning()) yield "pg map generate cancel";
                if (MapRelightJob.isRunning()) yield "pg map relight cancel";
                if (MapRenderJob.isRunning()) yield "pg map render cancel";
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
