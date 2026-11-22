package com.provinggrounds.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.provinggrounds.block.StatsBoardBlockEntity;
import com.provinggrounds.network.StatsLeaderboardPacket;
import com.provinggrounds.network.StatsPersonalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 檔案: StatsBoardRenderer.java
 * 目的: 畫數據看板上方的浮空文字（計畫書 B5，只在客戶端）。文字永遠面向看的人，只在 32 格內繪製。
 *   個人數據用伺服器送給本人的那一份；全服排行用附近同步來的排行，依本機選的欄位排序，顯示前 10 名，
 *   自己不在前 10 名時最下面多一行自己的名次。每個人看到的都是自己的畫面。
 */
public class StatsBoardRenderer implements BlockEntityRenderer<StatsBoardBlockEntity> {

    private static final double MAX_DIST = 32;
    private static final float SCALE = 0.025f;
    private static final int LINE_H = 10;
    private static final int GOLD = 0xFFE8D060, WHITE = 0xFFFFFFFF, DIM = 0xFFA0A8B8, BLUE = 0xFF80D0FF;
    /** 排行表各欄的 x 位移：名次、玩家、勝場、場數、擊殺、K/D、等級。 */
    private static final int[] COLS = { 0, 26, 120, 150, 180, 210, 245 };

    /** 一行文字：cells 依 xs 的位移排開，整行同色。 */
    private record Line(String[] cells, int[] xs, int color) {
        static Line of(String text, int color) { return new Line(new String[] { text }, new int[] { 0 }, color); }
        static Line row(String[] cells, int color) { return new Line(cells, COLS, color); }
    }

    private final Font font;

    public StatsBoardRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public boolean shouldRenderOffScreen(StatsBoardBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return (int) MAX_DIST; }

    @Override
    public void render(StatsBoardBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > MAX_DIST * MAX_DIST) return;

        List<Line> lines = ClientStatsBoard.showRanking() ? ranking(mc) : personal();
        int width = 0;
        for (Line l : lines) width = Math.max(width, lineWidth(l));

        pose.pushPose();
        pose.translate(0.5, 1.4 + lines.size() * LINE_H * SCALE, 0.5);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        pose.scale(-SCALE, -SCALE, SCALE);
        Matrix4f m = pose.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.35f) * 255) << 24;
        int x0 = -width / 2;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            for (int c = 0; c < l.cells().length; c++) {
                font.drawInBatch(l.cells()[c], x0 + l.xs()[c], i * LINE_H, l.color(), false, m, buffers,
                    Font.DisplayMode.NORMAL, c == 0 ? bg : 0, LightTexture.FULL_BRIGHT);
            }
        }
        pose.popPose();
    }

    private int lineWidth(Line l) {
        int last = l.cells().length - 1;
        return l.xs()[last] + font.width(l.cells()[last]);
    }

    private static List<Line> personal() {
        List<Line> out = new ArrayList<>();
        StatsPersonalPacket p = ClientStatsBoard.personal();
        out.add(Line.of("大逃殺數據｜個人", GOLD));
        if (p == null) {
            out.add(Line.of("資料讀取中…", DIM));
        } else {
            double rate = p.games() == 0 ? 0 : 100.0 * p.wins() / p.games();
            out.add(Line.of(p.name(), WHITE));
            out.add(Line.of(String.format("等級 %d（距離下一級還差 %d XP）", p.level(), p.xpToNext()), WHITE));
            out.add(Line.of(String.format("場數 %d｜勝場 %d｜勝率 %.1f%%", p.games(), p.wins(), rate), WHITE));
            out.add(Line.of(String.format("擊殺 %d｜死亡 %d｜助攻 %d｜K/D %.2f", p.kills(), p.deaths(), p.assists(), p.kd()), WHITE));
            out.add(Line.of("單場最高擊殺 " + p.bestKills(), WHITE));
            out.add(Line.of(String.format("全服名次：勝場 第 %s 名｜擊殺 第 %s 名", rankText(p.rankWins()), rankText(p.rankKills())), BLUE));
        }
        out.add(Line.of("右鍵：切換到全服排行", DIM));
        return out;
    }

    private static String rankText(int r) { return r <= 0 ? "-" : String.valueOf(r); }

    private static List<Line> ranking(Minecraft mc) {
        List<Line> out = new ArrayList<>();
        out.add(Line.of("大逃殺數據｜全服排行（依" + ClientStatsBoard.sort().label + "）", GOLD));
        out.add(Line.row(new String[] { "名次", "玩家", "勝場", "場數", "擊殺", "K/D", "等級" }, DIM));
        List<StatsLeaderboardPacket.Entry> list = ClientStatsBoard.sorted();
        UUID self = mc.player != null ? mc.player.getUUID() : null;
        boolean selfShown = false;
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            StatsLeaderboardPacket.Entry e = list.get(i);
            boolean me = e.id().equals(self);
            selfShown |= me;
            out.add(Line.row(cells(i + 1, e), me ? GOLD : WHITE));
        }
        if (list.isEmpty()) out.add(Line.of("還沒有任何數據（站在看板附近幾秒後會更新）", DIM));
        if (!selfShown && self != null) {
            for (int i = 10; i < list.size(); i++) {
                if (list.get(i).id().equals(self)) {
                    out.add(Line.of("⋯", DIM));
                    out.add(Line.row(cells(i + 1, list.get(i)), GOLD));
                    break;
                }
            }
        }
        out.add(Line.of("右鍵：個人數據｜蹲下＋右鍵：換排序", DIM));
        return out;
    }

    private static String[] cells(int rank, StatsLeaderboardPacket.Entry e) {
        String name = e.name().length() > 16 ? e.name().substring(0, 16) : e.name();
        return new String[] { rank + ".", name, String.valueOf(e.wins()), String.valueOf(e.games()), String.valueOf(e.kills()),
            String.format("%.2f", e.kd()), String.valueOf(e.level()) };
    }
}
