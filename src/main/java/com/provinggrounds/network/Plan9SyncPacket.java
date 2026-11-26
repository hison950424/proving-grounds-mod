package com.provinggrounds.network;

import com.provinggrounds.special.SpecialHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * 檔案: Plan9SyncPacket.java
 * 目的: 伺服器 → 客戶端。同步計畫書 9 新特殊道具的視覺狀態：
 *   - disguised：中了「敵我不分」的玩家 UUID（客戶端顯示史蒂夫、隱藏護甲與名條）
 *   - ethereal：進入「無實體化」的玩家 UUID（半透明幽靈＋穿過 1 格方塊）
 *   - mimics：進入「擬態」的玩家 UUID → 物資箱種類（0 木箱、1 鐵箱、2 金箱）
 *   - domains：正在展開的「領域展開」球體（中心、半徑、剩餘 tick）
 */
public record Plan9SyncPacket(
    List<UUID> disguised,
    List<UUID> ethereal,
    Map<UUID, Integer> mimics,
    List<DomainInfo> domains,
    Map<UUID, Byte> freeSkydive
) {
    public record DomainInfo(double x, double y, double z, double radius, int leftTicks) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(disguised.size());
        for (UUID id : disguised) buf.writeUUID(id);
        buf.writeVarInt(ethereal.size());
        for (UUID id : ethereal) buf.writeUUID(id);
        buf.writeVarInt(mimics.size());
        for (Map.Entry<UUID, Integer> e : mimics.entrySet()) {
            buf.writeUUID(e.getKey());
            buf.writeVarInt(e.getValue());
        }
        buf.writeVarInt(domains.size());
        for (DomainInfo d : domains) {
            buf.writeDouble(d.x());
            buf.writeDouble(d.y());
            buf.writeDouble(d.z());
            buf.writeDouble(d.radius());
            buf.writeVarInt(d.leftTicks());
        }
        buf.writeVarInt(freeSkydive.size());
        for (Map.Entry<UUID, Byte> e : freeSkydive.entrySet()) {
            buf.writeUUID(e.getKey());
            buf.writeByte(e.getValue());
        }
    }

    public static Plan9SyncPacket decode(FriendlyByteBuf buf) {
        int nd = Math.min(buf.readVarInt(), 256);
        List<UUID> disguised = new ArrayList<>(nd);
        for (int i = 0; i < nd; i++) disguised.add(buf.readUUID());
        int ne = Math.min(buf.readVarInt(), 256);
        List<UUID> ethereal = new ArrayList<>(ne);
        for (int i = 0; i < ne; i++) ethereal.add(buf.readUUID());
        int nm = Math.min(buf.readVarInt(), 256);
        Map<UUID, Integer> mimics = new HashMap<>(nm);
        for (int i = 0; i < nm; i++) mimics.put(buf.readUUID(), buf.readVarInt());
        int ndom = Math.min(buf.readVarInt(), 32);
        List<DomainInfo> domains = new ArrayList<>(ndom);
        for (int i = 0; i < ndom; i++) {
            domains.add(new DomainInfo(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt()));
        }
        int nf = Math.min(buf.readVarInt(), 256);
        Map<UUID, Byte> freeSkydive = new HashMap<>(nf);
        for (int i = 0; i < nf; i++) freeSkydive.put(buf.readUUID(), buf.readByte());
        return new Plan9SyncPacket(disguised, ethereal, mimics, domains, freeSkydive);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            SpecialHooks.CLIENT_DISGUISED.clear();
            SpecialHooks.CLIENT_DISGUISED.addAll(disguised);
            SpecialHooks.CLIENT_ETHEREAL.clear();
            SpecialHooks.CLIENT_ETHEREAL.addAll(ethereal);
            SpecialHooks.CLIENT_MIMIC.clear();
            SpecialHooks.CLIENT_MIMIC.putAll(mimics);
            com.provinggrounds.special.client.ClientPlan9Specials.setDomains(domains);
            com.provinggrounds.special.client.ClientPlan9Specials.setFreeSkydive(freeSkydive);
        }));
        ctx.get().setPacketHandled(true);
    }
}
