package com.provinggrounds.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.item.ParachuteItem;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 檔案: ParachuteRenderer.java
 * 目的: 畫出打開的降落傘（只在客戶端）。誰手上拿著打開的降落傘（ParachuteItem 的 Open），
 *   就在他頭上畫傘衣模型（models/item/parachute_canopy.json，放大 3 倍，跟著身體方向轉、輕微擺動）。
 *   別人：在畫人物之後畫（RenderPlayerEvent）。
 *   自己第一人稱：原版不畫自己的身體，所以另外在世界裡畫，抬頭就看得到傘衣。
 *     第一人稱用沒有傘繩的模型（parachute_canopy_fp.json）：傘繩從肩膀往上收攏，會剛好穿過鏡頭擋住視野。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class ParachuteRenderer {

    private static final float SCALE = 3.0f;
    /** 第一人稱用的傘衣模型（沒有傘繩），在 ClientModEvents 註冊成額外模型。 */
    public static final net.minecraft.resources.ResourceLocation FP_MODEL = new net.minecraft.resources.ResourceLocation(ProvingGroundsMod.MOD_ID, "item/parachute_canopy_fp");
    private static ItemStack canopy = ItemStack.EMPTY;

    private ParachuteRenderer() {}

    private static ItemStack canopy() {
        if (canopy.isEmpty()) canopy = new ItemStack(PGBlocks.PARACHUTE_CANOPY.get());
        return canopy;
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        if (!ParachuteItem.isOpen(p.getMainHandItem()) || p.isInvisible()) return;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        float bodyYaw = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot);
        draw(ps, event.getMultiBufferSource(), event.getPackedLight(), p, bodyYaw, p.tickCount + event.getPartialTick(), false);
        ps.popPose();
    }

    /** 自己第一人稱時也畫（抬頭可以看到）。 */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson() || !ParachuteItem.isOpen(p.getMainHandItem())) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(x - cam.x, y - cam.y, z - cam.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        draw(ps, buf, LightTexture.FULL_BRIGHT, p, Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot), p.tickCount + pt, true);
        buf.endBatch();
        ps.popPose();
    }

    /** 從玩家腳底的座標系開始：移到肩膀上方、轉成身體方向、擺動、放大，畫傘衣。 */
    private static void draw(PoseStack ps, MultiBufferSource buf, int light, Player p, float bodyYaw, float age, boolean firstPerson) {
        ps.translate(0, 1.45, 0); // 肩膀
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.08f) * 3f)); // 左右輕微擺動
        ps.mulPose(Axis.XP.rotationDegrees(6f)); // 稍微往前傾（往前飄）
        ps.scale(SCALE, SCALE, SCALE);
        ps.translate(0, 0.5, 0); // 模型的底部（傘繩收攏處）對齊肩膀
        Minecraft mc = Minecraft.getInstance();
        if (firstPerson) {
            var model = mc.getModelManager().getModel(FP_MODEL);
            if (model != mc.getModelManager().getMissingModel()) {
                mc.getItemRenderer().render(canopy(), ItemDisplayContext.FIXED, false, ps, buf, light, OverlayTexture.NO_OVERLAY, model);
                return;
            }
        }
        mc.getItemRenderer().renderStatic(canopy(), ItemDisplayContext.FIXED, light,
            OverlayTexture.NO_OVERLAY, ps, buf, p.level(), p.getId());
    }
}
