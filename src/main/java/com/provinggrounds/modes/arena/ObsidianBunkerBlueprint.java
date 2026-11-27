package com.provinggrounds.modes.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 檔案: ObsidianBunkerBlueprint.java
 * 目的: 內建團隊競技場地圖「黑曜地堡」(obsidian_bunker) 的純資料 3D 藍圖生成器。
 *
 * ── 設計特色（三大主題戰區 × 三層立體高程 × 180° 競技公平） ─────────────────────
 * 尺寸：85(X 東西) × 14(Y 高度) × 65(Z 南北)，完全封閉室內戰術設施。
 * 紅隊基地於西側 (x=1..12, z=25..39)，藍隊基地於東側 (x=72..83, z=25..39)，
 * 兩隊前往 A / B / C 三個據點的距離與交火節奏完全公平，但北／中／南三條戰線
 * 擁有截然不同的建築結構、視覺主題、交火距離與高低差玩法：
 *
 * 1. 北區 Point A「潔淨實驗室與觀測穹頂」(Cleanroom Bio-Lab, z=2..21)
 *    - 白色石英與青色玻璃的明亮無塵實驗室風格，八角切角挑高穹頂。
 *    - 北側氣閘繞後道採交錯隔間＋可擊破青色玻璃窗，打破無聊直線長廊。
 *    - 南側設有 y=4 觀測夾層，可隔著可擊破玻璃壓制 A 點或從中央開口直接空降突襲。
 *
 * 2. 中區 Point B「反應爐立體中庭與懸空天橋」(Multi-Level Reactor Atrium, z=22..42)
 *    - 三層立體交戰核心：y=1 下沉反應爐底池 (Point B)、y=4 東西雙層俯瞰平台、
 *      y=7 懸空玻璃環形天橋。
 *    - 東西中路採上下分流：低路穿過底層隧道直插反應爐，高路走寬階梯上 y=4 平台
 *      與北側實驗室觀測台、南側貨運通道連成全圖中層幹道。
 *    - 搭配 2 座對角戰術電梯井 (y=1 ↔ y=7)，支援團隊模式 1.8 秒啟動快速高點側襲。
 *
 * 3. 南區 Point C「工業貨運碼頭與集裝箱堆場」(Industrial Cargo Bay, z=43..62)
 *    - 暖色磚牆、黃黑警戒線與橘／青／黃三色大型貨櫃的工業倉儲風格。
 *    - 貨櫃採非對稱錯位擺放：東西各有一座「中空可穿越貨櫃」直插 C 點側翼，
 *      另有多座可透過貨箱階梯攀爬至 y=4 貨櫃頂端進行屋頂對槍的立體掩體。
 *    - 上方橫跨 y=7 裝卸天車桁架直連中庭天橋，南端並設管線維護溝提供近戰繞後。
 *
 * 注意：嚴禁出現任何木頭儲物箱／陷阱箱／木桶。
 */
public final class ObsidianBunkerBlueprint {

    public static final String MAP_ID = "obsidian_bunker";
    public static final String MAP_NAME = "黑曜地堡";

    public static final int WIDTH = 85;
    public static final int HEIGHT = 14;
    public static final int DEPTH = 65;

    public static final int BASE_RADIUS = 10;
    public static final int ANTI_CAMP_RADIUS = 14;

    public enum Cell {
        AIR,
        WALL_OUTER,
        WALL_INNER,
        WALL_RED,
        WALL_BLUE,
        WALL_LAB,
        WALL_CARGO,
        CEILING,
        FLOOR_MAIN,
        FLOOR_CORRIDOR,
        FLOOR_METAL,
        FLOOR_RED,
        FLOOR_BLUE,
        FLOOR_UPPER,
        FLOOR_LAB,
        FLOOR_CARGO,
        HAZARD_STRIPE,
        GLASS_FLOOR,
        POINT_FLOOR_A,
        POINT_FLOOR_B,
        POINT_FLOOR_C,
        CONTAINER_ORANGE,
        CONTAINER_TEAL,
        CONTAINER_YELLOW,
        ELEVATOR,
        LIGHT,
        LIGHT_WARM,
        REACTOR_CORE,
        GLASS_PANE,
        GLASS_CYAN,
        TINTED_GLASS,
        IRON_BARS,
        COVER_FULL,
        COVER_HALF,
        COVER_CRATE,
        COVER_SANDBAG,
        COVER_SERVER,
        COVER_PILLAR,
        COVER_PIPE,
        COVER_LAB,
        STAIR_E,
        STAIR_W,
        STAIR_N,
        STAIR_S;

        public boolean isWalkableFloor() {
            return switch (this) {
                case FLOOR_MAIN, FLOOR_CORRIDOR, FLOOR_METAL, FLOOR_RED, FLOOR_BLUE, FLOOR_UPPER,
                     FLOOR_LAB, FLOOR_CARGO, HAZARD_STRIPE, GLASS_FLOOR,
                     POINT_FLOOR_A, POINT_FLOOR_B, POINT_FLOOR_C,
                     CONTAINER_ORANGE, CONTAINER_TEAL, CONTAINER_YELLOW,
                     ELEVATOR, LIGHT, LIGHT_WARM,
                     STAIR_E, STAIR_W, STAIR_N, STAIR_S -> true;
                default -> false;
            };
        }

        public boolean isPassable() {
            return this == AIR || isStair();
        }

        public boolean isStair() {
            return this == STAIR_E || this == STAIR_W || this == STAIR_N || this == STAIR_S;
        }

        public boolean isCover() {
            return switch (this) {
                case COVER_FULL, COVER_HALF, COVER_CRATE, COVER_SANDBAG,
                     COVER_SERVER, COVER_PILLAR, COVER_PIPE, COVER_LAB -> true;
                default -> false;
            };
        }
    }

    public record LocalSpawn(double x, double y, double z) {}
    public record LocalBox(int x0, int y0, int z0, int x1, int y1, int z1) {}
    public record LocalPoint(String name, double cx, int cy, double cz, int radius,
                             int fx0, int fy0, int fz0, int fx1, int fy1, int fz1) {}

    public static final class Built {
        public final Cell[][][] grid;
        public final List<LocalSpawn> redSpawns;
        public final List<LocalSpawn> blueSpawns;
        public final List<LocalBox> barriers;
        public final List<LocalPoint> domPoints;
        public final List<LocalSpawn> gkSpawns;

        Built(Cell[][][] grid, List<LocalSpawn> redSpawns, List<LocalSpawn> blueSpawns,
              List<LocalBox> barriers, List<LocalPoint> domPoints, List<LocalSpawn> gkSpawns) {
            this.grid = grid;
            this.redSpawns = List.copyOf(redSpawns);
            this.blueSpawns = List.copyOf(blueSpawns);
            this.barriers = List.copyOf(barriers);
            this.domPoints = List.copyOf(domPoints);
            this.gkSpawns = List.copyOf(gkSpawns);
        }

        public ArenaMapRegistry.ArenaMapDef toMapDef(BlockPos origin) {
            List<Vec3> red = new ArrayList<>(redSpawns.size());
            for (LocalSpawn s : redSpawns) {
                red.add(new Vec3(origin.getX() + s.x(), origin.getY() + s.y(), origin.getZ() + s.z()));
            }
            List<Vec3> blue = new ArrayList<>(blueSpawns.size());
            for (LocalSpawn s : blueSpawns) {
                blue.add(new Vec3(origin.getX() + s.x(), origin.getY() + s.y(), origin.getZ() + s.z()));
            }
            List<BlockPos[]> bar = new ArrayList<>(barriers.size());
            for (LocalBox b : barriers) {
                bar.add(new BlockPos[] {
                    origin.offset(b.x0(), b.y0(), b.z0()),
                    origin.offset(b.x1(), b.y1(), b.z1())
                });
            }
            List<ArenaMapRegistry.DomPointDef> pts = new ArrayList<>(domPoints.size());
            for (LocalPoint p : domPoints) {
                pts.add(new ArenaMapRegistry.DomPointDef(
                    p.name(),
                    new Vec3(origin.getX() + p.cx(), origin.getY() + p.cy(), origin.getZ() + p.cz()),
                    p.radius(),
                    new BlockPos[] {
                        origin.offset(p.fx0(), p.fy0(), p.fz0()),
                        origin.offset(p.fx1(), p.fy1(), p.fz1())
                    }
                ));
            }
            List<Vec3> gk = new ArrayList<>(gkSpawns.size());
            for (LocalSpawn s : gkSpawns) {
                gk.add(new Vec3(origin.getX() + s.x(), origin.getY() + s.y(), origin.getZ() + s.z()));
            }
            return new ArenaMapRegistry.ArenaMapDef(
                MAP_ID, MAP_NAME, origin, WIDTH, HEIGHT, DEPTH,
                BASE_RADIUS, ANTI_CAMP_RADIUS, true,
                List.copyOf(red), List.copyOf(blue), List.copyOf(bar),
                List.of(), List.copyOf(pts), List.copyOf(gk)
            );
        }
    }

    private ObsidianBunkerBlueprint() {}

    public static Built build() {
        Cell[][][] g = new Cell[WIDTH][HEIGHT][DEPTH];

        // 1. 實心初始化＋外殼封閉 (y=0 基底、四面外牆、y=13 外頂蓋)
        fill(g, 0, 0, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1, Cell.WALL_INNER);
        fill(g, 0, 0, 0, WIDTH - 1, 0, DEPTH - 1, Cell.FLOOR_MAIN);
        fill(g, 0, 12, 0, WIDTH - 1, 12, DEPTH - 1, Cell.CEILING);
        fill(g, 0, 0, 0, 0, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);
        fill(g, WIDTH - 1, 0, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);
        fill(g, 0, 0, 0, WIDTH - 1, HEIGHT - 1, 0, Cell.WALL_OUTER);
        fill(g, 0, 0, DEPTH - 1, WIDTH - 1, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);
        fill(g, 0, HEIGHT - 1, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);

        // 2. 紅藍隊指揮基地（西 x=1..12, 東 x=72..83, z=25..39，各自 3 道屏障出口）
        buildBases(g);

        // 3. 北區 Point A：潔淨實驗室與觀測穹頂 (z=2..21)
        buildNorthLabDistrict(g);

        // 4. 中區 Point B：反應爐立體中庭、中層平台與懸空玻璃天橋 (z=22..42)
        buildCenterReactorDistrict(g);

        // 5. 南區 Point C：工業貨運碼頭、可穿越／可攀爬集裝箱與天車桁架 (z=43..62)
        buildSouthCargoDistrict(g);

        // 6. 戰術電梯井 (2 座 180° 對角配置，連接 y=1 與 y=7)
        buildElevatorShafts(g);

        // 7. 標記三個佔點據點地板
        fill(g, 39, 0, 8, 45, 0, 14, Cell.POINT_FLOOR_A);
        fill(g, 38, 0, 28, 46, 0, 36, Cell.POINT_FLOOR_B);
        fill(g, 39, 0, 50, 45, 0, 56, Cell.POINT_FLOOR_C);

        // 8. 區段特色掩體與全場照明
        placeTacticalCover(g);
        placeLighting(g);

        // 9. 重生點、出口屏障、佔點定義與槍王 44+ 重生點（涵蓋 y=1 / y=4 / y=7 三層）
        List<LocalSpawn> redSpawns = List.of(
            new LocalSpawn(6.5, 1, 32.5),
            new LocalSpawn(4.5, 1, 27.5),
            new LocalSpawn(4.5, 1, 37.5)
        );
        List<LocalSpawn> blueSpawns = List.of(
            new LocalSpawn(78.5, 1, 32.5),
            new LocalSpawn(80.5, 1, 27.5),
            new LocalSpawn(80.5, 1, 37.5)
        );
        List<LocalBox> barriers = List.of(
            new LocalBox(6, 1, 24, 8, 3, 24),
            new LocalBox(13, 1, 31, 13, 3, 33),
            new LocalBox(6, 1, 40, 8, 3, 40),
            new LocalBox(76, 1, 24, 78, 3, 24),
            new LocalBox(71, 1, 31, 71, 3, 33),
            new LocalBox(76, 1, 40, 78, 3, 40)
        );
        List<LocalPoint> domPoints = List.of(
            new LocalPoint("A", 42.5, 0, 11.5, 3, 39, 0, 8, 45, 0, 14),
            new LocalPoint("B", 42.5, 0, 32.5, 4, 38, 0, 28, 46, 0, 36),
            new LocalPoint("C", 42.5, 0, 53.5, 3, 39, 0, 50, 45, 0, 56)
        );
        List<LocalSpawn> gkSpawns = collectGkSpawns();

        clearSpawnTiles(g, redSpawns);
        clearSpawnTiles(g, blueSpawns);
        clearSpawnTiles(g, gkSpawns);

        // 最後再次確保最外殼完全封閉（防止任何輔助函式誤寫邊界）
        sealOuterShell(g);

        return new Built(g, redSpawns, blueSpawns, barriers, domPoints, gkSpawns);
    }

    // ── 基地與出入口 ────────────────────────────────────────────────────────

    private static void buildBases(Cell[][][] g) {
        // 紅隊基地（西側）
        carveRoom(g, 2, 1, 25, 12, 5, 39, Cell.FLOOR_RED);
        fill(g, 1, 1, 25, 1, 5, 39, Cell.WALL_RED);
        fill(g, 2, 6, 25, 12, 6, 39, Cell.CEILING);
        // 隊色門框標示
        fill(g, 5, 1, 25, 5, 4, 25, Cell.WALL_RED);
        fill(g, 9, 1, 25, 9, 4, 25, Cell.WALL_RED);
        fill(g, 5, 1, 39, 5, 4, 39, Cell.WALL_RED);
        fill(g, 9, 1, 39, 9, 4, 39, Cell.WALL_RED);
        fill(g, 12, 1, 30, 12, 4, 30, Cell.WALL_RED);
        fill(g, 12, 1, 34, 12, 4, 34, Cell.WALL_RED);

        // 藍隊基地（東側）
        carveRoom(g, 72, 1, 25, 82, 5, 39, Cell.FLOOR_BLUE);
        fill(g, 83, 1, 25, 83, 5, 39, Cell.WALL_BLUE);
        fill(g, 72, 6, 25, 82, 6, 39, Cell.CEILING);
        fill(g, 75, 1, 25, 75, 4, 25, Cell.WALL_BLUE);
        fill(g, 79, 1, 25, 79, 4, 25, Cell.WALL_BLUE);
        fill(g, 75, 1, 39, 75, 4, 39, Cell.WALL_BLUE);
        fill(g, 79, 1, 39, 79, 4, 39, Cell.WALL_BLUE);
        fill(g, 72, 1, 30, 72, 4, 30, Cell.WALL_BLUE);
        fill(g, 72, 1, 34, 72, 4, 34, Cell.WALL_BLUE);

        // 6 道基地出口（與 barriers 嚴格對應）
        carveDoor(g, 6, 24, 8, 24, 3, Cell.FLOOR_LAB);
        carveDoor(g, 13, 31, 13, 33, 3, Cell.FLOOR_METAL);
        carveDoor(g, 6, 40, 8, 40, 3, Cell.FLOOR_CARGO);

        carveDoor(g, 76, 24, 78, 24, 3, Cell.FLOOR_LAB);
        carveDoor(g, 71, 31, 71, 33, 3, Cell.FLOOR_METAL);
        carveDoor(g, 76, 40, 78, 40, 3, Cell.FLOOR_CARGO);
    }

    // ── 北區 Point A：潔淨實驗室與觀測穹頂 ──────────────────────────────────

    private static void buildNorthLabDistrict(Cell[][][] g) {
        // 先將北區牆體預先著色為潔淨實驗室白牆 (z=1..22)
        fill(g, 1, 1, 1, 83, 11, 22, Cell.WALL_LAB);

        // 1. 西北／東北實驗室入口前廳（接紅藍基地北門 z=24）
        carveRoom(g, 3, 1, 6, 12, 5, 23, Cell.FLOOR_LAB);
        carveRoom(g, 72, 1, 6, 81, 5, 23, Cell.FLOOR_LAB);
        // 前廳內斜角導流隔牆（避免一走出基地就被長距離狙擊）
        fill(g, 6, 1, 15, 9, 4, 15, Cell.WALL_LAB);
        fill(g, 75, 1, 15, 78, 4, 15, Cell.WALL_LAB);
        // 隔牆上的可擊破觀察窗
        fill(g, 7, 2, 15, 8, 3, 15, Cell.GLASS_CYAN);
        fill(g, 76, 2, 15, 77, 3, 15, Cell.GLASS_CYAN);

        // 2. 西翼與東翼實驗室 (x=14..32 & x=52..70, z=6..17)
        carveRoom(g, 14, 1, 6, 32, 5, 17, Cell.FLOOR_LAB);
        carveRoom(g, 52, 1, 6, 70, 5, 17, Cell.FLOOR_LAB);
        carveDoor(g, 13, 8, 13, 11, 4, Cell.FLOOR_LAB);
        carveDoor(g, 13, 14, 13, 16, 4, Cell.FLOOR_LAB);
        carveDoor(g, 71, 8, 71, 11, 4, Cell.FLOOR_LAB);
        carveDoor(g, 71, 14, 71, 16, 4, Cell.FLOOR_LAB);

        // 實驗室翼區玻璃隔間（營造無塵實驗隔間＋可破玻璃交火點）
        fill(g, 20, 1, 11, 27, 1, 11, Cell.WALL_LAB);
        fill(g, 20, 2, 11, 27, 3, 11, Cell.GLASS_CYAN);
        fill(g, 20, 4, 11, 27, 4, 11, Cell.WALL_LAB);
        fill(g, 57, 1, 11, 64, 1, 11, Cell.WALL_LAB);
        fill(g, 57, 2, 11, 64, 3, 11, Cell.GLASS_CYAN);
        fill(g, 57, 4, 11, 64, 4, 11, Cell.WALL_LAB);

        // 3. Point A 八角挑高潔淨穹頂 (x=34..50, z=5..16, y=1..11)
        carveRoom(g, 34, 1, 5, 50, 11, 16, Cell.FLOOR_LAB);
        // 四角切角柱營造八角實驗穹頂感
        fill(g, 34, 1, 5, 35, 11, 6, Cell.WALL_LAB);
        fill(g, 49, 1, 5, 50, 11, 6, Cell.WALL_LAB);
        fill(g, 34, 1, 15, 35, 11, 16, Cell.WALL_LAB);
        fill(g, 49, 1, 15, 50, 11, 16, Cell.WALL_LAB);
        // 切角發光燈條
        for (int y = 2; y <= 10; y += 2) {
            g[35][y][6] = Cell.LIGHT;
            g[49][y][6] = Cell.LIGHT;
            g[35][y][15] = Cell.LIGHT;
            g[49][y][15] = Cell.LIGHT;
        }
        // 東西雙進出門接兩翼實驗室
        carveDoor(g, 33, 7, 33, 9, 4, Cell.FLOOR_LAB);
        carveDoor(g, 33, 12, 33, 14, 4, Cell.FLOOR_LAB);
        carveDoor(g, 51, 7, 51, 9, 4, Cell.FLOOR_LAB);
        carveDoor(g, 51, 12, 51, 14, 4, Cell.FLOOR_LAB);

        // 4. 北側氣閘繞後道 (z=2..4, x=15..69) — 交錯氣閘門＋面向 A 點的玻璃觀景窗
        carveCorridor(g, 15, 1, 2, 69, 4, 4, Cell.FLOOR_LAB);
        carveDoor(g, 16, 5, 18, 5, 3, Cell.FLOOR_LAB);
        carveDoor(g, 66, 5, 68, 5, 3, Cell.FLOOR_LAB);
        carveDoor(g, 41, 4, 43, 5, 3, Cell.FLOOR_LAB);
        // 兩處交錯氣閘隔板（阻斷 55 格超長直線狙擊）
        fill(g, 28, 1, 2, 28, 4, 3, Cell.WALL_LAB);
        g[28][2][3] = Cell.GLASS_CYAN;
        fill(g, 56, 1, 3, 56, 4, 4, Cell.WALL_LAB);
        g[56][2][3] = Cell.GLASS_CYAN;
        // 氣閘道看進 A 點穹頂的觀測玻璃（z=5 牆體）
        for (int x : new int[] { 37, 38, 39, 45, 46, 47 }) {
            g[x][1][4] = Cell.FLOOR_LAB; // 保持氣閘走道可通
        }

        // 5. Point A 南側觀測夾層 (y=4, 地板 y=3, x=31..53, z=17..20)
        // 先清空 1F 夾層下方走廊（x=39..45, z=17..21, y=1..2? 不，y=1..2 太矮！讓中央通道 y=1..3 淨空，夾層於 y=4 挑空！）
        // 注意：y=4 地板在 y=3，所以 1F 通道頭頂淨空為 y=1..2（2 格高，但玩家站 1.8 格會覺得略壓迫）。
        // 改法：夾層主體設於 z=17..20, y=4（地板 y=3），而中央直通反應爐的 1F 大門改走 x=40..44, z=17..22，
        // 並讓夾層在 x=40..44 處挑高 1 格走天橋或繞過？更棒的做法：1F 地面通道保持 y=1..3 淨空（地板 y=0，門高 3 格），
        // 夾層高度設於 y=4（地板在 y=3，所以 1F 通道走 x=40..44 時，y=3 只有橋面薄薄一層 -> 1F 淨空為 y=1, y=2 共 2 格高）
        // 為了 1F 通道有舒適 3 格淨空，我們把 1F 連通 A 與 B 的兩條寬通道設在夾層東西兩端下方以外 (x=31..33 & x=51..53)？
        // 其實在 Minecraft 裡 1F 通道高 3 格 (y=1..3) 代表天花板在 y=4；若夾層地板在 y=3，則 y=1..2 有 2 格淨空（可順暢衝刺跳不撞頭？跳躍會微頂）。
        // 更好！讓夾層地板在 y=3，而中央 A↔B 主通道 (x=40..44) 保持 1F 全挑高 y=1..11，
        // 觀測夾層分為「西觀測台 (x=31..39, z=17..20, y=4)」與「東觀測台 (x=45..53, z=17..20, y=4)」，
        // 中間用一座 y=4 高架玻璃連橋 (x=40..44, z=19..20) 橫跨過去，這樣 1F 進 A 點的門 (z=17..18) 完全挑高不壓迫，
        // 只有 z=19..20 兩格寬過橋下是 2 格淨空，體驗超自然！
        carveRoom(g, 31, 4, 17, 53, 8, 20, Cell.FLOOR_LAB);
        // 面向 Point A 的全景可擊破觀測窗 (z=16, y=4..6)，中央 (x=41..43) 留空當空降突擊口！
        for (int x = 36; x <= 48; x++) {
            if (x >= 41 && x <= 43) {
                fill(g, x, 4, 16, x, 6, 16, Cell.AIR);
            } else {
                g[x][4][16] = Cell.COVER_LAB;
                g[x][5][16] = Cell.GLASS_CYAN;
                g[x][6][16] = Cell.GLASS_CYAN;
            }
        }
        // 西／東實驗室上 y=4 觀測台的階梯 (3 階: y=1→4)
        carveStairE(g, 28, 1, 18, 20, 3, Cell.FLOOR_LAB); // x=28,29,30 升至 x=31, y=4
        carveStairW(g, 56, 1, 18, 20, 3, Cell.FLOOR_LAB); // x=56,55,54 升至 x=53, y=4
        // 實驗室翼區接階梯的 1F 入口走道
        carveRoom(g, 24, 1, 18, 27, 4, 20, Cell.FLOOR_LAB);
        carveRoom(g, 57, 1, 18, 60, 4, 20, Cell.FLOOR_LAB);
        carveDoor(g, 25, 17, 27, 17, 3, Cell.FLOOR_LAB);
        carveDoor(g, 57, 17, 59, 17, 3, Cell.FLOOR_LAB);

        // 1F 中央 A↔B 聯絡通道 (x=40..44, z=17..23, y=1..2 穿過連橋下，其餘挑高)
        carveCorridor(g, 40, 1, 17, 44, 2, 23, Cell.FLOOR_LAB);
        // 1F 西／東 Lab↔Mid 側翼通道 (x=17..19 & x=65..67, z=18..25, y=1..4)
        carveCorridor(g, 17, 1, 18, 19, 4, 25, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 65, 1, 18, 67, 4, 25, Cell.FLOOR_CORRIDOR);
    }

    // ── 中區 Point B：反應爐立體中庭與懸空天橋 ──────────────────────────────

    private static void buildCenterReactorDistrict(Cell[][][] g) {
        // 1. 西／東中路集結廳（紅藍基地中門出來後立即面臨高低分流）
        carveRoom(g, 14, 1, 26, 23, 5, 38, Cell.FLOOR_METAL);
        carveRoom(g, 61, 1, 26, 70, 5, 38, Cell.FLOOR_METAL);

        // 2. 宏偉的中央反應爐挑高中庭 (x=27..57, z=22..42, 全挑高 y=1..11！)
        carveRoom(g, 27, 1, 22, 57, 11, 42, Cell.FLOOR_METAL);

        // 3. 中央反應爐核心能源柱 (x=41..43, z=31..33, y=1..11)
        buildReactorCore(g);

        // 4. Level 2 (y=4, 地板 y=3)：東西雙側俯瞰平台 (x=27..33 & x=51..57, z=22..42)
        //    並與北側實驗室觀測夾層 (z=20, y=4) 及南側貨運上層走道 (z=44, y=4) 無縫貫通！
        fill(g, 27, 3, 21, 33, 3, 43, Cell.FLOOR_UPPER);
        fill(g, 51, 3, 21, 57, 3, 43, Cell.FLOOR_UPPER);
        // 確保平台上方 y=4..11 暢通，以及連結北觀測台 (z=21) 與南貨運高台 (z=43) 暢通
        fill(g, 30, 4, 21, 33, 8, 21, Cell.AIR);
        fill(g, 51, 4, 21, 54, 8, 21, Cell.AIR);
        fill(g, 27, 4, 43, 33, 8, 43, Cell.AIR);
        fill(g, 51, 4, 43, 57, 8, 43, Cell.AIR);

        // 5. 低路隧道：從東西集結廳直穿 y=4 平台下方進入反應爐底池 (z=31..33, y=1..2)
        //    為求順暢不壓迫，將 x=24..26 (y=1..4) 與 z=31..33 (x=27..33, y=1..2) 完全打通
        carveCorridor(g, 24, 1, 31, 33, 2, 33, Cell.FLOOR_METAL);
        carveCorridor(g, 51, 1, 31, 60, 2, 33, Cell.FLOOR_METAL);

        // 6. 集結廳直上 y=4 平台的寬階梯（南北雙側各一，快速搶佔中層高地）
        carveStairE(g, 24, 1, 27, 29, 3, Cell.FLOOR_METAL); // x=24,25,26 升至 x=27, y=4
        carveStairE(g, 24, 1, 35, 37, 3, Cell.FLOOR_METAL);
        carveStairW(g, 60, 1, 27, 29, 3, Cell.FLOOR_METAL); // x=60,59,58 升至 x=57, y=4
        carveStairW(g, 60, 1, 35, 37, 3, Cell.FLOOR_METAL);

        // 7. 反應爐底池 (y=1) 爬回 y=4 平台的內側逃生階梯（180° 旋轉對稱）
        carveStairW(g, 36, 1, 24, 25, 3, Cell.FLOOR_METAL); // x=36,35,34 升至 x=33, y=4
        carveStairE(g, 48, 1, 39, 40, 3, Cell.FLOOR_METAL); // x=48,49,50 升至 x=51, y=4

        // 8. Level 3 (y=7, 地板 y=6)：懸空玻璃環形天橋與四向飛橋
        //    環繞反應爐外圈 (x=36..48, z=26..38，中空 x=39..45, z=29..35 可俯瞰 Point B)
        fill(g, 36, 6, 26, 48, 6, 38, Cell.FLOOR_UPPER);
        fill(g, 39, 6, 29, 45, 6, 35, Cell.AIR);
        // 內圈鑲嵌可站立玻璃地板條，視覺張力十足！
        for (int x = 38; x <= 46; x++) {
            g[x][6][28] = Cell.GLASS_FLOOR;
            g[x][6][36] = Cell.GLASS_FLOOR;
        }
        for (int z = 28; z <= 36; z++) {
            g[38][6][z] = Cell.GLASS_FLOOR;
            g[46][6][z] = Cell.GLASS_FLOOR;
        }
        // 東西飛橋連接至 y=7 樓梯平台
        fill(g, 28, 6, 31, 35, 6, 33, Cell.FLOOR_UPPER);
        fill(g, 49, 6, 31, 56, 6, 33, Cell.FLOOR_UPPER);
        // 南北飛橋連接至北側觀測陽台與南側天車桁架
        fill(g, 41, 6, 18, 43, 6, 25, Cell.FLOOR_UPPER);
        fill(g, 41, 6, 39, 43, 6, 48, Cell.FLOOR_UPPER);
        fill(g, 41, 7, 18, 43, 10, 25, Cell.AIR);
        fill(g, 41, 7, 39, 43, 10, 48, Cell.AIR);
        // 北側飛橋終端迷你狙擊觀景台（俯瞰 Point A）
        fill(g, 39, 6, 17, 45, 6, 18, Cell.FLOOR_LAB);
        fill(g, 39, 7, 17, 45, 10, 18, Cell.AIR);

        // 9. y=4 → y=7 上層階梯（180° 旋轉對稱：西側由北往南爬、東側由南往北爬）
        fill(g, 28, 6, 29, 30, 6, 33, Cell.FLOOR_UPPER); // 西側 y=7 樓梯平台
        carveStairS(g, 28, 30, 4, 26, 3, Cell.FLOOR_UPPER); // z=26,27,28 升至 z=29, y=7

        fill(g, 54, 6, 31, 56, 6, 35, Cell.FLOOR_UPPER); // 東側 y=7 樓梯平台
        carveStairN(g, 54, 56, 4, 38, 3, Cell.FLOOR_UPPER); // z=38,37,36 升至 z=35, y=7

        // 10. 懸空天橋低矮鐵欄杆／半高護欄（不擋視野又防失足）
        for (int x = 36; x <= 48; x++) {
            if (x == 41 || x == 42 || x == 43) continue; // 留南北飛橋口
            g[x][7][26] = Cell.IRON_BARS;
            g[x][7][38] = Cell.IRON_BARS;
        }
        for (int z = 27; z <= 37; z++) {
            if (z == 31 || z == 32 || z == 33) continue; // 留東西飛橋口
            g[36][7][z] = Cell.IRON_BARS;
            g[48][7][z] = Cell.IRON_BARS;
        }
    }

    // ── 南區 Point C：工業貨運碼頭與集裝箱堆場 ──────────────────────────────

    private static void buildSouthCargoDistrict(Cell[][][] g) {
        // 將南區牆體著色為工業紅磚／深板岩倉儲風 (z=43..63)
        fill(g, 1, 1, 44, 83, 11, 63, Cell.WALL_CARGO);

        // 1. 西南／東南貨運入口裝卸場（接紅藍基地南門 z=40）
        carveRoom(g, 3, 1, 41, 12, 5, 59, Cell.FLOOR_CARGO);
        carveRoom(g, 72, 1, 41, 81, 5, 59, Cell.FLOOR_CARGO);
        // 入口場地防直視導流貨牆
        fill(g, 6, 1, 48, 9, 3, 48, Cell.CONTAINER_ORANGE);
        fill(g, 75, 1, 48, 78, 3, 48, Cell.CONTAINER_TEAL);

        // 2. 宏大開放式工業貨倉大廳 (x=14..70, z=45..61, 全挑高 y=1..11)
        carveRoom(g, 14, 1, 45, 70, 11, 61, Cell.FLOOR_CARGO);
        // 寬敞裝卸大門連接西南／東南入口場
        carveDoor(g, 13, 48, 13, 52, 4, Cell.FLOOR_CARGO);
        carveDoor(g, 13, 55, 13, 58, 4, Cell.FLOOR_CARGO);
        carveDoor(g, 71, 48, 71, 52, 4, Cell.FLOOR_CARGO);
        carveDoor(g, 71, 55, 71, 58, 4, Cell.FLOOR_CARGO);

        // 1F 中路↔南貨倉連通通道
        carveCorridor(g, 17, 1, 39, 19, 4, 44, Cell.FLOOR_CARGO);
        carveCorridor(g, 65, 1, 39, 67, 4, 44, Cell.FLOOR_CARGO);
        carveCorridor(g, 40, 1, 43, 44, 4, 44, Cell.FLOOR_CARGO);

        // 3. Point C 中央裝卸台黃黑警戒框
        fill(g, 38, 0, 49, 46, 0, 57, Cell.HAZARD_STRIPE);

        // 4. 六座特色集裝箱（中空可穿越＋頂端 y=4 可攀爬立體對槍！）
        //    (a) 西側「中空可穿越橘色貨櫃」 (x=29..36, z=49..53, 頂蓋 y=3)
        buildWalkthroughContainerX(g, 29, 49, 36, 53, Cell.CONTAINER_ORANGE);
        //    (b) 東側「中空可穿越青色貨櫃」 (x=48..55, z=53..57, 頂蓋 y=3，Z 軸錯位打破單調對稱)
        buildWalkthroughContainerX(g, 48, 53, 55, 57, Cell.CONTAINER_TEAL);
        //    (c) 西南「黃色堆疊貨櫃」 (x=20..26, z=55..58, 頂蓋 y=3) + 西側貨箱爬梯
        buildSolidContainer(g, 20, 55, 26, 58, Cell.CONTAINER_YELLOW);
        carveStairE(g, 17, 1, 56, 57, 3, Cell.FLOOR_CARGO); // x=17,18,19 爬上 y=4 貨櫃頂
        //    (d) 東北「橘色堆疊貨櫃」 (x=58..64, z=48..51, 頂蓋 y=3) + 東側貨箱爬梯
        buildSolidContainer(g, 58, 48, 64, 51, Cell.CONTAINER_ORANGE);
        carveStairW(g, 67, 1, 49, 50, 3, Cell.FLOOR_CARGO); // x=67,66,65 爬上 y=4 貨櫃頂
        //    (e) 西北「青色高台貨櫃」 (x=20..25, z=46..48, 頂蓋 y=3)
        buildSolidContainer(g, 20, 46, 25, 48, Cell.CONTAINER_TEAL);
        //    (f) 東南「黃色高台貨櫃」 (x=59..64, z=57..59, 頂蓋 y=3)
        buildSolidContainer(g, 59, 57, 64, 59, Cell.CONTAINER_YELLOW);

        // 5. 貨櫃頂端 y=4 戰術鐵板連橋（讓玩家可在貨櫃群與中區 y=4 平台之間流暢跑酷交火！）
        fill(g, 26, 3, 46, 29, 3, 48, Cell.FLOOR_UPPER); // 西北青貨櫃 ↔ 西側中區 y=4 平台延伸
        fill(g, 29, 3, 44, 33, 3, 48, Cell.FLOOR_UPPER); // 接通西中區 y=4 平台 (x=27..33, z=43) ↔ 西橘貨櫃頂 (z=49)
        fill(g, 29, 4, 44, 33, 8, 44, Cell.AIR);
        fill(g, 25, 3, 52, 28, 3, 54, Cell.FLOOR_UPPER); // 西南黃貨櫃 ↔ 西橘貨櫃頂
        fill(g, 51, 3, 44, 57, 3, 49, Cell.FLOOR_UPPER); // 東中區 y=4 平台 ↔ 東北橘貨櫃頂 (x=58)
        fill(g, 51, 4, 44, 57, 8, 44, Cell.AIR);
        fill(g, 53, 3, 50, 57, 3, 52, Cell.FLOOR_UPPER); // 東北橘貨櫃頂 ↔ 東青貨櫃頂 (z=53)
        fill(g, 56, 3, 56, 58, 3, 58, Cell.FLOOR_UPPER); // 東青貨櫃頂 ↔ 東南黃貨櫃頂

        // 6. y=7 工業裝卸天車桁架（貫穿 z=44 隔牆，從中庭南飛橋 x=41..43 延伸至 Point C 正上方）
        fill(g, 41, 6, 44, 43, 6, 48, Cell.FLOOR_UPPER);
        fill(g, 41, 7, 44, 43, 10, 48, Cell.AIR);
        fill(g, 35, 6, 48, 49, 6, 50, Cell.FLOOR_UPPER); // 主天車橫樑
        fill(g, 41, 6, 51, 43, 6, 57, Cell.FLOOR_UPPER); // 吊臂延伸至 C 點上空（可空降）
        // 天車雙側細鋼樑裝飾（不阻擋跳下）
        g[35][7][48] = Cell.IRON_BARS;
        g[49][7][48] = Cell.IRON_BARS;
        g[35][7][50] = Cell.IRON_BARS;
        g[49][7][50] = Cell.IRON_BARS;
        // 兩端設工業警示黃黑條
        g[35][6][49] = Cell.HAZARD_STRIPE;
        g[49][6][49] = Cell.HAZARD_STRIPE;
    }

    // ── 戰術電梯井 (2 座 180° 對角配置) ─────────────────────────────────────

    private static void buildElevatorShafts(Cell[][][] g) {
        // 西北電梯井 (x=21, z=23)：1F 中路/實驗室交界 (y=1) ↔ 2F 西天橋延伸台 (y=7)
        carveRoom(g, 20, 1, 22, 22, 4, 24, Cell.FLOOR_METAL);
        carveDoor(g, 20, 25, 22, 25, 3, Cell.FLOOR_METAL); // 連西集結廳
        carveDoor(g, 19, 22, 19, 24, 3, Cell.FLOOR_METAL); // 連西北通道
        g[21][0][23] = Cell.ELEVATOR;
        // 上層電梯出口台 (y=7, x=20..27, z=22..24) 並連接至西側 y=7 平台 (x=28..30, z=29..33)
        fill(g, 20, 6, 22, 27, 6, 24, Cell.FLOOR_UPPER);
        fill(g, 20, 7, 22, 27, 10, 24, Cell.AIR);
        fill(g, 25, 6, 25, 27, 6, 31, Cell.FLOOR_UPPER);
        fill(g, 25, 7, 25, 27, 10, 31, Cell.AIR);
        g[21][6][23] = Cell.ELEVATOR;

        // 東南電梯井 (x=63, z=41)：1F 中路/貨倉交界 (y=1) ↔ 2F 東天橋延伸台 (y=7)
        carveRoom(g, 62, 1, 40, 64, 4, 42, Cell.FLOOR_METAL);
        carveDoor(g, 62, 39, 64, 39, 3, Cell.FLOOR_METAL); // 連東集結廳
        carveDoor(g, 65, 40, 65, 42, 3, Cell.FLOOR_METAL); // 連東南通道
        g[63][0][41] = Cell.ELEVATOR;
        // 上層電梯出口台 (y=7, x=57..64, z=40..42) 並連接至東側 y=7 平台 (x=54..56, z=31..35)
        fill(g, 57, 6, 40, 64, 6, 42, Cell.FLOOR_UPPER);
        fill(g, 57, 7, 40, 64, 10, 42, Cell.AIR);
        fill(g, 57, 6, 33, 59, 6, 39, Cell.FLOOR_UPPER);
        fill(g, 57, 7, 33, 59, 10, 39, Cell.AIR);
        g[63][6][41] = Cell.ELEVATOR;
    }

    // ── 掩體與照明 ──────────────────────────────────────────────────────────

    private static void placeTacticalCover(Cell[][][] g) {
        // 1. 紅藍基地內側防線沙包
        for (int z : new int[] { 27, 32, 37 }) {
            placeCover(g, 10, 1, z, Cell.COVER_SANDBAG, false);
            placeCover(g, 74, 1, z, Cell.COVER_SANDBAG, false);
        }

        // 2. 北區實驗室：潔淨實驗桌、主機櫃與標本柱
        for (int[] p : new int[][] {
            { 8, 9 }, { 5, 19 }, { 76, 9 }, { 79, 19 },
            { 17, 8 }, { 22, 8 }, { 29, 8 }, { 17, 14 }, { 24, 15 },
            { 67, 8 }, { 62, 8 }, { 55, 8 }, { 67, 14 }, { 60, 15 }
        }) {
            placeCover(g, p[0], 1, p[1], Cell.COVER_LAB, false);
        }
        for (int[] p : new int[][] {
            { 25, 7 }, { 30, 14 }, { 59, 7 }, { 54, 14 }
        }) {
            placeCover(g, p[0], 1, p[1], Cell.COVER_SERVER, true);
        }
        // 北氣閘道低矮掩體
        for (int x : new int[] { 21, 34, 49, 62 }) {
            placeCover(g, x, 1, (x % 2 == 0) ? 2 : 4, Cell.COVER_LAB, false);
        }
        // Point A 潔淨室內部掩體（對角標本柱＋實驗矮桌）
        placeCover(g, 38, 1, 8, Cell.COVER_SERVER, true);
        placeCover(g, 46, 1, 14, Cell.COVER_SERVER, true);
        placeCover(g, 42, 1, 9, Cell.COVER_LAB, false);
        placeCover(g, 42, 1, 13, Cell.COVER_LAB, false);
        placeCover(g, 39, 1, 13, Cell.COVER_HALF, false);
        placeCover(g, 45, 1, 9, Cell.COVER_HALF, false);
        // Point A 南側觀測夾層 (y=4) 掩體
        for (int x : new int[] { 34, 38, 46, 50 }) {
            placeCover(g, x, 4, 19, Cell.COVER_LAB, false);
        }

        // 3. 中區反應爐中庭：三層立體掩體
        // 東西集結廳掩體
        for (int[] p : new int[][] {
            { 16, 28 }, { 16, 36 }, { 20, 31 }, { 20, 34 },
            { 68, 28 }, { 68, 36 }, { 64, 31 }, { 64, 34 }
        }) {
            placeCover(g, p[0], 1, p[1], Cell.COVER_FULL, p[0] == 20 || p[0] == 64);
        }
        // 反應爐底池 (y=1) 四向防爆掩體
        for (int[] p : new int[][] {
            { 37, 27 }, { 47, 27 }, { 37, 37 }, { 47, 37 },
            { 39, 30 }, { 45, 34 }, { 39, 34 }, { 45, 30 }
        }) {
            placeCover(g, p[0], 1, p[1], Cell.COVER_SANDBAG, false);
        }
        placeCover(g, 36, 1, 32, Cell.COVER_HALF, false);
        placeCover(g, 48, 1, 32, Cell.COVER_HALF, false);
        // 中層雙側俯瞰平台 (y=4) 邊緣防禦沙包與全高掩體
        for (int z : new int[] { 24, 28, 32, 36, 40 }) {
            placeCover(g, 33, 4, z, (z == 32) ? Cell.COVER_FULL : Cell.COVER_SANDBAG, z == 32);
            placeCover(g, 51, 4, z, (z == 32) ? Cell.COVER_FULL : Cell.COVER_SANDBAG, z == 32);
        }
        for (int z : new int[] { 26, 38 }) {
            placeCover(g, 29, 4, z, Cell.COVER_HALF, false);
            placeCover(g, 55, 4, z, Cell.COVER_HALF, false);
        }
        // 上層懸空天橋 (y=7) 戰術掩體
        for (int[] p : new int[][] {
            { 37, 27 }, { 47, 27 }, { 37, 37 }, { 47, 37 },
            { 31, 31 }, { 53, 33 }, { 41, 22 }, { 43, 42 }
        }) {
            placeCover(g, p[0], 7, p[1], Cell.COVER_HALF, false);
        }

        // 4. 南區貨運碼頭：貨箱、管線與屋頂掩體
        for (int[] p : new int[][] {
            { 8, 44 }, { 5, 54 }, { 76, 44 }, { 79, 54 },
            { 16, 47 }, { 16, 53 }, { 68, 47 }, { 68, 53 },
            { 27, 50 }, { 27, 57 }, { 57, 50 }, { 57, 57 },
            { 35, 46 }, { 49, 46 }, { 34, 57 }, { 50, 59 }
        }) {
            placeCover(g, p[0], 1, p[1], Cell.COVER_CRATE, (p[0] == 27 || p[0] == 57));
        }
        // 南側維護溝管線與沙包
        for (int x : new int[] { 22, 31, 40, 44, 53, 62 }) {
            placeCover(g, x, 1, 60, (x % 2 == 0) ? Cell.COVER_PIPE : Cell.COVER_SANDBAG, x % 4 == 0);
        }
        // Point C 裝卸台四角掩體
        placeCover(g, 40, 1, 51, Cell.COVER_CRATE, false);
        placeCover(g, 44, 1, 55, Cell.COVER_CRATE, false);
        placeCover(g, 40, 1, 55, Cell.COVER_SANDBAG, false);
        placeCover(g, 44, 1, 51, Cell.COVER_SANDBAG, false);
        // 貨櫃頂端 (y=4) 屋頂對槍掩體
        for (int[] p : new int[][] {
            { 22, 56 }, { 32, 50 }, { 35, 52 },
            { 50, 54 }, { 53, 56 }, { 61, 49 },
            { 22, 47 }, { 61, 58 }
        }) {
            placeCover(g, p[0], 4, p[1], Cell.COVER_SANDBAG, false);
        }
        // 天車桁架 (y=7) 掩體
        placeCover(g, 37, 7, 49, Cell.COVER_HALF, false);
        placeCover(g, 47, 7, 49, Cell.COVER_HALF, false);
        placeCover(g, 42, 7, 54, Cell.COVER_SANDBAG, false);
    }

    private static void placeLighting(Cell[][][] g) {
        // 挑高頂棚 y=12 嵌燈（依北冷光／中混合／南暖光區分視覺氛圍！）
        for (int x = 14; x <= WIDTH - 15; x += 5) {
            for (int z = 5; z <= DEPTH - 6; z += 5) {
                if (g[x][11][z] == Cell.AIR && g[x][12][z] == Cell.CEILING) {
                    if (z <= 21) {
                        g[x][12][z] = Cell.LIGHT; // 北區實驗室：冷白海晶燈
                    } else if (z >= 44) {
                        g[x][12][z] = Cell.LIGHT_WARM; // 南區貨倉：暖黃蛙光燈
                    } else {
                        g[x][12][z] = ((x + z) % 10 == 0) ? Cell.LIGHT : Cell.LIGHT_WARM;
                    }
                }
            }
        }
        // 低天花板室（前廳／翼室／走廊）y=6 或 y=5 嵌燈
        for (int x = 4; x <= WIDTH - 5; x += 5) {
            for (int z = 4; z <= DEPTH - 5; z += 5) {
                if (g[x][4][z] == Cell.AIR && (g[x][5][z] == Cell.WALL_LAB || g[x][5][z] == Cell.WALL_CARGO || g[x][5][z] == Cell.WALL_INNER)) {
                    g[x][5][z] = (z <= 24) ? Cell.LIGHT : Cell.LIGHT_WARM;
                } else if (g[x][5][z] == Cell.AIR && g[x][6][z] == Cell.CEILING) {
                    g[x][6][z] = (z <= 24) ? Cell.LIGHT : Cell.LIGHT_WARM;
                }
            }
        }
        // 紅藍基地主燈
        for (int z : new int[] { 28, 32, 36 }) {
            g[5][6][z] = Cell.LIGHT_WARM;
            g[79][6][z] = Cell.LIGHT_WARM;
        }
    }

    // ── 幾何雕刻輔助 ────────────────────────────────────────────────────────

    private static void buildReactorCore(Cell[][][] g) {
        for (int y = 1; y <= 11; y++) {
            g[41][y][31] = Cell.COVER_PILLAR;
            g[43][y][31] = Cell.COVER_PILLAR;
            g[41][y][33] = Cell.COVER_PILLAR;
            g[43][y][33] = Cell.COVER_PILLAR;
            g[42][y][32] = (y % 2 == 1) ? Cell.REACTOR_CORE : Cell.COVER_PILLAR;
            g[42][y][31] = (y >= 2 && y <= 10) ? Cell.TINTED_GLASS : Cell.COVER_PILLAR;
            g[42][y][33] = (y >= 2 && y <= 10) ? Cell.TINTED_GLASS : Cell.COVER_PILLAR;
            g[41][y][32] = (y >= 2 && y <= 10) ? Cell.TINTED_GLASS : Cell.COVER_PILLAR;
            g[43][y][32] = (y >= 2 && y <= 10) ? Cell.TINTED_GLASS : Cell.COVER_PILLAR;
        }
    }

    /** 建造中空可東西穿越、且頂部 y=3 可站立 (y=4) 的集裝箱 */
    private static void buildWalkthroughContainerX(Cell[][][] g, int x0, int z0, int x1, int z1, Cell wall) {
        fill(g, x0, 1, z0, x1, 3, z1, wall);
        // 挖空內部通道 (z0+1..z1-1, y=1..2)，貫穿 x0..x1
        fill(g, x0, 1, z0 + 1, x1, 2, z1 - 1, Cell.AIR);
        // 貨櫃內部嵌一盞暖燈避免太暗
        g[(x0 + x1) / 2][3][(z0 + z1) / 2] = Cell.LIGHT_WARM;
    }

    /** 建造實心堆疊貨櫃（頂部 y=3 可站立 y=4） */
    private static void buildSolidContainer(Cell[][][] g, int x0, int z0, int x1, int z1, Cell wall) {
        fill(g, x0, 1, z0, x1, 3, z1, wall);
    }

    private static void carveStairE(Cell[][][] g, int xStart, int yStart, int z0, int z1, int steps, Cell baseFloor) {
        for (int k = 0; k < steps; k++) {
            int x = xStart + k;
            int stepY = yStart + k;
            for (int z = z0; z <= z1; z++) {
                if (yStart == 1) g[x][0][z] = baseFloor;
                for (int y = yStart; y < stepY; y++) g[x][y][z] = Cell.WALL_INNER;
                g[x][stepY][z] = Cell.STAIR_E;
                for (int y = stepY + 1; y <= Math.min(11, stepY + 4); y++) g[x][y][z] = Cell.AIR;
            }
        }
    }

    private static void carveStairW(Cell[][][] g, int xStart, int yStart, int z0, int z1, int steps, Cell baseFloor) {
        for (int k = 0; k < steps; k++) {
            int x = xStart - k;
            int stepY = yStart + k;
            for (int z = z0; z <= z1; z++) {
                if (yStart == 1) g[x][0][z] = baseFloor;
                for (int y = yStart; y < stepY; y++) g[x][y][z] = Cell.WALL_INNER;
                g[x][stepY][z] = Cell.STAIR_W;
                for (int y = stepY + 1; y <= Math.min(11, stepY + 4); y++) g[x][y][z] = Cell.AIR;
            }
        }
    }

    private static void carveStairS(Cell[][][] g, int x0, int x1, int yStart, int zStart, int steps, Cell baseFloor) {
        for (int k = 0; k < steps; k++) {
            int z = zStart + k;
            int stepY = yStart + k;
            for (int x = x0; x <= x1; x++) {
                if (yStart == 1) g[x][0][z] = baseFloor;
                for (int y = yStart; y < stepY; y++) g[x][y][z] = Cell.WALL_INNER;
                g[x][stepY][z] = Cell.STAIR_S;
                for (int y = stepY + 1; y <= Math.min(11, stepY + 4); y++) g[x][y][z] = Cell.AIR;
            }
        }
    }

    private static void carveStairN(Cell[][][] g, int x0, int x1, int yStart, int zStart, int steps, Cell baseFloor) {
        for (int k = 0; k < steps; k++) {
            int z = zStart - k;
            int stepY = yStart + k;
            for (int x = x0; x <= x1; x++) {
                if (yStart == 1) g[x][0][z] = baseFloor;
                for (int y = yStart; y < stepY; y++) g[x][y][z] = Cell.WALL_INNER;
                g[x][stepY][z] = Cell.STAIR_N;
                for (int y = stepY + 1; y <= Math.min(11, stepY + 4); y++) g[x][y][z] = Cell.AIR;
            }
        }
    }

    private static void fill(Cell[][][] g, int x0, int y0, int z0, int x1, int y1, int z1, Cell c) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1);
        int ay = Math.min(y0, y1), by = Math.max(y0, y1);
        int az = Math.min(z0, z1), bz = Math.max(z0, z1);
        for (int x = ax; x <= bx; x++) {
            for (int y = ay; y <= by; y++) {
                for (int z = az; z <= bz; z++) {
                    g[x][y][z] = c;
                }
            }
        }
    }

    private static void carveRoom(Cell[][][] g, int x0, int y0, int z0, int x1, int y1, int z1, Cell floor) {
        fill(g, x0, y0 - 1, z0, x1, y0 - 1, z1, floor);
        fill(g, x0, y0, z0, x1, y1, z1, Cell.AIR);
    }

    private static void carveCorridor(Cell[][][] g, int x0, int y0, int z0, int x1, int y1, int z1, Cell floor) {
        fill(g, x0, y0 - 1, z0, x1, y0 - 1, z1, floor);
        fill(g, x0, y0, z0, x1, y1, z1, Cell.AIR);
    }

    private static void carveDoor(Cell[][][] g, int x0, int z0, int x1, int z1, int height, Cell floor) {
        fill(g, x0, 0, z0, x1, 0, z1, floor);
        fill(g, x0, 1, z0, x1, height, z1, Cell.AIR);
    }

    private static void sealOuterShell(Cell[][][] g) {
        fill(g, 0, 0, 0, 0, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);
        fill(g, WIDTH - 1, 0, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);
        fill(g, 0, 0, 0, WIDTH - 1, HEIGHT - 1, 0, Cell.WALL_OUTER);
        fill(g, 0, 0, DEPTH - 1, WIDTH - 1, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);
        fill(g, 0, HEIGHT - 1, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);
    }

    private static void placeCover(Cell[][][] g, int x, int y, int z, Cell type, boolean twoHigh) {
        if (x <= 1 || x >= WIDTH - 2 || z <= 1 || z >= DEPTH - 2) return;
        if (!g[x][y - 1][z].isWalkableFloor() || g[x][y][z] != Cell.AIR) return;
        g[x][y][z] = type;
        if (twoHigh && g[x][y + 1][z] == Cell.AIR) {
            g[x][y + 1][z] = type;
        }
    }

    private static void clearSpawnTiles(Cell[][][] g, List<LocalSpawn> spawns) {
        for (LocalSpawn s : spawns) {
            int sx = (int) Math.floor(s.x());
            int sy = (int) Math.floor(s.y());
            int sz = (int) Math.floor(s.z());
            if (sx < 1 || sx >= WIDTH - 1 || sy < 1 || sy >= HEIGHT - 2 || sz < 1 || sz >= DEPTH - 1) continue;
            if (!g[sx][sy - 1][sz].isWalkableFloor()) {
                g[sx][sy - 1][sz] = Cell.FLOOR_CORRIDOR;
            }
            if (g[sx][sy][sz].isCover() || g[sx][sy][sz] == Cell.AIR) g[sx][sy][sz] = Cell.AIR;
            if (g[sx][sy + 1][sz].isCover() || g[sx][sy + 1][sz] == Cell.AIR) g[sx][sy + 1][sz] = Cell.AIR;
        }
    }

    private static List<LocalSpawn> collectGkSpawns() {
        int[][] coords = {
            // 紅藍基地內側與前後廳（8 點）
            { 4, 1, 29 }, { 4, 1, 35 }, { 80, 1, 29 }, { 80, 1, 35 },
            { 7, 1, 10 }, { 77, 1, 10 }, { 7, 1, 52 }, { 77, 1, 52 },
            // 北區潔淨實驗室 1F & 氣閘道 & 觀測夾層 y=4（12 點）
            { 18, 1, 3 }, { 42, 1, 3 }, { 66, 1, 3 },
            { 18, 1, 9 }, { 26, 1, 14 }, { 58, 1, 14 }, { 66, 1, 9 },
            { 37, 1, 11 }, { 47, 1, 11 },
            { 35, 4, 18 }, { 42, 4, 19 }, { 49, 4, 18 },
            // 中區反應爐集結廳 & 底池 y=1 & 雙層平台 y=4（10 點）
            { 18, 1, 29 }, { 18, 1, 35 }, { 66, 1, 29 }, { 66, 1, 35 },
            { 38, 1, 32 }, { 46, 1, 32 },
            { 30, 4, 24 }, { 30, 4, 40 }, { 54, 4, 24 }, { 54, 4, 40 },
            // 南區工業貨運碼頭 1F & 貨櫃頂端 y=4（10 點）
            { 17, 1, 50 }, { 67, 1, 52 }, { 32, 1, 51 }, { 52, 1, 55 },
            { 30, 1, 59 }, { 54, 1, 59 },
            { 23, 4, 56 }, { 33, 4, 51 }, { 51, 4, 55 }, { 61, 4, 49 },
            // 頂層懸空天橋與天車桁架 y=7（6 點，總計 46 點）
            { 37, 7, 32 }, { 47, 7, 32 }, { 42, 7, 27 }, { 42, 7, 37 },
            { 42, 7, 18 }, { 42, 7, 49 }
        };
        List<LocalSpawn> out = new ArrayList<>(coords.length);
        for (int[] c : coords) {
            out.add(new LocalSpawn(c[0] + 0.5, c[1], c[2] + 0.5));
        }
        return out;
    }
}
