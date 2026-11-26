package com.provinggrounds.special;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: SpecialHooks.java
 * 目的: Mixin 與跨端程式呼叫的小工具（兩端共用，不碰任何只在客戶端的類別）。
 */
public final class SpecialHooks {

    public static final Set<Integer> CLIENT_GLOWING = ConcurrentHashMap.newKeySet();
    /** 客戶端同步：中了「敵我不分」的玩家 UUID。 */
    public static final Set<UUID> CLIENT_DISGUISED = ConcurrentHashMap.newKeySet();
    /** 客戶端同步：進入「無實體化」的玩家 UUID。 */
    public static final Set<UUID> CLIENT_ETHEREAL = ConcurrentHashMap.newKeySet();
    /** 客戶端同步：正在「擬態」的玩家 UUID → 箱子類型（0 木箱、1 鐵箱、2 金箱）。 */
    public static final Map<UUID, Integer> CLIENT_MIMIC = new ConcurrentHashMap<>();

    private SpecialHooks() {}

    /** 靜音靴：這個玩家的移動聲音要不要靜音。 */
    public static boolean silent(Entity e) {
        return e instanceof Player && e instanceof LivingEntity l && l.hasEffect(SpecialRegistry.SILENT.get());
    }

    /** 敵我不分：任一方有此效果時強制開啟友軍誤傷。 */
    public static boolean forceFriendlyFire(Entity a, Entity b) {
        return (a instanceof LivingEntity la && la.hasEffect(SpecialRegistry.FRIEND_OR_FOE.get()))
            || (b instanceof LivingEntity lb && lb.hasEffect(SpecialRegistry.FRIEND_OR_FOE.get()));
    }

    /** 無實體化：兩端都能問（自己靠效果、別人靠 CLIENT_ETHEREAL）。 */
    public static boolean isEthereal(Entity e) {
        if (e instanceof LivingEntity l && l.hasEffect(SpecialRegistry.ETHEREAL.get())) return true;
        return e != null && e.level().isClientSide && CLIENT_ETHEREAL.contains(e.getUUID());
    }

    /** 客戶端：畫面上這名玩家是否要顯示為史蒂夫（當本地玩家也中了敵我不分，且對象也在敵我不分名單中）。 */
    public static boolean showAsSteve(Player viewer, Entity target) {
        if (viewer == null || target == null || viewer == target) return false;
        if (!viewer.hasEffect(SpecialRegistry.FRIEND_OR_FOE.get())) return false;
        return target instanceof Player && CLIENT_DISGUISED.contains(target.getUUID());
    }

    /** 客戶端目前同步到的領域結界（用來在 Entity.collide 做實體方塊般的邊界碰撞）。 */
    public record DomainBarrier(double x, double y, double z, double radius, Set<UUID> inside) {}
    public static volatile java.util.List<DomainBarrier> CLIENT_DOMAINS = java.util.List.of();

    public static void setClientDomains(java.util.List<com.provinggrounds.network.Plan9SyncPacket.DomainInfo> list) {
        java.util.List<DomainBarrier> out = new java.util.ArrayList<>(list.size());
        for (var d : list) out.add(new DomainBarrier(d.x(), d.y(), d.z(), d.radius(), Set.copyOf(d.inside())));
        CLIENT_DOMAINS = java.util.List.copyOf(out);
    }

    public static void clearClient() {
        CLIENT_GLOWING.clear();
        CLIENT_DISGUISED.clear();
        CLIENT_ETHEREAL.clear();
        CLIENT_MIMIC.clear();
        CLIENT_DOMAINS = java.util.List.of();
    }

    /**
     * 領域展開實體牆壁碰撞（EntitySpecialMixin 在 Entity.collide 回傳時呼叫，客戶端與伺服器共用同一套算法）：
     * 讓玩家撞到領域球殼邊界時像撞到方塊一樣立刻停下（斜走可沿球面滑行），不會有被伺服器拉回的橡皮筋感。
     */
    public static net.minecraft.world.phys.Vec3 clampDomainCollision(Entity self, net.minecraft.world.phys.Vec3 step) {
        if (!(self instanceof Player p) || !p.isAlive() || p.isSpectator()) return step;
        java.util.List<DomainBarrier> list = self.level().isClientSide
            ? CLIENT_DOMAINS
            : Plan9Specials.barriersIn(self.level());
        if (list.isEmpty()) return step;

        final double margin = 0.40; // 略大於玩家半寬 0.3，確保鏡頭不穿牆
        net.minecraft.world.phys.Vec3 cur = p.position();
        net.minecraft.world.phys.Vec3 s = step;
        for (DomainBarrier d : list) {
            boolean in = d.inside().contains(p.getUUID());
            net.minecraft.world.phys.Vec3 next = cur.add(s);
            double nx = next.x - d.x(), ny = next.y - d.y(), nz = next.z - d.z();
            double dist = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (in) {
                double limit = Math.max(1.0, d.radius() - margin);
                if (dist <= limit) continue;
                // 在地面平走時保持 Y 不被往下壓進地板，只把水平位置貼齊球殼切面
                if (p.onGround() && Math.abs(ny) < limit - 0.1) {
                    double rHoriz = Math.sqrt(Math.max(0.01, limit * limit - ny * ny));
                    double hDist = Math.hypot(nx, nz);
                    if (hDist > rHoriz && hDist > 1e-6) {
                        double tx = d.x() + nx / hDist * rHoriz;
                        double tz = d.z() + nz / hDist * rHoriz;
                        s = new net.minecraft.world.phys.Vec3(tx - cur.x, s.y, tz - cur.z);
                        continue;
                    }
                }
                double scale = limit / Math.max(1e-6, dist);
                double tx = d.x() + nx * scale, ty = d.y() + ny * scale, tz = d.z() + nz * scale;
                s = new net.minecraft.world.phys.Vec3(tx - cur.x, ty - cur.y, tz - cur.z);
            } else {
                double limit = d.radius() + margin;
                if (dist >= limit) continue;
                if (p.onGround() && Math.abs(ny) < limit - 0.1) {
                    double rHoriz = Math.sqrt(Math.max(0.01, limit * limit - ny * ny));
                    double hDist = Math.hypot(nx, nz);
                    if (hDist < rHoriz) {
                        double dirX = hDist > 1e-6 ? nx / hDist : 1.0;
                        double dirZ = hDist > 1e-6 ? nz / hDist : 0.0;
                        double tx = d.x() + dirX * rHoriz;
                        double tz = d.z() + dirZ * rHoriz;
                        s = new net.minecraft.world.phys.Vec3(tx - cur.x, s.y, tz - cur.z);
                        continue;
                    }
                }
                if (dist < 1e-6) {
                    s = new net.minecraft.world.phys.Vec3(d.x() + limit - cur.x, s.y, d.z() - cur.z);
                } else {
                    double scale = limit / dist;
                    double tx = d.x() + nx * scale, ty = d.y() + ny * scale, tz = d.z() + nz * scale;
                    s = new net.minecraft.world.phys.Vec3(tx - cur.x, ty - cur.y, tz - cur.z);
                }
            }
        }
        return s;
    }
}
