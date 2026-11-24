package com.provinggrounds.core;

import com.provinggrounds.config.PGConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 檔案: Hitbox.java
 * 目的: 精準命中判定（伺服器端）。原版玩家的碰撞箱是 0.6 × 0.6 × 1.8 的方柱，前後比人物模型厚很多（模型身體只有 4 像素厚），
 *   子彈擦過胸前、背後的空氣也算打中。這裡在 TACZ 子彈「已經打到碰撞箱、還沒扣血」的時候，
 *   再用人物模型的形狀（頭、身體、兩手、兩腳，跟著身體與頭的方向轉）檢查一次；沒有打到模型就取消這次傷害。
 *   只會「縮小」判定（只剔除打到碰撞箱但沒打到模型的子彈），不會讓原本打不到的子彈變成打得到。
 *   站立、蹲下才用模型判定；趴下、游泳、滑翔、騎乘等姿勢沿用原版碰撞箱。
 *   設定：precise_hitbox（開關）、hitbox_tolerance（每個部位往外放寬幾格，抵銷護甲厚度與網路延遲）。
 */
public final class Hitbox {

    private Hitbox() {}

    /** 一像素（玩家模型縮放 0.9375）。 */
    private static final double PX = 0.9375 / 16.0;

    /**
     * 子彈這一段路線（a → b）有沒有打到人物模型。
     * 不是玩家、或不是站立／蹲下姿勢時一律回傳 true（照原版）。
     */
    public static boolean hitsModel(Entity target, Vec3 a, Vec3 b) {
        if (!(target instanceof Player p)) return true;
        double tol;
        try {
            if (!PGConfig.SERVER.preciseHitbox.get()) return true;
            tol = PGConfig.SERVER.hitboxTolerance.get();
        } catch (IllegalStateException e) {
            return true;
        }
        if (p.isPassenger() || p.isFallFlying() || p.isSleeping()) return true;
        // TACZ 判定命中時會把目標往回推（延遲補償：約 3 tick ＋ 開槍者的延遲），
        // 所以這裡要用目標「最近幾 tick 的位置」逐一檢查，任何一個打到模型就算中（否則移動中的人會打不到）
        if (check(new Snap(p), a, b, tol)) return true;
        java.util.Deque<Snap> hist = HISTORY.get(p.getUUID());
        if (hist != null) for (Snap s : hist) if (check(s, a, b, tol)) return true;
        return false;
    }

    /** 某一個時間點的姿勢與位置。 */
    private record Snap(double x, double y, double z, float bodyYaw, float headYaw, float height, Pose pose) {
        Snap(Player p) { this(p.getX(), p.getY(), p.getZ(), p.yBodyRot, p.yHeadRot, p.getBbHeight(), p.getPose()); }
    }

    /** 每位玩家最近 HISTORY_TICKS 個 tick 的位置（伺服器每 tick 記錄）。 */
    private static final java.util.Map<java.util.UUID, java.util.Deque<Snap>> HISTORY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int HISTORY_TICKS = 12; // 600 毫秒：涵蓋 TACZ 的 3 tick 偏移與最多 250 毫秒延遲，再留一點餘裕

    /** 每 tick 記錄一次（SpecialEvents.onPlayerTick）。 */
    public static void record(Player p) {
        java.util.Deque<Snap> d = HISTORY.computeIfAbsent(p.getUUID(), k -> new java.util.ArrayDeque<>());
        d.addFirst(new Snap(p));
        while (d.size() > HISTORY_TICKS) d.removeLast();
    }

    public static void forget(java.util.UUID id) { HISTORY.remove(id); }

    private static boolean check(Snap s, Vec3 a, Vec3 b, double tol) {
        Pose pose = s.pose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return true; // 趴下、游泳等：照原版碰撞箱
        boolean crouch = pose == Pose.CROUCHING;
        double hScale = s.height() / (32 * PX); // 模型高度縮放到碰撞箱高度
        double y0 = s.y();
        double body = Math.toRadians(s.bodyYaw()), head = Math.toRadians(s.headYaw());
        double[][] parts = parts(crouch);
        for (int i = 0; i < parts.length - 1; i++) {
            if (segmentHitsBox(a, b, s.x(), y0, s.z(), body, parts[i], hScale, tol)) return true;
        }
        return segmentHitsBox(a, b, s.x(), y0, s.z(), head, parts[parts.length - 1], hScale, tol);
    }

    /**
     * 各部位：{中心 x（往右）, 底 y, 中心 z（往前）, 半寬, 高, 半厚}，單位像素。前 5 個跟著身體轉，最後一個（頭）跟著頭轉。
     * 客戶端的除錯畫框（HitboxDebugRenderer）也用這份資料，畫出來的框就是伺服器實際的判定。
     */
    public static double[][] parts(boolean crouch) {
        double depth = crouch ? 8 : 4; // 蹲下時身體前傾，前後比較厚
        return new double[][] {
            { -2, 0, 0, 2, 12, depth / 2 },  // 左腳
            { 2, 0, 0, 2, 12, depth / 2 },   // 右腳
            { 0, 12, 0, 4, 12, depth / 2 },  // 身體
            { -6, 12, 0, 2, 12, 2 },         // 左手
            { 6, 12, 0, 2, 12, 2 },          // 右手
            { 0, 24, 0, 4, 8, 4 },           // 頭
        };
    }

    /** 一像素（格）。 */
    public static double pixel() { return PX; }

    /** 模型高度縮放到碰撞箱高度。 */
    public static double heightScale(float bbHeight) { return bbHeight / (32 * PX); }


    /** 把線段轉到部位自己的座標（繞垂直軸旋轉），再做 AABB 與線段的相交測試。 */
    private static boolean segmentHitsBox(Vec3 a, Vec3 b, double ox, double oy, double oz, double yaw,
                                          double[] part, double hScale, double tol) {
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double[] la = toLocal(a, ox, oy, oz, cos, sin), lb = toLocal(b, ox, oy, oz, cos, sin);
        double minX = (part[0] - part[3]) * PX - tol, maxX = (part[0] + part[3]) * PX + tol;
        double minY = part[1] * PX * hScale - tol, maxY = (part[1] + part[4]) * PX * hScale + tol;
        double minZ = (part[2] - part[5]) * PX - tol, maxZ = (part[2] + part[5]) * PX + tol;
        double t0 = 0, t1 = 1;
        double[] lo = { minX, minY, minZ }, hi = { maxX, maxY, maxZ };
        for (int i = 0; i < 3; i++) {
            double d = lb[i] - la[i];
            if (Math.abs(d) < 1e-9) {
                if (la[i] < lo[i] || la[i] > hi[i]) return false;
                continue;
            }
            double u = (lo[i] - la[i]) / d, v = (hi[i] - la[i]) / d;
            if (u > v) { double t = u; u = v; v = t; }
            t0 = Math.max(t0, u);
            t1 = Math.min(t1, v);
            if (t0 > t1) return false;
        }
        return true;
    }

    /** 世界座標 → 部位座標：x 往右、y 往上、z 往前（面向的方向）。原版面向 = (−sin yaw, cos yaw)。 */
    private static double[] toLocal(Vec3 w, double ox, double oy, double oz, double cos, double sin) {
        double dx = w.x - ox, dz = w.z - oz;
        double fwd = -dx * sin + dz * cos;
        double right = -dx * cos - dz * sin;
        return new double[] { right, w.y - oy, fwd };
    }
}
