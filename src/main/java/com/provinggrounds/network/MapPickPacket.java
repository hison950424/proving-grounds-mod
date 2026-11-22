package com.provinggrounds.network;

import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.item.AdminToolItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 檔案: MapPickPacket.java
 * 目的: 箱子轉換工具、地圖繪畫工具的「選擇地圖」（雙向）：
 *   伺服器 → 客戶端（open = true）：已登記的地圖清單，開啟選擇畫面；
 *   客戶端 → 伺服器（open = false）：玩家選的地圖編號。伺服器重新檢查權限後執行。
 */
public record MapPickPacket(boolean open, AdminToolItem.Tool tool, List<Entry> maps, int chosen) {

    public record Entry(int id, String name, int points) {}

    public static MapPickPacket open(AdminToolItem.Tool tool, List<Entry> maps) {
        return new MapPickPacket(true, tool, maps, 0);
    }

    public static MapPickPacket choose(AdminToolItem.Tool tool, int mapId) {
        return new MapPickPacket(false, tool, List.of(), mapId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeEnum(tool);
        buf.writeVarInt(chosen);
        buf.writeVarInt(maps.size());
        for (Entry e : maps) {
            buf.writeVarInt(e.id());
            buf.writeUtf(e.name(), 128);
            buf.writeVarInt(e.points());
        }
    }

    public static MapPickPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        AdminToolItem.Tool tool = buf.readEnum(AdminToolItem.Tool.class);
        int chosen = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 64);
        List<Entry> maps = new ArrayList<>(n);
        for (int i = 0; i < n; i++) maps.add(new Entry(buf.readVarInt(), buf.readUtf(128), buf.readVarInt()));
        return new MapPickPacket(open, tool, maps, chosen);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (open) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.provinggrounds.ui.MapPickScreen.open(tool, maps));
                return;
            }
            ServerPlayer p = c.getSender();
            if (p != null) AdminTools.runMapTool(p, tool, chosen);
        });
        c.setPacketHandled(true);
    }
}
