package com.provinggrounds.special.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.ClientGunAim;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.network.Plan9SyncPacket;
import com.provinggrounds.registry.PGBlocks;
import com.provinggrounds.special.SpecialHooks;
import com.provinggrounds.special.SpecialRegistry;
import com.provinggrounds.ui.DamageIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 檔案: ClientPlan9Specials.java
 * 目的: 計畫書 9 新增特殊道具的客戶端表現：
 *   - 強制抬頭（欸你看／欸你們看）
 *   - 領域展開（半徑 20 格半透明紫色圓頂結界）
 *   - 敵我不分（隱藏名條；史蒂夫外觀與隱藏護甲在 PlayerSkinMixin／ArmorHideMixin）
 *   - 被害妄想症（隨機假受擊方向指示器＋地圖假紅點）
 *   - 走火詛咒（持槍強制開火）
 *   - 溜冰鞋（放開方向鍵不減速，撞到方塊停下）
 *   - 顯示干擾器（隱藏所有抬頭顯示器）
 *   - 擬態（把玩家畫成物資箱方塊、隱藏名條）
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientPlan9Specials {

    private static volatile List<Plan9SyncPacket.DomainInfo> domains = List.of();

    /** 被害妄想症的假敵人地圖標記。 */
    public record FakeEnemy(double x, double z, float yaw, int age) {}
    private static final List<FakeEnemy> FAKE_ENEMIES = new ArrayList<>();
    private static int paranoiaHitCd = 0;

    /** 溜冰鞋滑行速度。 */
    private static double slideX = 0, slideZ = 0;
    private static boolean sliding = false;

    private ClientPlan9Specials() {}

    public static void setDomains(List<Plan9SyncPacket.DomainInfo> list) {
        domains = List.copyOf(list);
    }

    public static List<FakeEnemy> fakeEnemies() {
        return FAKE_ENEMIES;
    }

    public static void clear() {
        domains = List.of();
        FAKE_ENEMIES.clear();
        slideX = slideZ = 0;
        sliding = false;
        SpecialHooks.clearClient();
    }

    public static void forceLookUp() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.setXRot(-90f);
        p.xRotO = -90f;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { clear(); return; }

        // 走火詛咒：拿著槍就強制開火
        if (p.hasEffect(SpecialRegistry.MISFIRE.get()) && mc.screen == null && GunCompat.holdsGun(p)) {
            ClientGunAim.forceShoot(p);
        }

        // 被害妄想症：假受擊方向指示器＋假敵人地圖紅點
        if (p.hasEffect(SpecialRegistry.PARANOIA.get())) {
            var rnd = p.getRandom();
            if (--paranoiaHitCd <= 0) {
                paranoiaHitCd = 15 + rnd.nextInt(30);
                double ang = rnd.nextDouble() * Math.PI * 2;
                Vec3 fakeFrom = p.position().add(Math.cos(ang) * 15, 0, Math.sin(ang) * 15);
                DamageIndicator.addFakeHit(fakeFrom);
                p.playSound(SoundEvents.PLAYER_HURT, 0.25f, 0.9f + rnd.nextFloat() * 0.2f);
            }
            if (p.tickCount % 12 == 0) {
                if (FAKE_ENEMIES.size() < 4 || rnd.nextInt(3) == 0) {
                    if (FAKE_ENEMIES.size() >= 4) FAKE_ENEMIES.remove(0);
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    double dist = 8 + rnd.nextDouble() * 28;
                    FAKE_ENEMIES.add(new FakeEnemy(p.getX() + Math.cos(ang) * dist, p.getZ() + Math.sin(ang) * dist, rnd.nextFloat() * 360f, 0));
                }
            }
            for (int i = 0; i < FAKE_ENEMIES.size(); i++) {
                FakeEnemy f = FAKE_ENEMIES.get(i);
                FAKE_ENEMIES.set(i, new FakeEnemy(f.x(), f.z(), f.yaw(), f.age() + 1));
            }
            FAKE_ENEMIES.removeIf(f -> f.age() > 60);
        } else {
            FAKE_ENEMIES.clear();
            paranoiaHitCd = 0;
        }

        // 溜冰鞋：滑行邏輯（碰到方塊停下來）
        if (p.hasEffect(SpecialRegistry.ICE_SKATES.get())) {
            if (p.horizontalCollision) {
                slideX = 0;
                slideZ = 0;
                sliding = false;
            } else {
                boolean hasInput = Math.abs(p.input.forwardImpulse) > 0.01f || Math.abs(p.input.leftImpulse) > 0.01f;
                Vec3 dm = p.getDeltaMovement();
                double spd = Math.hypot(dm.x, dm.z);
                if (hasInput && spd > 0.04) {
                    double cap = Math.min(0.32, Math.max(0.22, spd));
                    slideX = dm.x / spd * cap;
                    slideZ = dm.z / spd * cap;
                    sliding = true;
                } else if (!hasInput && sliding) {
                    p.setDeltaMovement(slideX, dm.y, slideZ);
                }
            }
        } else {
            slideX = slideZ = 0;
            sliding = false;
        }
    }

    /** 顯示干擾器：隱藏準心、血條、彈藥數、快捷欄、小地圖等所有抬頭顯示器。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasEffect(SpecialRegistry.HUD_JAM.get())) {
            event.setCanceled(true);
        }
    }

    /** 敵我不分與擬態：隱藏頭頂 ID 名條。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onNameTag(RenderNameTagEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (SpecialHooks.showAsSteve(mc.player, event.getEntity())) {
            event.setResult(Event.Result.DENY);
            return;
        }
        if (event.getEntity() instanceof Player p && SpecialHooks.CLIENT_MIMIC.containsKey(p.getUUID())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** 擬態：取消繪製玩家模型，改在腳下繪製物資箱方塊。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player p)) return;
        Integer kind = SpecialHooks.CLIENT_MIMIC.get(p.getUUID());
        if (kind == null) return;
        event.setCanceled(true);
        BlockState state = switch (kind) {
            case 1 -> PGBlocks.CRATE_MID.get().defaultBlockState();
            case 2 -> PGBlocks.CRATE_HIGH.get().defaultBlockState();
            default -> PGBlocks.CRATE_LOW.get().defaultBlockState();
        };
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-0.5, 0.0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, event.getMultiBufferSource(),
            event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** 領域展開：在世界裡繪製半徑 20 格半透明紫色封閉球形結界。 */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || domains.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Plan9SyncPacket.DomainInfo d : domains) {
            drawDome(buf, m, d.x(), d.y(), d.z(), d.radius());
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void drawDome(BufferBuilder buf, Matrix4f m, double cx, double cy, double cz, double r) {
        int latSteps = 14, lonSteps = 28;
        for (int i = 0; i < latSteps; i++) {
            double phi0 = -Math.PI / 2 + Math.PI * i / latSteps;
            double phi1 = -Math.PI / 2 + Math.PI * (i + 1) / latSteps;
            double y0 = cy + Math.sin(phi0) * r, y1 = cy + Math.sin(phi1) * r;
            double r0 = Math.cos(phi0) * r, r1 = Math.cos(phi1) * r;
            int alpha = (i % 2 == 0) ? 55 : 35;
            for (int j = 0; j < lonSteps; j++) {
                double th0 = Math.PI * 2 * j / lonSteps, th1 = Math.PI * 2 * (j + 1) / lonSteps;
                float x00 = (float) (cx + Math.cos(th0) * r0), z00 = (float) (cz + Math.sin(th0) * r0);
                float x10 = (float) (cx + Math.cos(th1) * r0), z10 = (float) (cz + Math.sin(th1) * r0);
                float x11 = (float) (cx + Math.cos(th1) * r1), z11 = (float) (cz + Math.sin(th1) * r1);
                float x01 = (float) (cx + Math.cos(th0) * r1), z01 = (float) (cz + Math.sin(th0) * r1);
                buf.vertex(m, x00, (float) y0, z00).color(120, 40, 200, alpha).endVertex();
                buf.vertex(m, x10, (float) y0, z10).color(120, 40, 200, alpha).endVertex();
                buf.vertex(m, x11, (float) y1, z11).color(90, 25, 160, alpha).endVertex();
                buf.vertex(m, x01, (float) y1, z01).color(90, 25, 160, alpha).endVertex();
            }
        }
    }
}
