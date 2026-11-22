package com.provinggrounds.ui;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.config.PGClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 檔案: DamageIndicator.java
 * 目的: 受擊方向指示器（只在客戶端）。受傷時在螢幕邊緣、對應攻擊方向的位置畫一塊紅色方塊，往畫面中央漸淡，
 *   約 damage_indicator_seconds 秒內淡出。整個螢幕代表方位：上 = 前方、下 = 後方、左 = 左方、右 = 右方。
 *   記下的是攻擊者（或爆炸點）的世界座標，每一幀依目前視角重新計算方向，所以轉頭時方塊會跟著移動。
 *   來源：原版收到傷害時記錄的 getLastDamageSource()（含攻擊者或來源位置）；
 *   沒有位置的傷害（摔落、圈外、流血等）不顯示。同時最多顯示 6 個。
 *   客戶端設定：damage_indicator_enabled、damage_indicator_seconds。
 */
@Mod.EventBusSubscriber(modid = ProvingGroundsMod.MOD_ID, value = Dist.CLIENT)
public final class DamageIndicator {

    private record Hit(Vec3 from, long startMs) {}

    private static final List<Hit> HITS = new ArrayList<>();
    private static DamageSource lastSeen;
    private static final int MAX_HITS = 6;

    private DamageIndicator() {}

    /** 每個客戶端 tick 檢查有沒有新的受傷（原版每次受傷都會換一個新的 DamageSource 物件）。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) { HITS.clear(); lastSeen = null; return; }
        DamageSource src = p.getLastDamageSource();
        if (src == null || src == lastSeen) return;
        lastSeen = src;
        Vec3 from = sourcePos(src, p);
        if (from == null) return;
        synchronized (HITS) {
            HITS.add(new Hit(from, System.currentTimeMillis()));
            while (HITS.size() > MAX_HITS) HITS.remove(0);
        }
    }

    /** 攻擊來源的位置：開槍／攻擊的人 → 直接打到的實體（子彈）→ 傷害來源位置（爆炸）。 */
    private static Vec3 sourcePos(DamageSource src, Entity self) {
        Entity e = src.getEntity();
        if (e == null || e == self) e = src.getDirectEntity();
        if (e != null && e != self) return e.position();
        return src.getSourcePosition();
    }

    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g, partialTick, width, height);

    private static void render(GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        double seconds = 1.5;
        try {
            if (!PGClientConfig.CLIENT.damageIndicatorEnabled.get()) return;
            seconds = PGClientConfig.CLIENT.damageIndicatorSeconds.get();
        } catch (IllegalStateException ignored) {
            // 用預設值
        }
        long now = System.currentTimeMillis();
        double life = seconds * 1000;
        float yaw = Mth.lerp(partial, p.yRotO, p.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 me = p.getPosition(partial);
        synchronized (HITS) {
            for (Iterator<Hit> it = HITS.iterator(); it.hasNext(); ) {
                Hit h = it.next();
                double age = now - h.startMs();
                if (age > life) { it.remove(); continue; }
                double dx = h.from().x - me.x, dz = h.from().z - me.z;
                if (dx * dx + dz * dz < 1e-6) continue;
                // 換成「相對於面向」的方向：forward = 面向的方向（−sin yaw, cos yaw），right = (−cos yaw, −sin yaw)
                double fwd = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
                double right = dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw);
                // 螢幕方向：上 = 前方（螢幕 y 往下為正，所以前方是 −y）
                double sx = right, sy = -fwd;
                float alpha = (float) (1.0 - age / life);
                drawEdge(g, width, height, sx, sy, alpha);
            }
        }
    }

    /**
     * 從畫面中央往 (sx, sy) 方向延伸到螢幕邊緣，在那裡畫一塊紅色方塊，往畫面中央漸淡。
     * 碰到上下邊就畫橫向的方塊、碰到左右邊就畫直向的方塊。
     */
    private static void drawEdge(GuiGraphics g, int w, int h, double sx, double sy, float alpha) {
        double len = Math.sqrt(sx * sx + sy * sy);
        sx /= len;
        sy /= len;
        double cx = w / 2.0, cy = h / 2.0;
        double tx = sx == 0 ? Double.MAX_VALUE : (cx - 2) / Math.abs(sx);
        double ty = sy == 0 ? Double.MAX_VALUE : (cy - 2) / Math.abs(sy);
        double t = Math.min(tx, ty);
        int px = (int) Math.round(cx + sx * t), py = (int) Math.round(cy + sy * t);
        int a = Math.max(0, Math.min(255, Math.round(alpha * 200)));
        final int size = 40, depth = 24, steps = 8; // 方塊長 40、從邊緣往內 24 像素漸淡
        boolean horizontalEdge = ty <= tx; // 碰到上下邊
        for (int i = 0; i < steps; i++) {
            int stripA = a * (steps - i) / steps;
            int color = (stripA << 24) | 0xE02020;
            int d0 = depth * i / steps, d1 = depth * (i + 1) / steps;
            if (horizontalEdge) {
                int x0 = Mth.clamp(px - size / 2, 0, w - size), x1 = x0 + size;
                if (sy < 0) g.fill(x0, d0, x1, d1, color);           // 上邊（前方）
                else g.fill(x0, h - d1, x1, h - d0, color);          // 下邊（後方）
            } else {
                int y0 = Mth.clamp(py - size / 2, 0, h - size), y1 = y0 + size;
                if (sx < 0) g.fill(d0, y0, d1, y1, color);           // 左邊
                else g.fill(w - d1, y0, w - d0, y1, color);          // 右邊
            }
        }
    }
}
