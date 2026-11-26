package com.provinggrounds.core;

import com.provinggrounds.config.PGConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: Hitbox.java
 * 目的: 精準命中判定（伺服器端；計畫書 10 升級為四肢跟隨動畫移動＋支援趴下姿勢）。
 *   - 每個部位用有向包圍盒（PartBox：樞軸點、相對於樞軸的局部範圍、偏航角 yaw、俯仰角 pitch）表示。
 *   - 走路／跑步／蹲下／揮擊／趴下爬行時，左右手與左右腳都會依原版 walkAnimation / attackAnim 擺動。
 *   - 客戶端的 F3+B 除錯畫框（HitboxDebugRenderer）直接使用同一份 computeParts()，確保看到的框與伺服器判定一致。
 */
public final class Hitbox {

    private Hitbox() {}

    /** 一像素（玩家模型縮放 0.9375）。 */
    private static final double PX = 0.9375 / 16.0;

    /**
     * 一個部位的有向包圍盒（單位：格，已乘上 PX 與高度縮放）。
     * 旋轉順序：先在樞軸 (pivX, pivY, pivZ) 局部空間繞 X 軸轉 pitch，再繞 Y 軸轉 yaw。
     * slot：對應的防具部位（HEAD / CHEST / LEGS；腿部細分護腿與靴子依命中高度決定）。
     */
    public record PartBox(
        double pivX, double pivY, double pivZ,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        double yawRad, double pitchRad,
        int kind // 0=左腳, 1=右腳, 2=身體, 3=左手, 4=右手, 5=頭
    ) {}

    /** 某一個時間點的姿勢、位置與四肢動畫狀態。 */
    private record Snap(
        double x, double y, double z,
        float bodyYaw, float headYaw, float headPitch,
        float height, Pose pose,
        float walkPos, float walkSpeed, float attackAnim
    ) {
        Snap(Player p) {
            this(p.getX(), p.getY(), p.getZ(), p.yBodyRot, p.yHeadRot, p.getXRot(),
                p.getBbHeight(), p.getPose(), p.walkAnimation.position(), p.walkAnimation.speed(), p.getAttackAnim(1f));
        }
    }

    /** 每位玩家最近 HISTORY_TICKS 個 tick 的位置（伺服器每 tick 記錄）。 */
    private static final Map<UUID, Deque<Snap>> HISTORY = new ConcurrentHashMap<>();
    private static final int HISTORY_TICKS = 12;

    /** 每 tick 記錄一次（SpecialEvents.onPlayerTick）。 */
    public static void record(Player p) {
        Deque<Snap> d = HISTORY.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        d.addFirst(new Snap(p));
        while (d.size() > HISTORY_TICKS) d.removeLast();
    }

    public static void forget(UUID id) { HISTORY.remove(id); }

    public static double pixel() { return PX; }

    public static double heightScale(float bbHeight) { return bbHeight / (32 * PX); }

    /**
     * 計算玩家 6 個部位（左腳、右腳、身體、左手、右手、頭）的動畫有向包圍盒。
     * 伺服器與客戶端 F3+B 共用這套算式。
     */
    public static List<PartBox> computeParts(Pose pose, float bbHeight, float bodyYawDeg, float headYawDeg, float headPitchDeg,
                                             float walkPos, float walkSpeed, float attackAnim) {
        double bodyYaw = Math.toRadians(bodyYawDeg);
        double headYaw = Math.toRadians(headYawDeg);
        double headPitch = Math.toRadians(Mth.clamp(headPitchDeg, -85f, 85f));
        float spd = Math.min(1.0f, walkSpeed);

        if (pose == Pose.SWIMMING) {
            // 趴下／游泳姿勢：全身水平朝向 bodyYaw 趴在地面上（頭在前 +z、軀幹居中、兩腿在後 -z），爬行時四肢微幅擺動
            double crawlArm = Math.cos(walkPos * 0.6662) * 0.35 * spd;
            double crawlLeg = Math.cos(walkPos * 0.6662) * 0.25 * spd;
            double cy = 0.24; // 趴下中心高度約 0.24 格（貼地）
            double p90 = Math.PI / 2.0; // 平躺基準（+y 轉到 +z 前方）
            List<PartBox> out = new ArrayList<>(6);
            // 0 左腳（從骨盆往後伸）
            out.add(new PartBox(-2 * PX, cy, -5 * PX, -2 * PX, -12 * PX, -2 * PX, 2 * PX, 0, 2 * PX, bodyYaw, p90 + crawlLeg, 0));
            // 1 右腳
            out.add(new PartBox(2 * PX, cy, -5 * PX, -2 * PX, -12 * PX, -2 * PX, 2 * PX, 0, 2 * PX, bodyYaw, p90 - crawlLeg, 1));
            // 2 身體（居中平躺）
            out.add(new PartBox(0, cy, -5 * PX, -4 * PX, 0, -2 * PX, 4 * PX, 12 * PX, 2 * PX, bodyYaw, p90, 2));
            // 3 左手（從肩膀往前／旁伸）
            out.add(new PartBox(-6 * PX, cy, 5 * PX, -2 * PX, -12 * PX, -2 * PX, 2 * PX, 0, 2 * PX, bodyYaw, p90 - crawlArm, 3));
            // 4 右手
            out.add(new PartBox(6 * PX, cy, 5 * PX, -2 * PX, -12 * PX, -2 * PX, 2 * PX, 0, 2 * PX, bodyYaw, p90 + crawlArm, 4));
            // 5 頭（在身體最前方）
            double hPitch = Math.toRadians(Mth.clamp(headPitchDeg * 0.5f, -45f, 45f));
            out.add(new PartBox(0, cy + 0.04, 7 * PX, -4 * PX, -4 * PX, 0, 4 * PX, 4 * PX, 8 * PX, headYaw, hPitch, 5));
            return out;
        }

        boolean crouch = pose == Pose.CROUCHING;
        double hs = crouch ? (bbHeight / (28.0 * PX)) : heightScale(bbHeight);
        // 原版 HumanoidModel 擺動角度（弧度）：walkPos * 0.6662
        double rightLegSwing = Math.cos(walkPos * 0.6662) * 1.4 * spd;
        double leftLegSwing  = Math.cos(walkPos * 0.6662 + Math.PI) * 1.4 * spd;
        double rightArmSwing = Math.cos(walkPos * 0.6662 + Math.PI) * 1.0 * spd;
        double leftArmSwing  = Math.cos(walkPos * 0.6662) * 1.0 * spd;
        if (attackAnim > 0.01f) {
            rightArmSwing += Math.sin(attackAnim * Math.PI) * 1.3; // 揮拳／揮刀時右手往前舉
        }
        // 在我們的局部空間中：+y 向上、+z 向前；手臂／腿從樞軸往下伸（minY < 0），所以 pitch = -swing 讓末端往前擺
        double hipY = (crouch ? 10.5 : 12.0) * PX * hs;
        double hipZ = crouch ? -3.0 * PX : 0.0;
        double shoulderY = (crouch ? 20.5 : 22.0) * PX * hs;
        double shoulderZ = crouch ? 1.0 * PX : 0.0;
        double neckY = (crouch ? 21.5 : 24.0) * PX * hs;
        double neckZ = crouch ? 1.5 * PX : 0.0;
        double torsoPitch = crouch ? 0.45 : 0.0; // 蹲下時軀幹前傾

        List<PartBox> out = new ArrayList<>(6);
        // 0 左腳（樞軸在髖關節，往下 12 像素）
        out.add(new PartBox(-2 * PX, hipY, hipZ, -2 * PX, -12 * PX * hs, -2 * PX, 2 * PX, 0, 2 * PX, bodyYaw, -leftLegSwing, 0));
        // 1 右腳
        out.add(new PartBox(2 * PX, hipY, hipZ, -2 * PX, -12 * PX * hs, -2 * PX, 2 * PX, 0, 2 * PX, bodyYaw, -rightLegSwing, 1));
        // 2 身體（樞軸在腰部，往上 12 像素）
        out.add(new PartBox(0, hipY, hipZ, -4 * PX, 0, -2 * PX, 4 * PX, 12 * PX * hs, 2 * PX, bodyYaw, torsoPitch, 2));
        // 3 左手（樞軸在左肩，往上 2 像素、往下 10 像素）
        out.add(new PartBox(-6 * PX, shoulderY, shoulderZ, -2 * PX, -10 * PX * hs, -2 * PX, 2 * PX, 2 * PX * hs, 2 * PX, bodyYaw, -leftArmSwing + (crouch ? 0.25 : 0), 3));
        // 4 右手
        out.add(new PartBox(6 * PX, shoulderY, shoulderZ, -2 * PX, -10 * PX * hs, -2 * PX, 2 * PX, 2 * PX * hs, 2 * PX, bodyYaw, -rightArmSwing + (crouch ? 0.25 : 0), 4));
        // 5 頭（樞軸在脖子，往上 8 像素，跟隨 headYaw 與 headPitch）
        out.add(new PartBox(0, neckY, neckZ, -4 * PX, 0, -4 * PX, 4 * PX, 8 * PX * hs, 4 * PX, headYaw, headPitch, 5));
        return out;
    }

    /** 子彈路線（a → b）有沒有打到人物模型。 */
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
        if (check(new Snap(p), a, b, tol)) return true;
        Deque<Snap> hist = HISTORY.get(p.getUUID());
        if (hist != null) for (Snap s : hist) if (check(s, a, b, tol)) return true;
        return false;
    }

    private static boolean check(Snap s, Vec3 a, Vec3 b, double tol) {
        Pose pose = s.pose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return true; // 趴下／游泳：原版碰撞箱已極小，避免誤漏判定
        List<PartBox> parts = computeParts(pose, s.height(), s.bodyYaw(), s.headYaw(), s.headPitch(), s.walkPos(), s.walkSpeed(), s.attackAnim());
        for (PartBox part : parts) {
            if (segmentEntry(a, b, s.x(), s.y(), s.z(), part, tol) != null) return true;
        }
        return false;
    }

    /** 子彈打到哪個防具部位（頭盔／胸甲／護腿／靴子）。支援站立、蹲下與趴下姿勢。 */
    @Nullable
    public static EquipmentSlot armorSlotHit(Entity target, Vec3 a, Vec3 b) {
        if (!(target instanceof Player p) || p.isPassenger() || p.isFallFlying() || p.isSleeping()) return null;
        double tol;
        try { tol = PGConfig.SERVER.hitboxTolerance.get(); } catch (IllegalStateException e) { tol = 0.06; }
        List<Snap> snaps = new ArrayList<>();
        snaps.add(new Snap(p));
        Deque<Snap> hist = HISTORY.get(p.getUUID());
        if (hist != null) snaps.addAll(hist);
        for (Snap s : snaps) {
            Pose pose = s.pose();
            if (pose != Pose.STANDING && pose != Pose.CROUCHING && pose != Pose.SWIMMING) return null;
            List<PartBox> parts = computeParts(pose, s.height(), s.bodyYaw(), s.headYaw(), s.headPitch(), s.walkPos(), s.walkSpeed(), s.attackAnim());
            PartBox best = null;
            double bestT = 2, bestLocalY = 0;
            for (PartBox part : parts) {
                double[] hit = segmentEntry(a, b, s.x(), s.y(), s.z(), part, tol);
                if (hit != null && hit[0] < bestT) { bestT = hit[0]; bestLocalY = hit[1]; best = part; }
            }
            if (best == null) continue;
            if (best.kind() == 5) return EquipmentSlot.HEAD;
            if (best.kind() >= 2) return EquipmentSlot.CHEST;
            // 腿的局部 y 是 [-12*PX, 0]：下 1/3（< -8*PX）算靴子，其餘算護腿
            return bestLocalY < best.minY() + (best.maxY() - best.minY()) * 0.34 ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
        }
        return null;
    }

    /** 把世界點轉進 PartBox 的局部座標（繞 Y 轉 -yaw、減樞軸、繞 X 轉 -pitch），回傳 {進入點 t, 局部 y}。 */
    @Nullable
    private static double[] segmentEntry(Vec3 a, Vec3 b, double ox, double oy, double oz, PartBox p, double tol) {
        double[] la = worldToPart(a, ox, oy, oz, p);
        double[] lb = worldToPart(b, ox, oy, oz, p);
        double[] lo = { p.minX() - tol, p.minY() - tol, p.minZ() - tol };
        double[] hi = { p.maxX() + tol, p.maxY() + tol, p.maxZ() + tol };
        double t0 = 0, t1 = 1;
        for (int i = 0; i < 3; i++) {
            double d = lb[i] - la[i];
            if (Math.abs(d) < 1e-9) {
                if (la[i] < lo[i] || la[i] > hi[i]) return null;
                continue;
            }
            double u = (lo[i] - la[i]) / d, v = (hi[i] - la[i]) / d;
            if (u > v) { double t = u; u = v; v = t; }
            t0 = Math.max(t0, u);
            t1 = Math.min(t1, v);
            if (t0 > t1) return null;
        }
        return new double[] { t0, la[1] + (lb[1] - la[1]) * t0 };
    }

    /** 世界座標 → 部位樞軸局部座標。 */
    private static double[] worldToPart(Vec3 w, double ox, double oy, double oz, PartBox p) {
        double dx = w.x - ox, dy = w.y - oy, dz = w.z - oz;
        double cy = Math.cos(p.yawRad()), sy = Math.sin(p.yawRad());
        // 玩家局部座標：right = (-cos, -sin), fwd = (-sin, cos)
        double rx = -dx * cy - dz * sy - p.pivX();
        double ry = dy - p.pivY();
        double rz = -dx * sy + dz * cy - p.pivZ();
        // 逆向繞 X 軸轉 (-pitch)
        double cp = Math.cos(p.pitchRad()), sp = Math.sin(p.pitchRad());
        double py = ry * cp + rz * sp;
        double pz = -ry * sp + rz * cp;
        return new double[] { rx, py, pz };
    }

    /** 部位樞軸局部點 (lx, ly, lz) → 相對於 (ox, oy, oz) 的世界偏移座標（HitboxDebugRenderer 畫框用）。 */
    public static double[] partToWorld(double ox, double oy, double oz, PartBox p, double lx, double ly, double lz) {
        // 繞 X 軸轉 +pitch
        double cp = Math.cos(p.pitchRad()), sp = Math.sin(p.pitchRad());
        double ry = ly * cp - lz * sp + p.pivY();
        double rz = ly * sp + lz * cp + p.pivZ();
        double rx = lx + p.pivX();
        // 繞 Y 軸轉回世界座標（right = (-cos, -sin), fwd = (-sin, cos)）
        double cy = Math.cos(p.yawRad()), sy = Math.sin(p.yawRad());
        return new double[] {
            ox + rx * -cy + rz * -sy,
            oy + ry,
            oz + rx * -sy + rz * cy
        };
    }
}
