package com.provinggrounds.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.client.ClientMapData;
import com.provinggrounds.network.MapMarkersPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * 檔案: MapRender.java
 * 目的: 大地圖與小地圖共用的繪圖（只在客戶端使用）。
 *   視窗 = 畫面上一個矩形（x, y, w, h），對應世界上以 (viewX, viewZ) 為左上角、寬 span 格的範圍，北方朝上。
 *   地形：伺服器產生的俯視圖（原版地圖顏色），收到後轉成材質。
 *   疊加：安全區（白）、下一圈（淺藍）、空投（黃）、隊友（隊伍色箭頭＋名字）、倒地隊友（紅白閃）、
 *         隊友看到的敵人（紅，漸淡）、自己（白箭頭）。
 */
public final class MapRender {

    private static final ResourceLocation TEXTURE = new ResourceLocation(ProvingGroundsMod.MOD_ID, "dynamic/br_map");
    @Nullable private static DynamicTexture texture;
    private static long builtVersion = Long.MIN_VALUE;
    private static int builtMap = Integer.MIN_VALUE;

    public static final int ZONE = 0xFFFFFFFF;
    public static final int NEXT_ZONE = 0xFF60C0FF;
    public static final int AIRDROP = 0xFFFFD040;
    public static final int ENEMY = 0xFFE04848;

    private MapRender() {}

    /** 世界 → 畫面的換算。 */
    public record View(int x, int y, int w, int h, double viewX, double viewZ, double span) {
        double px(double wx) { return x + (wx - viewX) / span * w; }
        double pz(double wz) { return y + (wz - viewZ) / span * h; }
        double blocksPerPixel() { return span / w; }
    }

    // ── 地形 ────────────────────────────────────────────────────────────────

    /** 目前的俯視圖（必要時重建材質）；沒有圖回傳 null。 */
    @Nullable
    public static ClientMapData.Image image() {
        ClientMapData.Image img = ClientMapData.image();
        if (img == null || img.size() <= 0) return null;
        if (img.version() != builtVersion || img.mapId() != builtMap) {
            NativeImage ni = new NativeImage(img.size(), img.size(), true);
            byte[] px = img.pixels();
            for (int z = 0; z < img.size(); z++) {
                for (int x = 0; x < img.size(); x++) {
                    int b = px[z * img.size() + x] & 0xFF;
                    // 沒有資料的格子透明；有資料的用原版地圖顏色（NativeImage 用 ABGR）
                    ni.setPixelRGBA(x, z, b < 4 ? 0 : MapColor.getColorFromPackedId(b));
                }
            }
            if (texture != null && texture.getPixels() != null
                    && texture.getPixels().getWidth() == img.size() && texture.getPixels().getHeight() == img.size()) {
                // 同尺寸：直接換像素重新上傳，不用建新材質
                texture.setPixels(ni);
                texture.upload();
            } else {
                // 尺寸不同（換地圖）：註冊新材質，TextureManager 會自動關閉舊的，這裡不要再 close 一次
                texture = new DynamicTexture(ni);
                Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
            }
            builtVersion = img.version();
            builtMap = img.mapId();
        }
        return img;
    }

    /** 畫地形（只畫視窗與圖片重疊的部分，避免材質重複）。 */
    public static void drawTerrain(GuiGraphics g, View v) {
        g.fill(v.x(), v.y(), v.x() + v.w(), v.y() + v.h(), 0xFF1A1F28);
        ClientMapData.Image img = image();
        if (img == null) {
            drawGrid(g, v);
            return;
        }
        double imgX0 = img.minX(), imgZ0 = img.minZ(), imgSpan = (double) img.size() * img.scale();
        double wx0 = Math.max(v.viewX(), imgX0), wz0 = Math.max(v.viewZ(), imgZ0);
        double wx1 = Math.min(v.viewX() + v.span(), imgX0 + imgSpan), wz1 = Math.min(v.viewZ() + v.span(), imgZ0 + imgSpan);
        if (wx1 <= wx0 || wz1 <= wz0) return;
        int sx0 = (int) Math.floor(v.px(wx0)), sz0 = (int) Math.floor(v.pz(wz0));
        int sx1 = (int) Math.ceil(v.px(wx1)), sz1 = (int) Math.ceil(v.pz(wz1));
        float u = (float) ((wx0 - imgX0) / img.scale()), t = (float) ((wz0 - imgZ0) / img.scale());
        int uw = Math.max(1, (int) Math.round((wx1 - wx0) / img.scale()));
        int vh = Math.max(1, (int) Math.round((wz1 - wz0) / img.scale()));
        g.blit(TEXTURE, sx0, sz0, sx1 - sx0, sz1 - sz0, u, t, uw, vh, img.size(), img.size());
    }

    /** 沒有俯視圖時畫 100 格一條的格線。 */
    private static void drawGrid(GuiGraphics g, View v) {
        int step = 100;
        for (double wx = Math.ceil(v.viewX() / step) * step; wx < v.viewX() + v.span(); wx += step) {
            int sx = (int) v.px(wx);
            g.fill(sx, v.y(), sx + 1, v.y() + v.h(), 0x40FFFFFF);
        }
        for (double wz = Math.ceil(v.viewZ() / step) * step; wz < v.viewZ() + v.span(); wz += step) {
            int sz = (int) v.pz(wz);
            g.fill(v.x(), sz, v.x() + v.w(), sz + 1, 0x40FFFFFF);
        }
    }

    // ── 圈 ──────────────────────────────────────────────────────────────────

    public static void drawCircle(GuiGraphics g, View v, double cx, double cz, double r, int color, int thickness, boolean dashed) {
        if (r <= 0) return;
        double pr = r / v.blocksPerPixel();
        int n = (int) Math.max(48, Math.min(1440, pr * 2 * Math.PI / 1.5));
        double scx = v.px(cx), scz = v.pz(cz);
        // 整個圓都在視窗外（例如小地圖只看得到圈內一小塊）就不用畫
        if (scx + pr < v.x() || scx - pr > v.x() + v.w() || scz + pr < v.y() || scz - pr > v.y() + v.h()) return;
        if (pr > Math.hypot(v.w(), v.h()) + Math.hypot(scx - (v.x() + v.w() / 2.0), scz - (v.y() + v.h() / 2.0))) return; // 視窗整個在圓內
        for (int i = 0; i < n; i++) {
            if (dashed && (i / 6) % 2 == 1) continue;
            double a = i * Math.PI * 2 / n;
            int sx = (int) (scx + Math.cos(a) * pr), sz = (int) (scz + Math.sin(a) * pr);
            // 只畫落在視窗裡的點（小地圖上大圈的大部分點都在外面，省下大量繪圖）
            if (sx < v.x() - thickness || sx > v.x() + v.w() || sz < v.y() - thickness || sz > v.y() + v.h()) continue;
            g.fill(sx, sz, sx + thickness, sz + thickness, color);
        }
    }

    // ── 標記 ────────────────────────────────────────────────────────────────

    public static void drawMarkers(GuiGraphics g, Font font, View v, boolean names) {
        long now = System.currentTimeMillis();
        for (MapMarkersPacket.Marker m : ClientMapData.markers()) {
            int sx = (int) v.px(m.x()), sz = (int) v.pz(m.z());
            switch (m.kind()) {
                case MapMarkersPacket.AIRDROP -> airdropBox(g, sx, sz);
                case MapMarkersPacket.AIRDROP_FALLING -> { if ((now / 300) % 2 == 0) airdropBox(g, sx, sz); }
                case MapMarkersPacket.ENEMY -> {
                    float alpha = 1f - Math.min(1f, m.age() / 60f) * 0.7f;
                    arrow(g, font, sx, sz, m.yaw(), withAlpha(ENEMY, alpha));
                }
                case MapMarkersPacket.TEAMMATE_DOWNED -> {
                    int c = (now / 300) % 2 == 0 ? ENEMY : 0xFFFFFFFF;
                    box(g, sx, sz, 2, c);
                    if (names) label(g, font, sx, sz, Tr.t("map.downed_name", m.name()), c);
                }
                default -> { // 隊友、觀戰時的所有玩家
                    int c = 0xFF000000 | m.color();
                    arrow(g, font, sx, sz, m.yaw(), c);
                    if (names) label(g, font, sx, sz, m.name(), c);
                }
            }
        }
    }

    /** 自己：白色箭頭。 */
    public static void drawSelf(GuiGraphics g, Font font, View v, double x, double z, float yaw) {
        arrow(g, font, (int) v.px(x), (int) v.pz(z), yaw, 0xFFFFFFFF);
    }

    /** 箭頭（▲ 字元旋轉）：yaw 0 = 朝南（畫面下方）。 */
    private static void arrow(GuiGraphics g, Font font, int sx, int sz, float yaw, int color) {
        g.pose().pushPose();
        g.pose().translate(sx, sz, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yaw + 180));
        g.drawString(font, "▲", -font.width("▲") / 2 + 1, -4, 0xFF000000, false); // 黑邊
        g.drawString(font, "▲", -font.width("▲") / 2, -4, color, false);
        g.pose().popPose();
    }

    /** 空投標記：原本的方塊（半徑 3）縮成 35%（AIRDROP_SCALE）。像素不能切一半，所以用縮放畫。 */
    private static final float AIRDROP_SCALE = 0.35f;

    private static void airdropBox(GuiGraphics g, int sx, int sz) {
        g.pose().pushPose();
        g.pose().translate(sx, sz, 0);
        g.pose().scale(AIRDROP_SCALE, AIRDROP_SCALE, 1);
        box(g, 0, 0, 3, AIRDROP);
        g.pose().popPose();
    }

    private static void box(GuiGraphics g, int sx, int sz, int r, int color) {
        g.fill(sx - r - 1, sz - r - 1, sx + r + 1, sz + r + 1, 0xFF000000);
        g.fill(sx - r, sz - r, sx + r, sz + r, color);
    }

    private static void label(GuiGraphics g, Font font, int sx, int sz, String text, int color) {
        g.drawString(font, text, sx - font.width(text) / 2, sz - 14, color, true);
    }

    private static int withAlpha(int argb, float a) {
        int alpha = Math.max(40, (int) (((argb >>> 24) & 0xFF) * a));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
