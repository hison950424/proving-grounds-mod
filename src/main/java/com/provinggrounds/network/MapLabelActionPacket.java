package com.provinggrounds.network;

import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.data.MapRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 檔案: MapLabelActionPacket.java
 * 目的: 客戶端 → 伺服器。管理員在地圖介面右鍵為大區域命名、改名或刪除區域名稱。
 */
public record MapLabelActionPacket(int mapId, boolean remove, double x, double z, String name) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mapId);
        buf.writeBoolean(remove);
        buf.writeDouble(x);
        buf.writeDouble(z);
        buf.writeUtf(name == null ? "" : name, 32);
    }

    public static MapLabelActionPacket decode(FriendlyByteBuf buf) {
        return new MapLabelActionPacket(
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readUtf(32)
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (!AdminTools.allowed(p)) {
                p.sendSystemMessage(AdminTools.err("只有管理員能編輯區域名稱。"));
                return;
            }
            MapRegistry reg = MapRegistry.get(p.server);
            MapRegistry.BRMap map = reg.getMap(mapId);
            if (map == null) {
                p.sendSystemMessage(AdminTools.err("找不到地圖 #" + mapId + "。"));
                return;
            }
            if (remove) {
                if (reg.removeLabel(map, name)) {
                    MapLabelsPacket.broadcast(p.server, map);
                    p.sendSystemMessage(Component.literal("[地圖] 已刪除區域名稱「" + name + "」。").withStyle(ChatFormatting.YELLOW));
                }
            } else {
                String clean = name.trim();
                if (clean.isEmpty()) return;
                reg.setLabel(map, clean, x, z);
                MapLabelsPacket.broadcast(p.server, map);
                p.sendSystemMessage(Component.literal(String.format("[地圖] 已標記區域「%s」(%.0f, %.0f)。", clean, x, z))
                    .withStyle(ChatFormatting.GREEN));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
