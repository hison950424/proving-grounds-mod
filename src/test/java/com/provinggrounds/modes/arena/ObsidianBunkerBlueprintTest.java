package com.provinggrounds.modes.arena;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 檔案: ObsidianBunkerBlueprintTest.java
 * 目的: 驗證室內近距團隊地圖「黑曜地堡」藍圖的封閉性、零木箱、走道寬度、掩體密度、
 *       全部重生點／佔點據點安全空間，以及 3D BFS（屏障關閉隔離、開啟全圖連通）。
 */
class ObsidianBunkerBlueprintTest {

    @Test
    void outerShellIsSealedAndZeroWoodenChestsOrBarrels() throws IOException {
        ObsidianBunkerBlueprint.Built b = ObsidianBunkerBlueprint.build();
        var g = b.grid;
        for (int x = 0; x < ObsidianBunkerBlueprint.WIDTH; x++) {
            for (int y = 0; y < ObsidianBunkerBlueprint.HEIGHT; y++) {
                assertEquals(ObsidianBunkerBlueprint.Cell.WALL_OUTER, g[x][y][0], "北外牆應完全密封");
                assertEquals(ObsidianBunkerBlueprint.Cell.WALL_OUTER, g[x][y][ObsidianBunkerBlueprint.DEPTH - 1], "南外牆應完全密封");
            }
        }
        for (int z = 0; z < ObsidianBunkerBlueprint.DEPTH; z++) {
            for (int y = 0; y < ObsidianBunkerBlueprint.HEIGHT; y++) {
                assertEquals(ObsidianBunkerBlueprint.Cell.WALL_OUTER, g[0][y][z], "西外牆應完全密封");
                assertEquals(ObsidianBunkerBlueprint.Cell.WALL_OUTER, g[ObsidianBunkerBlueprint.WIDTH - 1][y][z], "東外牆應完全密封");
            }
        }
        for (int x = 0; x < ObsidianBunkerBlueprint.WIDTH; x++) {
            for (int z = 0; z < ObsidianBunkerBlueprint.DEPTH; z++) {
                assertEquals(ObsidianBunkerBlueprint.Cell.WALL_OUTER, g[x][ObsidianBunkerBlueprint.HEIGHT - 1][z], "屋頂應完全密封");
            }
        }

        for (String name : List.of("ArenaMapGenerator.java", "ObsidianBunkerBlueprint.java")) {
            Path p = Paths.get("src", "main", "java", "com", "provinggrounds", "modes", "arena", name);
            assertTrue(Files.exists(p), "找不到原始碼: " + p);
            String src = Files.readString(p);
            assertFalse(src.contains("Blocks.CHEST"), name + " 不得使用 Blocks.CHEST");
            assertFalse(src.contains("Blocks.TRAPPED_CHEST"), name + " 不得使用 Blocks.TRAPPED_CHEST");
            assertFalse(src.contains("Blocks.BARREL"), name + " 不得使用 Blocks.BARREL");
        }
    }

    @Test
    void allSpawnsStandOnWalkableFloorWithHeadroom() {
        ObsidianBunkerBlueprint.Built b = ObsidianBunkerBlueprint.build();
        assertEquals(3, b.redSpawns.size(), "紅隊應有 3 個重生點");
        assertEquals(3, b.blueSpawns.size(), "藍隊應有 3 個重生點");
        assertEquals(6, b.barriers.size(), "紅藍基地應合計 6 道出口屏障");
        assertTrue(b.gkSpawns.size() >= 40, "槍王重生點數量不足: " + b.gkSpawns.size());

        verifySpawns("紅隊", b.grid, b.redSpawns);
        verifySpawns("藍隊", b.grid, b.blueSpawns);
        verifySpawns("槍王", b.grid, b.gkSpawns);
    }

    private static void verifySpawns(String label, ObsidianBunkerBlueprint.Cell[][][] g,
                                     List<ObsidianBunkerBlueprint.LocalSpawn> spawns) {
        for (var s : spawns) {
            int x = (int) Math.floor(s.x());
            int y = (int) Math.floor(s.y());
            int z = (int) Math.floor(s.z());
            assertTrue(x >= 1 && x < ObsidianBunkerBlueprint.WIDTH - 1
                    && y >= 1 && y < ObsidianBunkerBlueprint.HEIGHT - 2
                    && z >= 1 && z < ObsidianBunkerBlueprint.DEPTH - 1,
                label + "重生點超出界線: " + s);
            assertTrue(g[x][y - 1][z].isWalkableFloor(),
                label + "重生點腳下不是可站立地板: " + s + " -> " + g[x][y - 1][z]);
            assertEquals(ObsidianBunkerBlueprint.Cell.AIR, g[x][y][z],
                label + "重生點腳部不是空氣: " + s);
            assertEquals(ObsidianBunkerBlueprint.Cell.AIR, g[x][y + 1][z],
                label + "重生點頭部不是空氣: " + s);
        }
    }

    @Test
    void domPointsValidAndSafeFromAntiCampZones() {
        ObsidianBunkerBlueprint.Built b = ObsidianBunkerBlueprint.build();
        assertEquals(3, b.domPoints.size(), "應有 A/B/C 三個佔點據點");

        for (var pt : b.domPoints) {
            int walkableInZone = 0;
            for (int x = pt.fx0(); x <= pt.fx1(); x++) {
                for (int z = pt.fz0(); z <= pt.fz1(); z++) {
                    if (b.grid[x][pt.cy()][z].isWalkableFloor()
                            && b.grid[x][pt.cy() + 1][z] == ObsidianBunkerBlueprint.Cell.AIR
                            && b.grid[x][pt.cy() + 2][z] == ObsidianBunkerBlueprint.Cell.AIR) {
                        walkableInZone++;
                    }
                }
            }
            assertTrue(walkableInZone >= 16, "據點 " + pt.name() + " 可站立空間過小: " + walkableInZone);

            for (var rs : b.redSpawns) {
                double d = Math.hypot(rs.x() - pt.cx(), rs.z() - pt.cz());
                assertTrue(d > ObsidianBunkerBlueprint.ANTI_CAMP_RADIUS + pt.radius(),
                    "據點 " + pt.name() + " 離紅隊基地過近: " + d);
            }
            for (var bs : b.blueSpawns) {
                double d = Math.hypot(bs.x() - pt.cx(), bs.z() - pt.cz());
                assertTrue(d > ObsidianBunkerBlueprint.ANTI_CAMP_RADIUS + pt.radius(),
                    "據點 " + pt.name() + " 離藍隊基地過近: " + d);
            }
        }
    }

    @Test
    void barriersSealBasesAndOpeningBarriersConnectsEverySpawnAndDomPoint() {
        ObsidianBunkerBlueprint.Built b = ObsidianBunkerBlueprint.build();
        var r0 = b.redSpawns.get(0);
        var b0 = b.blueSpawns.get(0);

        // 1. 屏障關閉：紅藍雙方都不能離開各自基地抵達任何據點或對方基地
        boolean[][][] redClosed = bfs(b, (int) Math.floor(r0.x()), (int) Math.floor(r0.y()), (int) Math.floor(r0.z()), true);
        boolean[][][] blueClosed = bfs(b, (int) Math.floor(b0.x()), (int) Math.floor(b0.y()), (int) Math.floor(b0.z()), true);
        assertFalse(redClosed[(int) Math.floor(b0.x())][(int) Math.floor(b0.y())][(int) Math.floor(b0.z())],
            "屏障關閉時紅隊不應能走到藍隊重生點");
        assertFalse(blueClosed[(int) Math.floor(r0.x())][(int) Math.floor(r0.y())][(int) Math.floor(r0.z())],
            "屏障關閉時藍隊不應能走到紅隊重生點");
        for (var pt : b.domPoints) {
            assertFalse(reachesPoint(redClosed, pt), "屏障關閉時紅隊不應能走到據點 " + pt.name());
            assertFalse(reachesPoint(blueClosed, pt), "屏障關閉時藍隊不應能走到據點 " + pt.name());
        }

        // 2. 屏障開啟：從紅隊重生點可走到藍隊基地、三個據點以及全部 44 個槍王重生點（含 2F）
        boolean[][][] open = bfs(b, (int) Math.floor(r0.x()), (int) Math.floor(r0.y()), (int) Math.floor(r0.z()), false);
        for (var s : b.redSpawns) {
            assertTrue(open[(int) Math.floor(s.x())][(int) Math.floor(s.y())][(int) Math.floor(s.z())],
                "應可走到紅隊重生點 " + s);
        }
        for (var s : b.blueSpawns) {
            assertTrue(open[(int) Math.floor(s.x())][(int) Math.floor(s.y())][(int) Math.floor(s.z())],
                "應可走到藍隊重生點 " + s);
        }
        for (var pt : b.domPoints) {
            assertTrue(reachesPoint(open, pt), "屏障開啟後應可走到據點 " + pt.name());
        }
        for (var gk : b.gkSpawns) {
            assertTrue(open[(int) Math.floor(gk.x())][(int) Math.floor(gk.y())][(int) Math.floor(gk.z())],
                "應可走到槍王重生點（含 2F）: " + gk);
        }
    }

    @Test
    void distinctDistrictsVerticalityAndCoverDensity() {
        ObsidianBunkerBlueprint.Built b = ObsidianBunkerBlueprint.build();
        var g = b.grid;
        int coverCount = 0;
        int labFloor = 0;
        int cargoFloor = 0;
        int containerBlocks = 0;
        int glassFloor = 0;
        int elevatorPairs = 0;
        for (int x = 0; x < ObsidianBunkerBlueprint.WIDTH; x++) {
            for (int z = 0; z < ObsidianBunkerBlueprint.DEPTH; z++) {
                int elevInCol = 0;
                for (int y = 0; y < ObsidianBunkerBlueprint.HEIGHT; y++) {
                    var c = g[x][y][z];
                    if (c.isCover()) coverCount++;
                    if (c == ObsidianBunkerBlueprint.Cell.FLOOR_LAB) labFloor++;
                    if (c == ObsidianBunkerBlueprint.Cell.FLOOR_CARGO) cargoFloor++;
                    if (c == ObsidianBunkerBlueprint.Cell.CONTAINER_ORANGE
                        || c == ObsidianBunkerBlueprint.Cell.CONTAINER_TEAL
                        || c == ObsidianBunkerBlueprint.Cell.CONTAINER_YELLOW) containerBlocks++;
                    if (c == ObsidianBunkerBlueprint.Cell.GLASS_FLOOR) glassFloor++;
                    if (c == ObsidianBunkerBlueprint.Cell.ELEVATOR) elevInCol++;
                }
                if (elevInCol >= 2) elevatorPairs++;
            }
        }
        assertTrue(coverCount >= 100, "掩體方塊數不足（預期至少 100）: " + coverCount);
        assertTrue(labFloor >= 150, "北區潔淨實驗室地板不足: " + labFloor);
        assertTrue(cargoFloor >= 200, "南區工業貨運碼頭地板不足: " + cargoFloor);
        assertTrue(containerBlocks >= 150, "南區集裝箱方塊數不足: " + containerBlocks);
        assertTrue(glassFloor >= 20, "中區懸空玻璃天橋地板不足: " + glassFloor);
        assertTrue(elevatorPairs >= 2, "應至少包含 2 組上下對齊的戰術電梯井: " + elevatorPairs);

        // 確認玩家步行（不靠電梯）即可到達 y=1 (一樓)、y=4 (中層觀測台／貨櫃頂)、y=7 (懸空天橋／天車)
        var r0 = b.redSpawns.get(0);
        boolean[][][] open = bfs(b, (int) Math.floor(r0.x()), (int) Math.floor(r0.y()), (int) Math.floor(r0.z()), false);
        assertTrue(open[42][4][19], "應可步行抵達北區實驗室 y=4 觀測夾層");
        assertTrue(open[30][4][32], "應可步行抵達中區反應爐 y=4 西側平台");
        assertTrue(open[33][4][51], "應可步行抵達南區橘色貨櫃 y=4 頂端");
        assertTrue(open[51][4][55], "應可步行抵達南區青色貨櫃 y=4 頂端");
        assertTrue(open[42][7][27], "應可步行抵達中區 y=7 懸空玻璃天橋");
        assertTrue(open[42][7][49], "應可步行抵達南區 y=7 裝卸天車桁架");
    }

    private static boolean reachesPoint(boolean[][][] vis, ObsidianBunkerBlueprint.LocalPoint pt) {
        for (int x = pt.fx0(); x <= pt.fx1(); x++) {
            for (int z = pt.fz0(); z <= pt.fz1(); z++) {
                if (vis[x][pt.cy() + 1][z]) return true;
            }
        }
        return false;
    }

    private static boolean[][][] bfs(ObsidianBunkerBlueprint.Built b, int sx, int sy, int sz, boolean withBarriers) {
        boolean[][][] barrierMask = new boolean[ObsidianBunkerBlueprint.WIDTH][ObsidianBunkerBlueprint.HEIGHT][ObsidianBunkerBlueprint.DEPTH];
        if (withBarriers) {
            for (var box : b.barriers) {
                for (int x = Math.min(box.x0(), box.x1()); x <= Math.max(box.x0(), box.x1()); x++) {
                    for (int y = Math.min(box.y0(), box.y1()); y <= Math.max(box.y0(), box.y1()); y++) {
                        for (int z = Math.min(box.z0(), box.z1()); z <= Math.max(box.z0(), box.z1()); z++) {
                            barrierMask[x][y][z] = true;
                        }
                    }
                }
            }
        }
        boolean[][][] vis = new boolean[ObsidianBunkerBlueprint.WIDTH][ObsidianBunkerBlueprint.HEIGHT][ObsidianBunkerBlueprint.DEPTH];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        if (canStandAt(b.grid, barrierMask, sx, sy, sz)) {
            vis[sx][sy][sz] = true;
            q.add(new int[] { sx, sy, sz });
        }
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int x = cur[0], y = cur[1], z = cur[2];
            for (int[] d : dirs) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < 1 || nx >= ObsidianBunkerBlueprint.WIDTH - 1 || nz < 1 || nz >= ObsidianBunkerBlueprint.DEPTH - 1) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 1 || ny >= ObsidianBunkerBlueprint.HEIGHT - 2) continue;
                    if (vis[nx][ny][nz]) continue;
                    if (!canStandAt(b.grid, barrierMask, nx, ny, nz)) continue;
                    // 上下 1 格台階時也確認頭頂不會撞到原本這格的天花板
                    if (dy == 1 && !passable(b.grid, barrierMask, x, y + 2, z)) continue;
                    if (dy == -1 && !passable(b.grid, barrierMask, nx, ny + 2, nz)) continue;
                    vis[nx][ny][nz] = true;
                    q.add(new int[] { nx, ny, nz });
                }
            }
        }
        return vis;
    }

    private static boolean canStandAt(ObsidianBunkerBlueprint.Cell[][][] g, boolean[][][] bar, int x, int y, int z) {
        return g[x][y - 1][z].isWalkableFloor()
            && passable(g, bar, x, y, z)
            && passable(g, bar, x, y + 1, z);
    }

    private static boolean passable(ObsidianBunkerBlueprint.Cell[][][] g, boolean[][][] bar, int x, int y, int z) {
        return !bar[x][y][z] && g[x][y][z].isPassable();
    }
}
