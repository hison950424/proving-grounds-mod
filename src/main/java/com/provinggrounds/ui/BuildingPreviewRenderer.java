package com.provinggrounds.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.admin.AdminTools;
import com.provinggrounds.admin.build.BuildingBlueprint;
import com.provinggrounds.admin.build.BuildingCanvas;
import com.provinggrounds.admin.build.BuildingCatalog;
import com.provinggrounds.item.AdminToolItem;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 檔案: BuildingPreviewRenderer.java
 * 目的: 管理員手持「建築生成工具」且已選定款式時，在世界中繪製 3D 佔地預覽框（只在客戶端）：
 *   - 青色線框顯示建築完整的寬 × 深 × 高範圍。
 *   - 金色前端亮線標示正門朝向位置，放置前即可精準對齊道路與空地。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class BuildingPreviewRenderer {

    private static final float[] CYAN = { 0.30f, 0.85f, 1.00f, 0.85f };
    private static final float[] GOLD = { 0.95f, 0.82f, 0.25f, 1.00f };

    private BuildingPreviewRenderer() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null || !AdminTools.allowed(p)) return;
        BuildingBlueprint bp = heldBlueprint(p);
        if (bp == null) return;

        BlockPos origin = BuildingCanvas.anchorFor(p);
        Direction facing = p.getDirection();
        Direction right = facing.getClockWise();

        int hw = bp.halfW();
        int leftExtent = hw;
        int rightExtent = bp.width() - 1 - hw;

        // 算出建築包圍盒在世界空間的四個角（含方塊邊緣 0..+1）
        BlockPos p00 = offset(origin, right, facing, -leftExtent, 0);
        BlockPos p10 = offset(origin, right, facing, rightExtent, 0);
        BlockPos p01 = offset(origin, right, facing, -leftExtent, bp.depth() - 1);
        BlockPos p11 = offset(origin, right, facing, rightExtent, bp.depth() - 1);

        float minX = Math.min(Math.min(p00.getX(), p10.getX()), Math.min(p01.getX(), p11.getX()));
        float maxX = Math.max(Math.max(p00.getX(), p10.getX()), Math.max(p01.getX(), p11.getX())) + 1.0f;
        float minZ = Math.min(Math.min(p00.getZ(), p10.getZ()), Math.min(p01.getZ(), p11.getZ()));
        float maxZ = Math.max(Math.max(p00.getZ(), p10.getZ()), Math.max(p01.getZ(), p11.getZ())) + 1.0f;
        float minY = origin.getY();
        float maxY = origin.getY() + bp.height() + 1.0f;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        box(b, m, minX, minY, minZ, maxX, maxY, maxZ, CYAN);

        // 金色框標出正門側（lz = 0 這一排底座，寬 2 格指示門洞）
        BlockPos doorL = offset(origin, right, facing, -1, 0);
        BlockPos doorR = offset(origin, right, facing, 1, 0);
        float dMinX = Math.min(doorL.getX(), doorR.getX());
        float dMaxX = Math.max(doorL.getX(), doorR.getX()) + 1.0f;
        float dMinZ = Math.min(doorL.getZ(), doorR.getZ());
        float dMaxZ = Math.max(doorL.getZ(), doorR.getZ()) + 1.0f;
        box(b, m, dMinX, minY + 0.02f, dMinZ, dMaxX, minY + 3.0f, dMaxZ, GOLD);

        Tesselator.getInstance().end();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static BlockPos offset(BlockPos origin, Direction right, Direction facing, int lx, int lz) {
        return origin.offset(
            right.getStepX() * lx + facing.getStepX() * lz,
            0,
            right.getStepZ() * lx + facing.getStepZ() * lz
        );
    }

    private static BuildingBlueprint heldBlueprint(Player p) {
        Item tool = PGBlocks.ADMIN_TOOLS.get(AdminToolItem.Tool.BUILDING_PLACER).get();
        ItemStack stack = p.getMainHandItem().is(tool) ? p.getMainHandItem()
            : (p.getOffhandItem().is(tool) ? p.getOffhandItem() : ItemStack.EMPTY);
        if (stack.isEmpty() || stack.getTag() == null) return null;
        return BuildingCatalog.byId(stack.getTag().getString(BuildingCatalog.TOOL_NBT));
    }

    private static void box(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float[] col) {
        float r = col[0], g = col[1], bl = col[2], a = col[3];
        float[][] c = { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 } };
        int[][] edges = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
        for (int[] e : edges) {
            b.vertex(m, c[e[0]][0], c[e[0]][1], c[e[0]][2]).color(r, g, bl, a).endVertex();
            b.vertex(m, c[e[1]][0], c[e[1]][1], c[e[1]][2]).color(r, g, bl, a).endVertex();
        }
    }
}
