package com.provinggrounds.core;

import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.network.ChuteSyncPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 檔案: Hitbox.java
 * 目的: 精準命中判定與姿勢計算中心（伺服器＋客戶端共用同一套數學）。
 *   - 使用與原版 Minecraft LivingEntityRenderer + PlayerRenderer + ModelPart 完全一致的 Matrix4f 轉換鏈：
 *     RY(180° - bodyYaw) → 貼地趴下平移與旋轉 → scale(0.9375) → scale(-1, -1, 1) → translate(0, -1.501, 0)
 *     → 各部位樞軸 translate(x/16, y/16, z/16) → RZ(zRot) * RY(yRot) * RX(xRot)。
 *   - 走路擺動、蹲下、揮擊、拿原版物品／舉盾／拉弓、拿 TACZ 槍械與開鏡、降落傘舉手、
 *     以及陸地趴下過渡（0→1 與 1→0）與爬行動畫，全部與客戶端模型渲染 100% 同步。
 *   - 客戶端 ProneAnimMixin、HumanoidArmsMixin、HitboxDebugRenderer 與伺服器子彈判定全部共用這套算式。
 */
public final class Hitbox {

    private Hitbox() {}

    /** 一像素（玩家模型縮放 0.9375）。 */
    private static final double PX = 0.9375 / 16.0;

    /** 陸地趴下完全平躺時的貼地偏移（套用在 RX(-90°) 之前，任何過渡幀都不會插進地面）。 */
    public static final float PRONE_Y = 0.24f;
    public static final float PRONE_Z = 1.05f;

    /** 趴下／站起的平滑曲線（smoothstep），過渡柔和且頭尾速度平緩。 */
    public static float proneEase(float swimAmount) {
        float t = Mth.clamp(swimAmount, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** 對應 Minecraft ModelPart 的單一部位樞軸與歐拉角（單位：像素與弧度）。 */
    public static final class PartTransform {
        public float x, y, z;
        public float xRot, yRot, zRot;

        public PartTransform(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** 玩家 6 個部位的模型空間姿勢（與 HumanoidModel 欄位一一對應）。 */
    public static final class LimbPose {
        public final PartTransform head     = new PartTransform(0.0f, 0.0f, 0.0f);
        public final PartTransform body     = new PartTransform(0.0f, 0.0f, 0.0f);
        public final PartTransform rightArm = new PartTransform(-5.0f, 2.0f, 0.0f);
        public final PartTransform leftArm  = new PartTransform(5.0f, 2.0f, 0.0f);
        public final PartTransform rightLeg = new PartTransform(-1.9f, 12.0f, 0.0f);
        public final PartTransform leftLeg  = new PartTransform(1.9f, 12.0f, 0.0f);
    }

    /** 計算姿勢所需的全部輸入參數（純資料，伺服器與客戶端皆可用）。 */
    public record PoseInput(
        boolean crouching,
        boolean inWater,
        float swimAmount,
        float netHeadYawDeg,
        float headPitchDeg,
        float walkPos,
        float walkSpeed,
        float attackAnim,
        boolean rightHanded,
        boolean holdsGun,
        float gunAimProgress,
        boolean mainHasItem,
        boolean offHasItem,
        UseAnim mainUseAnim,
        UseAnim offUseAnim,
        boolean chuteOpen
    ) {
        public static PoseInput fromPlayer(Player p, float pt, float bodyYawDeg, float headYawDeg, float headPitchDeg) {
            boolean holdsGun = GunCompat.holdsGun(p);
            float aim = holdsGun ? GunCompat.aimProgress(p) : 0.0f;
            UseAnim mainUse = (p.isUsingItem() && p.getUsedItemHand() == InteractionHand.MAIN_HAND)
                ? p.getUseItem().getUseAnimation() : UseAnim.NONE;
            UseAnim offUse = (p.isUsingItem() && p.getUsedItemHand() == InteractionHand.OFF_HAND)
                ? p.getUseItem().getUseAnimation() : UseAnim.NONE;
            return new PoseInput(
                p.isCrouching(),
                p.isInWater(),
                p.getSwimAmount(pt),
                Mth.wrapDegrees(headYawDeg - bodyYawDeg),
                headPitchDeg,
                p.walkAnimation.position(pt),
                p.walkAnimation.speed(pt),
                p.getAttackAnim(pt),
                p.getMainArm() == HumanoidArm.RIGHT,
                holdsGun,
                aim,
                !p.getMainHandItem().isEmpty(),
                !p.getOffhandItem().isEmpty(),
                mainUse,
                offUse,
                ChuteSyncPacket.isOpen(p.getId())
            );
        }
    }

    /**
     * 一個部位的有向包圍盒：
     * partToEntity 把部位局部方塊座標（像素 / 16）轉換到以玩家腳底為原點的世界偏移座標。
     * kind：0=左腳, 1=右腳, 2=身體, 3=左手, 4=右手, 5=頭。
     */
    public record PartBox(
        Matrix4f partToEntity,
        float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ,
        int kind
    ) {}

    /** 某一個時間點的完整玩家快照（伺服器每 tick 記錄）。 */
    private record Snap(
        double x, double y, double z,
        float bodyYaw,
        Pose pose,
        PoseInput input
    ) {
        static Snap capture(Player p) {
            float bodyYaw = p.yBodyRot;
            float headYaw = p.yHeadRot;
            if (!p.isInWater() && (p.getPose() == Pose.SWIMMING || ProneManager.isProne(p.getUUID()))) {
                bodyYaw = p.getYRot();
                headYaw = p.getYRot();
            }
            return new Snap(
                p.getX(), p.getY(), p.getZ(),
                bodyYaw,
                p.getPose(),
                PoseInput.fromPlayer(p, 1.0f, bodyYaw, headYaw, p.getXRot())
            );
        }
    }

    /** 每位玩家最近 HISTORY_TICKS 個 tick 的狀態（伺服器每 tick 記錄）。 */
    private static final Map<UUID, Deque<Snap>> HISTORY = new ConcurrentHashMap<>();
    private static final int HISTORY_TICKS = 12;

    /** 每 tick 記錄一次（WorldEvents / SpecialEvents 呼叫），順便同步陸地趴下者的身體轉向。 */
    public static void record(Player p) {
        if (!p.isInWater() && !p.isFallFlying() && (p.getPose() == Pose.SWIMMING || ProneManager.isProne(p.getUUID()))) {
            p.yBodyRot = p.getYRot();
            p.yBodyRotO = p.yRotO;
        }
        Deque<Snap> d = HISTORY.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        d.addFirst(Snap.capture(p));
        while (d.size() > HISTORY_TICKS) d.removeLast();
    }

    public static void forget(UUID id) { HISTORY.remove(id); }

    public static double pixel() { return PX; }

    /**
     * 計算玩家 6 個部位在原版模型空間中的姿勢（與 HumanoidModel.setupAnim + TACZ + 陸地趴下完全一致）。
     * HumanoidArmsMixin（客戶端渲染）與 Hitbox.computeParts（命中判定與 F3+B）共用此函式。
     */
    public static LimbPose computeLimbPose(PoseInput in) {
        LimbPose p = new LimbPose();
        float degToRad = (float) Math.PI / 180.0f;
        float headYawRad = in.netHeadYawDeg() * degToRad;
        float headPitchRad = Mth.clamp(in.headPitchDeg(), -89.0f, 89.0f) * degToRad;
        float spd = Math.min(1.0f, Math.max(0.0f, in.walkSpeed()));

        // 1. 頭部朝向
        p.head.yRot = headYawRad;
        p.head.xRot = headPitchRad;

        // 2. 原版走路四肢擺動（與 HumanoidModel.setupAnim 100% 相同）
        p.rightArm.xRot = Mth.cos(in.walkPos() * 0.6662f + (float) Math.PI) * 2.0f * spd * 0.5f;
        p.leftArm.xRot  = Mth.cos(in.walkPos() * 0.6662f) * 2.0f * spd * 0.5f;
        p.rightLeg.xRot = Mth.cos(in.walkPos() * 0.6662f) * 1.4f * spd;
        p.leftLeg.xRot  = Mth.cos(in.walkPos() * 0.6662f + (float) Math.PI) * 1.4f * spd;

        // 3. 原版手持物品／使用物品姿勢（未持槍時套用）
        boolean crouchActive = in.crouching() && in.swimAmount() < 0.01f;
        if (!in.holdsGun()) {
            PartTransform mainArm = in.rightHanded() ? p.rightArm : p.leftArm;
            PartTransform offArm  = in.rightHanded() ? p.leftArm  : p.rightArm;
            boolean mainIsRight = in.rightHanded();
            boolean offIsRight  = !in.rightHanded();

            // 副手先套用，再套用主手（雙手動作如拉弓／弩由主動手覆蓋）
            applyItemArmPose(offArm, mainArm, p.head, offIsRight, in.offHasItem(), in.offUseAnim(), crouchActive);
            applyItemArmPose(mainArm, offArm, p.head, mainIsRight, in.mainHasItem(), in.mainUseAnim(), crouchActive);
        }

        // 4. 原版攻擊揮手動畫（未持槍時套用）
        if (!in.holdsGun() && in.attackAnim() > 0.001f) {
            applyAttackAnimation(p, in.attackAnim(), in.rightHanded());
        }

        // 5. 原版蹲下姿勢（未進入趴下時）
        if (crouchActive) {
            p.body.xRot = 0.5f;
            p.rightArm.xRot += 0.4f;
            p.leftArm.xRot += 0.4f;
            p.rightLeg.z = 4.0f;
            p.leftLeg.z = 4.0f;
            p.rightLeg.y = 12.2f;
            p.leftLeg.y = 12.2f;
            p.head.y = 4.2f;
            p.body.y = 3.2f;
            p.leftArm.y = 5.2f;
            p.rightArm.y = 5.2f;
        }

        // 6. TACZ 第三人稱持槍／開鏡姿勢（與 ThirdPersonManager$1 完全一致，套用在蹲下之後）
        if (in.holdsGun() && !in.chuteOpen()) {
            float aim = Mth.clamp(in.gunAimProgress(), 0.0f, 1.0f);
            float yOff = Mth.lerp(aim, 0.3f, 0.35f);
            float xOff = Mth.lerp(aim, 1.4f, 1.6f);
            p.rightArm.yRot = -yOff + p.head.yRot;
            p.leftArm.yRot  =  0.8f + p.head.yRot;
            p.rightArm.xRot = -xOff + p.head.xRot;
            p.leftArm.xRot  = -xOff + p.head.xRot;
            p.rightArm.zRot = 0.0f;
            p.leftArm.zRot  = 0.0f;
        }

        // 7. 降落傘舉手
        if (in.chuteOpen()) {
            p.rightArm.xRot = (float) Math.PI + 0.15f;
            p.leftArm.xRot  = (float) Math.PI + 0.15f;
            p.rightArm.yRot = 0.0f;
            p.leftArm.yRot  = 0.0f;
            p.rightArm.zRot = 0.35f;
            p.leftArm.zRot  = -0.35f;
        }

        // 8. 陸地趴下過渡與爬行動畫（e = 0 站立 → e = 1 完全趴下，過渡期間平滑插值，絕不瞬切）
        if (!in.inWater() && in.swimAmount() > 0.0001f) {
            float e = proneEase(in.swimAmount());
            float phase = in.walkPos() * 0.6662f;

            // 頭：平躺時抬頭朝前看（不低頭插地），並隨上下視角微幅俯仰
            float proneHeadX = Mth.clamp(-1.15f + headPitchRad * 0.45f, -1.55f, -0.35f);
            p.head.xRot = Mth.lerp(e, p.head.xRot, proneHeadX);
            p.head.yRot = Mth.lerp(e, p.head.yRot, headYawRad * 0.5f);
            p.head.zRot = 0.0f;
            p.head.y = Mth.lerp(e, p.head.y, 0.0f);

            // 軀幹：平直貼地
            p.body.xRot = Mth.lerp(e, p.body.xRot, 0.0f);
            p.body.yRot = Mth.lerp(e, p.body.yRot, 0.0f);
            p.body.zRot = 0.0f;
            p.body.y = Mth.lerp(e, p.body.y, 0.0f);

            // 雙腿：自然微外八貼地，爬行時雙腳交替微抬蹬地（不往下插進地板）
            float legKickR = 0.12f + Math.max(0.0f, Mth.cos(phase)) * 0.32f * spd;
            float legKickL = 0.12f + Math.max(0.0f, Mth.cos(phase + (float) Math.PI)) * 0.32f * spd;
            p.rightLeg.xRot = Mth.lerp(e, p.rightLeg.xRot, legKickR);
            p.leftLeg.xRot  = Mth.lerp(e, p.leftLeg.xRot, legKickL);
            p.rightLeg.yRot = Mth.lerp(e, p.rightLeg.yRot, 0.0f);
            p.leftLeg.yRot  = Mth.lerp(e, p.leftLeg.yRot, 0.0f);
            p.rightLeg.zRot = Mth.lerp(e, p.rightLeg.zRot,  0.14f);
            p.leftLeg.zRot  = Mth.lerp(e, p.leftLeg.zRot, -0.14f);
            p.rightLeg.y = Mth.lerp(e, p.rightLeg.y, 12.0f);
            p.leftLeg.y  = Mth.lerp(e, p.leftLeg.y, 12.0f);
            p.rightLeg.z = Mth.lerp(e, p.rightLeg.z, 0.0f);
            p.leftLeg.z  = Mth.lerp(e, p.leftLeg.z, 0.0f);

            // 雙手樞軸回到標準肩位
            p.rightArm.x = Mth.lerp(e, p.rightArm.x, -5.0f);
            p.rightArm.y = Mth.lerp(e, p.rightArm.y,  2.0f);
            p.rightArm.z = Mth.lerp(e, p.rightArm.z,  0.0f);
            p.leftArm.x  = Mth.lerp(e, p.leftArm.x,   5.0f);
            p.leftArm.y  = Mth.lerp(e, p.leftArm.y,   2.0f);
            p.leftArm.z  = Mth.lerp(e, p.leftArm.z,   0.0f);

            if (!in.chuteOpen()) {
                if (in.holdsGun()) {
                    // 趴下持槍：整個趴下／站起過程槍口持續朝前瞄準，雙手內收托槍
                    float aim = Mth.clamp(in.gunAimProgress(), 0.0f, 1.0f);
                    float gunPitch = Mth.clamp(headPitchRad * 0.55f, -0.50f, 0.30f);
                    float proneGunX = -(float) Math.PI - Mth.lerp(aim, 0.16f, 0.24f) + gunPitch;
                    p.rightArm.xRot = Mth.lerp(e, p.rightArm.xRot, proneGunX);
                    p.leftArm.xRot  = Mth.lerp(e, p.leftArm.xRot, proneGunX);
                    p.rightArm.yRot = Mth.lerp(e, p.rightArm.yRot, 0.0f);
                    p.leftArm.yRot  = Mth.lerp(e, p.leftArm.yRot, 0.0f);
                    p.rightArm.zRot = Mth.lerp(e, p.rightArm.zRot,  0.22f);
                    p.leftArm.zRot  = Mth.lerp(e, p.leftArm.zRot, -0.52f);
                } else {
                    // 趴下未持槍：雙手往前撐地，移動時呈低姿匍匐交替划動
                    float crawlR = Mth.cos(phase + (float) Math.PI) * 0.45f * spd;
                    float crawlL = Mth.cos(phase) * 0.45f * spd;
                    float armRX = -2.96f - Math.abs(crawlR) * 0.28f;
                    float armLX = -2.96f - Math.abs(crawlL) * 0.28f;
                    float armRZ = -0.22f - crawlR * 0.50f;
                    float armLZ =  0.22f + crawlL * 0.50f;
                    p.rightArm.xRot = Mth.lerp(e, p.rightArm.xRot, armRX);
                    p.leftArm.xRot  = Mth.lerp(e, p.leftArm.xRot, armLX);
                    p.rightArm.yRot = Mth.lerp(e, p.rightArm.yRot, 0.0f);
                    p.leftArm.yRot  = Mth.lerp(e, p.leftArm.yRot, 0.0f);
                    p.rightArm.zRot = Mth.lerp(e, p.rightArm.zRot, armRZ);
                    p.leftArm.zRot  = Mth.lerp(e, p.leftArm.zRot, armLZ);
                }
            }
        }

        return p;
    }

    private static void applyItemArmPose(PartTransform arm, PartTransform otherArm, PartTransform head,
                                         boolean isRight, boolean hasItem, UseAnim use, boolean crouch) {
        if (use == UseAnim.BLOCK) {
            arm.xRot = arm.xRot * 0.5f - 0.9424779f;
            arm.yRot = isRight ? -0.5235988f : 0.5235988f;
        } else if (use == UseAnim.BOW) {
            arm.yRot = (isRight ? -0.1f : 0.1f) + head.yRot;
            otherArm.yRot = (isRight ? 0.5f : -0.5f) + head.yRot;
            arm.xRot = -((float) Math.PI / 2.0f) + head.xRot;
            otherArm.xRot = -((float) Math.PI / 2.0f) + head.xRot;
        } else if (use == UseAnim.CROSSBOW) {
            arm.yRot = (isRight ? -0.3f : 0.3f) + head.yRot;
            otherArm.yRot = (isRight ? 0.6f : -0.6f) + head.yRot;
            arm.xRot = -((float) Math.PI / 2.0f) + head.xRot + 0.1f;
            otherArm.xRot = -1.5f + head.xRot;
        } else if (use == UseAnim.SPYGLASS) {
            arm.xRot = Mth.clamp(head.xRot - 1.9198622f - (crouch ? 0.2617994f : 0.0f), -2.4f, 3.3f);
            arm.yRot = head.yRot + (isRight ? -0.2617994f : 0.2617994f);
        } else if (use == UseAnim.SPEAR) {
            arm.xRot = arm.xRot * 0.5f - (float) Math.PI;
            arm.yRot = 0.0f;
        } else if (hasItem) {
            arm.xRot = arm.xRot * 0.5f - ((float) Math.PI / 10.0f);
            arm.yRot = 0.0f;
        }
    }

    private static void applyAttackAnimation(LimbPose p, float attackAnim, boolean rightHanded) {
        float f = 1.0f - attackAnim;
        f = f * f;
        f = f * f;
        f = 1.0f - f;
        float f1 = Mth.sin(f * (float) Math.PI);
        float f2 = Mth.sin(attackAnim * (float) Math.PI) * -(p.head.xRot - 0.7f) * 0.75f;

        p.body.yRot = Mth.sin(Mth.sqrt(attackAnim) * ((float) Math.PI * 2.0f)) * 0.2f;
        if (!rightHanded) p.body.yRot *= -1.0f;

        p.rightArm.z = Mth.sin(p.body.yRot) * 5.0f;
        p.rightArm.x = -Mth.cos(p.body.yRot) * 5.0f;
        p.leftArm.z  = -Mth.sin(p.body.yRot) * 5.0f;
        p.leftArm.x  = Mth.cos(p.body.yRot) * 5.0f;
        p.rightArm.yRot += p.body.yRot;
        p.leftArm.yRot  += p.body.yRot;
        p.leftArm.xRot  += p.body.yRot;

        PartTransform main = rightHanded ? p.rightArm : p.leftArm;
        main.xRot -= f1 * 1.2f + f2;
        main.yRot += p.body.yRot * 2.0f;
        main.zRot += Mth.sin(attackAnim * (float) Math.PI) * -0.4f;
    }

    /** 依玩家當下狀態計算 6 個部位的精準動畫有向包圍盒（客戶端 F3+B 直接呼叫）。 */
    public static List<PartBox> computeParts(Player p, float pt) {
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float headPitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        PoseInput in = PoseInput.fromPlayer(p, pt, bodyYaw, headYaw, headPitch);
        return computeParts(bodyYaw, in);
    }

    /** 依指定的 bodyYaw 與 PoseInput 計算 6 個部位的有向包圍盒（伺服器與客戶端共用）。 */
    public static List<PartBox> computeParts(float bodyYawDeg, PoseInput in) {
        LimbPose pose = computeLimbPose(in);

        // 與 LivingEntityRenderer.render + PlayerRenderer.getRenderOffset / setupRotations / ProneAnimMixin 完全相同的變換矩陣
        Matrix4f root = new Matrix4f();
        if (in.crouching() && in.swimAmount() < 0.01f) {
            root.translate(0.0f, -0.125f, 0.0f); // 原版 PlayerRenderer.getRenderOffset：蹲下時整個人物往下偏移 0.125 格
        }
        root.rotateY((float) Math.toRadians(180.0f - bodyYawDeg));
        if (!in.inWater() && in.swimAmount() > 0.0001f) {
            float e = proneEase(in.swimAmount());
            root.translate(0.0f, PRONE_Y * e, PRONE_Z * e);
            root.rotateX((float) Math.toRadians(-90.0f * e));
        } else if (in.inWater() && in.swimAmount() > 0.0001f) {
            float f3 = -90.0f - in.headPitchDeg();
            float f4 = Mth.lerp(in.swimAmount(), 0.0f, f3);
            root.rotateX((float) Math.toRadians(f4));
            root.translate(0.0f, -1.0f, 0.3f);
        }
        root.scale(0.9375f, 0.9375f, 0.9375f);
        root.scale(-1.0f, -1.0f, 1.0f);
        root.translate(0.0f, -1.501f, 0.0f);

        List<PartBox> out = new ArrayList<>(6);
        // 標準 Minecraft PlayerModel 像素方盒（單位：像素 / 16）
        out.add(buildPart(root, pose.leftLeg,  -2f,  0f, -2f,  2f, 12f, 2f, 0));
        out.add(buildPart(root, pose.rightLeg, -2f,  0f, -2f,  2f, 12f, 2f, 1));
        out.add(buildPart(root, pose.body,     -4f,  0f, -2f,  4f, 12f, 2f, 2));
        out.add(buildPart(root, pose.leftArm,  -1f, -2f, -2f,  3f, 10f, 2f, 3));
        out.add(buildPart(root, pose.rightArm, -3f, -2f, -2f,  1f, 10f, 2f, 4));
        out.add(buildPart(root, pose.head,     -4f, -8f, -4f,  4f,  0f, 4f, 5));
        return out;
    }

    private static PartBox buildPart(Matrix4f root, PartTransform pt,
                                     float minXpx, float minYpx, float minZpx,
                                     float maxXpx, float maxYpx, float maxZpx, int kind) {
        Matrix4f m = new Matrix4f(root);
        m.translate(pt.x / 16.0f, pt.y / 16.0f, pt.z / 16.0f);
        if (pt.zRot != 0.0f) m.rotateZ(pt.zRot);
        if (pt.yRot != 0.0f) m.rotateY(pt.yRot);
        if (pt.xRot != 0.0f) m.rotateX(pt.xRot);
        return new PartBox(m,
            minXpx / 16.0f, minYpx / 16.0f, minZpx / 16.0f,
            maxXpx / 16.0f, maxYpx / 16.0f, maxZpx / 16.0f,
            kind);
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
        if (check(Snap.capture(p), a, b, tol)) return true;
        Deque<Snap> hist = HISTORY.get(p.getUUID());
        if (hist != null) for (Snap s : hist) if (check(s, a, b, tol)) return true;
        return false;
    }

    private static boolean check(Snap s, Vec3 a, Vec3 b, double tol) {
        Pose pose = s.pose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING && pose != Pose.SWIMMING) return true;
        // 趴下過半後原版碰撞箱已縮成 0.6×0.6 貼地小框，避免誤漏判定
        if (s.input().swimAmount() > 0.5f) return true;
        List<PartBox> parts = computeParts(s.bodyYaw(), s.input());
        for (PartBox part : parts) {
            if (segmentEntry(a, b, s.x(), s.y(), s.z(), part, tol) != null) return true;
        }
        return false;
    }

    /** 子彈打到哪個防具部位（頭盔／胸甲／護腿／靴子）。支援站立、蹲下、趴下與所有過渡狀態。 */
    @Nullable
    public static EquipmentSlot armorSlotHit(Entity target, Vec3 a, Vec3 b) {
        if (!(target instanceof Player p) || p.isPassenger() || p.isFallFlying() || p.isSleeping()) return null;
        double tol;
        try { tol = PGConfig.SERVER.hitboxTolerance.get(); } catch (IllegalStateException e) { tol = 0.06; }
        List<Snap> snaps = new ArrayList<>();
        snaps.add(Snap.capture(p));
        Deque<Snap> hist = HISTORY.get(p.getUUID());
        if (hist != null) snaps.addAll(hist);
        for (Snap s : snaps) {
            Pose pose = s.pose();
            if (pose != Pose.STANDING && pose != Pose.CROUCHING && pose != Pose.SWIMMING) return null;
            List<PartBox> parts = computeParts(s.bodyYaw(), s.input());
            PartBox best = null;
            double bestT = 2.0, bestLocalY = 0.0;
            for (PartBox part : parts) {
                double[] hit = segmentEntry(a, b, s.x(), s.y(), s.z(), part, tol);
                if (hit != null && hit[0] < bestT) { bestT = hit[0]; bestLocalY = hit[1]; best = part; }
            }
            if (best == null) continue;
            if (best.kind() == 5) return EquipmentSlot.HEAD;
            if (best.kind() >= 2) return EquipmentSlot.CHEST;
            // 原版模型空間中 +Y 向下：腿部 y ∈ [0, 12/16]，下 1/3（> 8/16）算靴子，其餘算護腿
            return bestLocalY > best.minY() + (best.maxY() - best.minY()) * 0.66 ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
        }
        return null;
    }

    /** 把世界線段 (a → b) 用反矩陣轉進 PartBox 的局部模型空間做 AABB 相交，回傳 {進入點 t, 局部 y}。 */
    @Nullable
    private static double[] segmentEntry(Vec3 a, Vec3 b, double ox, double oy, double oz, PartBox p, double tol) {
        Matrix4f inv = new Matrix4f(p.partToEntity()).invert();
        Vector3f la = inv.transformPosition((float) (a.x - ox), (float) (a.y - oy), (float) (a.z - oz), new Vector3f());
        Vector3f lb = inv.transformPosition((float) (b.x - ox), (float) (b.y - oy), (float) (b.z - oz), new Vector3f());
        float ltol = (float) (tol / 0.9375);
        double[] pa = { la.x, la.y, la.z };
        double[] pb = { lb.x, lb.y, lb.z };
        double[] lo = { p.minX() - ltol, p.minY() - ltol, p.minZ() - ltol };
        double[] hi = { p.maxX() + ltol, p.maxY() + ltol, p.maxZ() + ltol };
        double t0 = 0.0, t1 = 1.0;
        for (int i = 0; i < 3; i++) {
            double d = pb[i] - pa[i];
            if (Math.abs(d) < 1e-9) {
                if (pa[i] < lo[i] || pa[i] > hi[i]) return null;
                continue;
            }
            double u = (lo[i] - pa[i]) / d, v = (hi[i] - pa[i]) / d;
            if (u > v) { double tmp = u; u = v; v = tmp; }
            t0 = Math.max(t0, u);
            t1 = Math.min(t1, v);
            if (t0 > t1) return null;
        }
        return new double[] { t0, pa[1] + (pb[1] - pa[1]) * t0 };
    }

    /** 部位局部點 (lx, ly, lz) → 相對於 (ox, oy, oz) 的世界偏移座標（HitboxDebugRenderer 畫框用）。 */
    public static double[] partToWorld(double ox, double oy, double oz, PartBox p, float lx, float ly, float lz) {
        Vector3f v = p.partToEntity().transformPosition(lx, ly, lz, new Vector3f());
        return new double[] { ox + v.x, oy + v.y, oz + v.z };
    }
}
