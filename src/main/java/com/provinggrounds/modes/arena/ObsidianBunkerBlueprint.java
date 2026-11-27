package com.provinggrounds.modes.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 檔案: ObsidianBunkerBlueprint.java
 * 目的: 雙層室內近距巷戰團隊地圖「黑曜地堡」（obsidian_bunker）純座標體素藍圖。
 *   - 適用於團隊死鬥（TDM）、佔點模式（DOM）與槍王之王（GK）。
 *   - 全室內雙層結構（85 × 14 × 65 格）：所有主走道與支線皆控制在 3～4 格寬，內部交錯布置大量矮牆、沙包、防爆牆、機櫃與貨櫃掩體。
 *   - 紅藍兩隊基地各有 3 個獨立出口（含屏障區域），4 座 3 格寬樓梯連接 1F 與 2F 環形天橋，完全無木箱／木桶。
 *   - 本類別刻意不引用 Forge Registry／Blocks，可在無伺服器啟動的 JUnit 單元測試直接進行 3D BFS 連通性與封鎖驗證。
 */
public final class ObsidianBunkerBlueprint {

    public static final String MAP_ID = "obsidian_bunker";
    public static final String MAP_NAME = "黑曜地堡";
    public static final int WIDTH = 85;   // X: 0..84
    public static final int HEIGHT = 14;  // Y: 0..13
    public static final int DEPTH = 65;   // Z: 0..64
    public static final int BASE_RADIUS = 7;
    public static final int ANTI_CAMP_RADIUS = 15;

    public enum Cell {
        AIR(false, true),
        WALL_OUTER(true, false),
        WALL_INNER(true, false),
        WALL_RED(true, false),
        WALL_BLUE(true, false),
        CEILING(true, false),
        FLOOR_MAIN(true, false),
        FLOOR_CORRIDOR(true, false),
        FLOOR_METAL(true, false),
        FLOOR_RED(true, false),
        FLOOR_BLUE(true, false),
        FLOOR_UPPER(true, false),
        POINT_FLOOR_A(true, false),
        POINT_FLOOR_B(true, false),
        POINT_FLOOR_C(true, false),
        LIGHT(true, false),
        LIGHT_WARM(true, false),
        REACTOR_CORE(true, false),
        GLASS_PANE(false, false),
        TINTED_GLASS(true, false),
        IRON_BARS(false, false),
        COVER_FULL(true, false),
        COVER_HALF(false, false),
        COVER_CRATE(true, false),
        COVER_SANDBAG(false, false),
        COVER_SERVER(true, false),
        COVER_PILLAR(true, false),
        COVER_PIPE(true, false),
        STAIR_E(true, false),
        STAIR_W(true, false),
        STAIR_N(true, false),
        STAIR_S(true, false);

        private final boolean walkableFloor;
        private final boolean passable;

        Cell(boolean walkableFloor, boolean passable) {
            this.walkableFloor = walkableFloor;
            this.passable = passable;
        }

        /** 是否可視為腳下可踩踏的完整頂面方塊／階梯。 */
        public boolean isWalkableFloor() {
            return walkableFloor;
        }

        /** 人物軀幹與頭部是否能直接穿過此格（不可破壞的玻璃／鐵欄／掩體皆為 false）。 */
        public boolean isPassable() {
            return passable;
        }

        public boolean isCover() {
            return this == COVER_FULL || this == COVER_HALF || this == COVER_CRATE
                || this == COVER_SANDBAG || this == COVER_SERVER || this == COVER_PILLAR || this == COVER_PIPE;
        }
    }

    public record LocalPoint(String name, double cx, int cy, double cz, int radius,
                             int fx0, int fy0, int fz0, int fx1, int fy1, int fz1) {}

    public record LocalBox(int x0, int y0, int z0, int x1, int y1, int z1) {}

    public record LocalSpawn(double x, double y, double z) {}

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
                BASE_RADIUS, ANTI_CAMP_RADIUS,
                List.copyOf(red), List.copyOf(blue), List.copyOf(bar),
                List.of(), List.copyOf(pts), List.copyOf(gk)
            );
        }
    }

    private ObsidianBunkerBlueprint() {}

    public static Built build() {
        Cell[][][] g = new Cell[WIDTH][HEIGHT][DEPTH];

        // 1. 先填滿實心牆體與雙層樓板，再逐條開挖走廊與房間，確保全封閉無漏風
        fill(g, 0, 0, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1, Cell.WALL_OUTER);
        fill(g, 1, 0, 1, WIDTH - 2, 0, DEPTH - 2, Cell.FLOOR_MAIN);
        fill(g, 1, 1, 1, WIDTH - 2, 5, DEPTH - 2, Cell.WALL_INNER);
        fill(g, 1, 6, 1, WIDTH - 2, 6, DEPTH - 2, Cell.CEILING);
        fill(g, 1, 7, 1, WIDTH - 2, 11, DEPTH - 2, Cell.WALL_INNER);
        fill(g, 1, 12, 1, WIDTH - 2, 12, DEPTH - 2, Cell.CEILING);

        // 2. 1F 紅藍基地＋3 出口（y = 1..4 挑高 4 格，出口屏障 3×3 與大廳門楣完全密合）
        carveRoom(g, 2, 1, 25, 12, 4, 39, Cell.FLOOR_RED);
        carveRoom(g, 72, 1, 25, 82, 4, 39, Cell.FLOOR_BLUE);
        // 基地牆面陣營識別色帶
        fill(g, 2, 2, 24, 12, 2, 24, Cell.WALL_RED);
        fill(g, 2, 2, 40, 12, 2, 40, Cell.WALL_RED);
        fill(g, 1, 2, 25, 1, 2, 39, Cell.WALL_RED);
        fill(g, 72, 2, 24, 82, 2, 24, Cell.WALL_BLUE);
        fill(g, 72, 2, 40, 82, 2, 40, Cell.WALL_BLUE);
        fill(g, 83, 2, 25, 83, 2, 39, Cell.WALL_BLUE);
        // 基地內部戰術隔牆（把基地內部也壓縮成 3 格寬巷道，防敵人從門口一眼瞄到底）
        fill(g, 9, 1, 27, 9, 3, 29, Cell.WALL_INNER);
        fill(g, 9, 1, 35, 9, 3, 37, Cell.WALL_INNER);
        fill(g, 75, 1, 27, 75, 3, 29, Cell.WALL_INNER);
        fill(g, 75, 1, 35, 75, 3, 37, Cell.WALL_INNER);

        // 基地 3 個出口通道（先鑿通，實際對戰於 PREP 放屏障、開打清空氣）
        carveCorridor(g, 6, 1, 24, 8, 3, 24, Cell.FLOOR_RED);     // 紅北門
        carveCorridor(g, 13, 1, 31, 13, 3, 33, Cell.FLOOR_RED);   // 紅中門
        carveCorridor(g, 6, 1, 40, 8, 3, 40, Cell.FLOOR_RED);     // 紅南門
        carveCorridor(g, 76, 1, 24, 78, 3, 24, Cell.FLOOR_BLUE);  // 藍北門
        carveCorridor(g, 71, 1, 31, 71, 3, 33, Cell.FLOOR_BLUE);  // 藍中門
        carveCorridor(g, 76, 1, 40, 78, 3, 40, Cell.FLOOR_BLUE);  // 藍南門

        // 3. 1F 走廊網（全數控制在 3～4 格寬）
        // 西側南北縱向走道（寬 4）
        carveCorridor(g, 5, 1, 4, 8, 4, 23, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 5, 1, 41, 8, 4, 60, Cell.FLOOR_CORRIDOR);
        // 東側南北縱向走道（寬 4）
        carveCorridor(g, 76, 1, 4, 79, 4, 23, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 76, 1, 41, 79, 4, 60, Cell.FLOOR_CORRIDOR);
        // 外圈北／南長廊（寬 4）
        carveCorridor(g, 5, 1, 3, 79, 4, 6, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 5, 1, 58, 79, 4, 61, Cell.FLOOR_CORRIDOR);
        // 內圈北／南整備道（寬 3）
        carveCorridor(g, 17, 1, 17, 67, 4, 19, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 17, 1, 45, 67, 4, 47, Cell.FLOOR_CORRIDOR);
        // 中路突穿走廊（寬 3）
        carveCorridor(g, 14, 1, 31, 36, 4, 33, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 48, 1, 31, 70, 4, 33, Cell.FLOOR_CORRIDOR);
        // 西／東外橫向交叉道（寬 4）
        carveCorridor(g, 17, 1, 7, 20, 4, 57, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 64, 1, 7, 67, 4, 57, Cell.FLOOR_CORRIDOR);
        // 西／東內側翼包抄道（寬 3）
        carveCorridor(g, 29, 1, 20, 31, 4, 44, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 53, 1, 20, 55, 4, 44, Cell.FLOOR_CORRIDOR);
        // 中央北／南連結通道（寬 3）
        carveCorridor(g, 41, 1, 20, 43, 4, 26, Cell.FLOOR_CORRIDOR);
        carveCorridor(g, 41, 1, 38, 43, 4, 44, Cell.FLOOR_CORRIDOR);

        // 4. 1F 機房／貨倉與中翼側室（內部再隔成 2～3 格窄巷，加門洞與可擊破玻璃窗）
        // NW 機房 x=22..35, z=9..15 ＋ 內部隔牆
        carveRoom(g, 22, 1, 9, 35, 4, 15, Cell.FLOOR_METAL);
        fill(g, 25, 1, 12, 32, 3, 12, Cell.WALL_INNER);
        carveDoor(g, 21, 11, 21, 13, 3, Cell.FLOOR_METAL); // 接西外交叉道
        carveDoor(g, 27, 16, 29, 16, 3, Cell.FLOOR_METAL); // 接內北走道
        carveDoor(g, 36, 10, 36, 12, 3, Cell.FLOOR_METAL); // 接 A 點
        // NE 機房 x=49..62, z=9..15
        carveRoom(g, 49, 1, 9, 62, 4, 15, Cell.FLOOR_METAL);
        fill(g, 52, 1, 12, 59, 3, 12, Cell.WALL_INNER);
        carveDoor(g, 63, 11, 63, 13, 3, Cell.FLOOR_METAL);
        carveDoor(g, 55, 16, 57, 16, 3, Cell.FLOOR_METAL);
        carveDoor(g, 48, 10, 48, 12, 3, Cell.FLOOR_METAL);
        // SW 貨倉 x=22..35, z=49..55
        carveRoom(g, 22, 1, 49, 35, 4, 55, Cell.FLOOR_METAL);
        fill(g, 25, 1, 52, 32, 3, 52, Cell.WALL_INNER);
        carveDoor(g, 21, 51, 21, 53, 3, Cell.FLOOR_METAL);
        carveDoor(g, 27, 48, 29, 48, 3, Cell.FLOOR_METAL);
        carveDoor(g, 36, 52, 36, 54, 3, Cell.FLOOR_METAL);
        // SE 貨倉 x=49..62, z=49..55
        carveRoom(g, 49, 1, 49, 62, 4, 55, Cell.FLOOR_METAL);
        fill(g, 52, 1, 52, 59, 3, 52, Cell.WALL_INNER);
        carveDoor(g, 63, 51, 63, 53, 3, Cell.FLOOR_METAL);
        carveDoor(g, 55, 48, 57, 48, 3, Cell.FLOOR_METAL);
        carveDoor(g, 48, 52, 48, 54, 3, Cell.FLOOR_METAL);

        // 四個中翼側室：NW 軍械庫、SW 醫務室、NE 實驗室、SE 監控室（接交叉道與內側翼，並開玻璃窗看中路）
        carveMidRoom(g, 22, 22, 27, 28, true, true);
        carveMidRoom(g, 22, 36, 27, 42, true, false);
        carveMidRoom(g, 57, 22, 62, 28, false, true);
        carveMidRoom(g, 57, 36, 62, 42, false, false);

        // 5. 三個佔點據點大廳（A 北側機房、B 中央反應爐挑高大廳、C 南側貨運裝卸間）
        // Point A: x=37..47, z=7..15（地板 x=39..45, z=8..14）
        carveRoom(g, 37, 1, 7, 47, 5, 15, Cell.FLOOR_METAL);
        fill(g, 39, 0, 8, 45, 0, 14, Cell.POINT_FLOOR_A);
        carveDoor(g, 41, 6, 43, 6, 3, Cell.FLOOR_CORRIDOR);
        carveDoor(g, 41, 16, 43, 16, 3, Cell.FLOOR_CORRIDOR);
        // 挑高讓 2F 北天橋視野清晰俯視 A 點
        fill(g, 39, 6, 9, 45, 11, 15, Cell.AIR);
        fill(g, 39, 6, 16, 45, 6, 16, Cell.FLOOR_UPPER);
        fill(g, 39, 7, 16, 45, 7, 16, Cell.COVER_HALF);
        fill(g, 39, 8, 16, 45, 9, 16, Cell.GLASS_PANE);

        // Point B: x=37..47, z=27..37（地板 x=38..46, z=28..36，挑高至 y=11）
        carveRoom(g, 37, 1, 27, 47, 5, 37, Cell.FLOOR_METAL);
        fill(g, 38, 0, 28, 46, 0, 36, Cell.POINT_FLOOR_B);
        carveDoor(g, 36, 31, 36, 33, 3, Cell.FLOOR_CORRIDOR);
        carveDoor(g, 48, 31, 48, 33, 3, Cell.FLOOR_CORRIDOR);
        carveDoor(g, 41, 26, 43, 26, 3, Cell.FLOOR_CORRIDOR);
        carveDoor(g, 41, 38, 43, 38, 3, Cell.FLOOR_CORRIDOR);
        // 挑高反應爐核心（中心柱 x=41..43, z=31..33 貫通 1F 與 2F）
        fill(g, 38, 6, 28, 46, 11, 36, Cell.AIR);
        buildReactorCore(g);

        // Point C: x=37..47, z=49..57（地板 x=39..45, z=50..56）
        carveRoom(g, 37, 1, 49, 47, 5, 57, Cell.FLOOR_METAL);
        fill(g, 39, 0, 50, 45, 0, 56, Cell.POINT_FLOOR_C);
        carveDoor(g, 41, 48, 43, 48, 3, Cell.FLOOR_CORRIDOR);
        carveDoor(g, 41, 58, 43, 58, 3, Cell.FLOOR_CORRIDOR);
        // 挑高讓 2F 南天橋視野俯視 C 點
        fill(g, 39, 6, 49, 45, 11, 55, Cell.AIR);
        fill(g, 39, 6, 48, 45, 6, 48, Cell.FLOOR_UPPER);
        fill(g, 39, 7, 48, 45, 7, 48, Cell.COVER_HALF);
        fill(g, 39, 8, 48, 45, 9, 48, Cell.GLASS_PANE);

        // 6. 四座 3 格寬樓梯（1F y=1 <-> 2F y=7）
        carveWestStair(g, 9);
        carveWestStair(g, 53);
        carveEastStair(g, 9);
        carveEastStair(g, 53);

        // 7. 2F 上層戰術天橋（所有走道均為 3 格寬！）
        carveUpper(g, 16, 9, 18, 55);   // 2F 西縱走道（接 NW / SW 樓梯）
        carveUpper(g, 66, 9, 68, 55);   // 2F 東縱走道（接 NE / SE 樓梯）
        carveUpper(g, 16, 17, 68, 19);  // 2F 北側連通天橋（含 A 點俯瞰窗）
        carveUpper(g, 16, 45, 68, 47);  // 2F 南側連通天橋（含 C 點俯瞰窗）
        carveUpper(g, 29, 17, 31, 47);  // 2F 西側中橋
        carveUpper(g, 53, 17, 55, 47);  // 2F 東側中橋
        carveUpper(g, 16, 31, 31, 33);  // 2F 西連接廊
        carveUpper(g, 53, 31, 68, 33);  // 2F 東連接廊
        carveUpper(g, 31, 24, 53, 26);  // 2F 北反應爐天橋
        carveUpper(g, 31, 38, 53, 40);  // 2F 南反應爐天橋
        // 2F 反應爐天橋對準 B 點的鐵欄護欄與玻璃窗
        fill(g, 38, 6, 27, 46, 6, 27, Cell.FLOOR_UPPER);
        fill(g, 38, 7, 27, 46, 7, 27, Cell.IRON_BARS);
        fill(g, 41, 7, 27, 43, 8, 27, Cell.GLASS_PANE);
        fill(g, 38, 6, 37, 46, 6, 37, Cell.FLOOR_UPPER);
        fill(g, 38, 7, 37, 46, 7, 37, Cell.IRON_BARS);
        fill(g, 41, 7, 37, 43, 8, 37, Cell.GLASS_PANE);

        // 8. 照明配置（嵌頂海燈籠＋暖色蛙光，保證全室內明亮不生怪且不擋頭頂）
        placeLighting(g);

        // 9. 大量交錯掩體（全部保留至少 2 格通行寬度，零木箱／木桶）
        placeDenseCover(g);

        // 10. 設定全部重生點、出口屏障與佔點定義，並保護出生點周圍 1 格清空無掩體遮擋
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

        return new Built(g, redSpawns, blueSpawns, barriers, domPoints, gkSpawns);
    }

    // ── 幾何雕刻輔助 ────────────────────────────────────────────────────────

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

    private static void carveMidRoom(Cell[][][] g, int x0, int z0, int x1, int z1, boolean westSide, boolean northHalf) {
        carveRoom(g, x0, 1, z0, x1, 4, z1, Cell.FLOOR_METAL);
        // 東西門口各接外交叉道與內側翼
        int outerDoorX = westSide ? x0 - 1 : x1 + 1;
        int innerDoorX = westSide ? x1 + 1 : x0 - 1;
        int doorZ = northHalf ? z0 + 2 : z1 - 4;
        carveDoor(g, outerDoorX, doorZ, outerDoorX, doorZ + 2, 3, Cell.FLOOR_METAL);
        carveDoor(g, innerDoorX, doorZ, innerDoorX, doorZ + 2, 3, Cell.FLOOR_METAL);

        // 面向中路走道（z=31..33）的觀察窗（1 格矮牆 + 2 格可擊破玻璃）
        int winZ = northHalf ? 29 : 35;
        int airZ = northHalf ? 30 : 34;
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            g[x][1][winZ] = Cell.WALL_INNER;
            g[x][2][winZ] = Cell.GLASS_PANE;
            g[x][3][winZ] = Cell.GLASS_PANE;
            g[x][1][airZ] = Cell.WALL_INNER;
            g[x][2][airZ] = Cell.AIR;
            g[x][3][airZ] = Cell.AIR;
        }
    }

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

    private static void carveWestStair(Cell[][][] g, int zStart) {
        int zEnd = zStart + 2;
        // 1F 西走廊進梯間門口 x=9
        carveDoor(g, 9, zStart, 9, zEnd, 4, Cell.FLOOR_CORRIDOR);
        for (int k = 0; k < 6; k++) {
            int x = 10 + k;
            int stepY = 1 + k;
            for (int z = zStart; z <= zEnd; z++) {
                g[x][0][z] = Cell.FLOOR_CORRIDOR;
                for (int y = 1; y < stepY; y++) g[x][y][z] = Cell.WALL_INNER;
                g[x][stepY][z] = Cell.STAIR_E;
                for (int y = stepY + 1; y <= 11; y++) g[x][y][z] = Cell.AIR;
            }
        }
    }

    private static void carveEastStair(Cell[][][] g, int zStart) {
        int zEnd = zStart + 2;
        carveDoor(g, 75, zStart, 75, zEnd, 4, Cell.FLOOR_CORRIDOR);
        for (int k = 0; k < 6; k++) {
            int x = 74 - k;
            int stepY = 1 + k;
            for (int z = zStart; z <= zEnd; z++) {
                g[x][0][z] = Cell.FLOOR_CORRIDOR;
                for (int y = 1; y < stepY; y++) g[x][y][z] = Cell.WALL_INNER;
                g[x][stepY][z] = Cell.STAIR_W;
                for (int y = stepY + 1; y <= 11; y++) g[x][y][z] = Cell.AIR;
            }
        }
    }

    private static void carveUpper(Cell[][][] g, int x0, int z0, int x1, int z1) {
        fill(g, x0, 6, z0, x1, 6, z1, Cell.FLOOR_UPPER);
        fill(g, x0, 7, z0, x1, 11, z1, Cell.AIR);
    }

    private static void placeLighting(Cell[][][] g) {
        // 1F 天花板嵌燈：放在 y=5（僅在上方不影響 2F 踩踏或走廊處）或於 y=6 貼平天花板兼作 2F 發光地磚條
        for (int x = 3; x <= WIDTH - 4; x += 4) {
            for (int z = 4; z <= DEPTH - 5; z += 4) {
                if (g[x][5][z] == Cell.AIR && g[x][6][z] == Cell.CEILING) {
                    g[x][6][z] = ((x + z) % 8 == 0) ? Cell.LIGHT : Cell.LIGHT_WARM;
                }
            }
        }
        // 2F 與挑高大廳頂棚 y=12 嵌燈
        for (int x = 11; x <= WIDTH - 12; x += 4) {
            for (int z = 5; z <= DEPTH - 6; z += 4) {
                if (g[x][11][z] == Cell.AIR && g[x][12][z] == Cell.CEILING) {
                    g[x][12][z] = ((x + z) % 8 == 0) ? Cell.LIGHT : Cell.LIGHT_WARM;
                }
            }
        }
        // 紅藍基地主燈
        for (int z : new int[] { 28, 32, 36 }) {
            g[5][5][z] = Cell.LIGHT_WARM;
            g[79][5][z] = Cell.LIGHT_WARM;
        }
    }

    private static void placeDenseCover(Cell[][][] g) {
        // ── 1F 走廊掩體：交錯靠左／靠右放置，每處都保證至少 2 格以上空間繞過 ──
        // 外北長廊 (z=3..6, 寬4)
        for (int x = 10; x <= 74; x += 6) {
            int z = ((x / 6) % 2 == 0) ? 3 : 6;
            placeCover(g, x, 1, z, (x % 12 == 4) ? Cell.COVER_FULL : Cell.COVER_HALF, true);
        }
        // 外南管線長廊 (z=58..61, 寬4)
        for (int x = 10; x <= 74; x += 6) {
            int z = ((x / 6) % 2 == 0) ? 61 : 58;
            placeCover(g, x, 1, z, (x % 12 == 4) ? Cell.COVER_PIPE : Cell.COVER_SANDBAG, true);
        }
        // 西／東南北縱向幹道 (x=5..8 & x=76..79)
        for (int z : new int[] { 7, 14, 19, 44, 50, 56 }) {
            int xOff = (z % 2 == 0) ? 5 : 8;
            placeCover(g, xOff, 1, z, Cell.COVER_SANDBAG, false);
            placeCover(g, WIDTH - 1 - xOff, 1, z, Cell.COVER_SANDBAG, false);
        }
        // 內北整備道 (z=17..19, 寬3) & 內南維護道 (z=45..47, 寬3)：避開門口處，交替 1 格寬掩體保留 2 格通道
        for (int x = 22; x <= 62; x += 6) {
            if (Math.abs(x - 28) <= 2 || Math.abs(x - 42) <= 2 || Math.abs(x - 56) <= 2) continue;
            placeCover(g, x, 1, (x % 12 == 4) ? 17 : 19, Cell.COVER_SERVER, true);
            placeCover(g, x, 1, (x % 12 == 4) ? 47 : 45, Cell.COVER_CRATE, true);
        }
        // 西／東中路突穿走廊 (z=31..33, 寬3)
        for (int x : new int[] { 23, 27, 34, 50, 57, 61 }) {
            int z = (x % 2 == 1) ? 31 : 33;
            placeCover(g, x, 1, z, (x == 34 || x == 50) ? Cell.COVER_FULL : Cell.COVER_SANDBAG, false);
        }
        // 西／東外交叉道 (x=17..20 & x=64..67, 寬4)
        for (int z : new int[] { 10, 15, 22, 28, 36, 42, 49, 55 }) {
            boolean left = (z % 2 == 0);
            int wx = left ? 17 : 20;
            int ex = left ? 67 : 64;
            Cell type = (z == 28 || z == 36) ? Cell.COVER_FULL : Cell.COVER_CRATE;
            placeCover(g, wx, 1, z, type, false);
            placeCover(g, ex, 1, z, type, false);
        }
        // 內側翼通道 (x=29..31 & x=53..55, 寬3)
        for (int z : new int[] { 21, 27, 37, 43 }) {
            placeCover(g, (z < 30) ? 29 : 31, 1, z, Cell.COVER_HALF, false);
            placeCover(g, (z < 30) ? 55 : 53, 1, z, Cell.COVER_HALF, false);
        }
        // 中央北／南連結通道 (x=41..43, z=20..26 & z=38..44)
        placeCover(g, 41, 1, 22, Cell.COVER_SANDBAG, false);
        placeCover(g, 43, 1, 24, Cell.COVER_HALF, false);
        placeCover(g, 41, 1, 40, Cell.COVER_HALF, false);
        placeCover(g, 43, 1, 42, Cell.COVER_SANDBAG, false);

        // 紅藍基地內側防線掩體
        for (int z : new int[] { 26, 32, 38 }) {
            placeCover(g, 11, 1, z, Cell.COVER_SANDBAG, false);
            placeCover(g, 73, 1, z, Cell.COVER_SANDBAG, false);
        }

        // 機房／貨倉／中翼側室內掩體
        for (int[] pos : new int[][] {
            { 24, 10 }, { 33, 10 }, { 24, 14 }, { 33, 14 },
            { 51, 10 }, { 60, 10 }, { 51, 14 }, { 60, 14 }
        }) {
            placeCover(g, pos[0], 1, pos[1], Cell.COVER_SERVER, true);
        }
        for (int[] pos : new int[][] {
            { 24, 50 }, { 33, 50 }, { 24, 54 }, { 33, 54 },
            { 51, 50 }, { 60, 50 }, { 51, 54 }, { 60, 54 }
        }) {
            placeCover(g, pos[0], 1, pos[1], Cell.COVER_CRATE, true);
        }
        // 四中翼側室掩體
        for (int[] pos : new int[][] {
            { 24, 23 }, { 26, 26 }, { 24, 41 }, { 26, 38 },
            { 60, 23 }, { 58, 26 }, { 60, 41 }, { 58, 38 }
        }) {
            placeCover(g, pos[0], 1, pos[1], Cell.COVER_HALF, false);
        }

        // 三個佔點室內部掩體
        // Point A：4 座機櫃＋中央矮控制台
        for (int[] pos : new int[][] { { 40, 9 }, { 44, 9 }, { 40, 13 }, { 44, 13 } }) {
            placeCover(g, pos[0], 1, pos[1], Cell.COVER_SERVER, true);
        }
        placeCover(g, 42, 1, 11, Cell.COVER_HALF, false);
        // Point B：反應爐四周角落矮沙包
        for (int[] pos : new int[][] { { 39, 29 }, { 45, 29 }, { 39, 35 }, { 45, 35 } }) {
            placeCover(g, pos[0], 1, pos[1], Cell.COVER_SANDBAG, false);
        }
        // Point C：4 座貨櫃堆＋中央矮貨架
        for (int[] pos : new int[][] { { 40, 51 }, { 44, 51 }, { 40, 55 }, { 44, 55 } }) {
            placeCover(g, pos[0], 1, pos[1], Cell.COVER_CRATE, true);
        }
        placeCover(g, 42, 1, 53, Cell.COVER_HALF, false);

        // ── 2F 天橋掩體（全為 3 格寬走道，靠邊放置半高與全高掩體保留 2 格順暢繞行空間）──
        for (int z : new int[] { 15, 24, 30, 34, 40, 49 }) {
            int wx = (z % 2 == 0) ? 16 : 18;
            int ex = (z % 2 == 0) ? 68 : 66;
            placeCover(g, wx, 7, z, (z == 24 || z == 40) ? Cell.COVER_FULL : Cell.COVER_HALF, z == 24 || z == 40);
            placeCover(g, ex, 7, z, (z == 24 || z == 40) ? Cell.COVER_FULL : Cell.COVER_HALF, z == 24 || z == 40);
        }
        for (int x : new int[] { 23, 35, 42, 49, 61 }) {
            placeCover(g, x, 7, (x % 2 == 0) ? 19 : 17, (x == 42) ? Cell.COVER_SANDBAG : Cell.COVER_HALF, false);
            placeCover(g, x, 7, (x % 2 == 0) ? 45 : 47, (x == 42) ? Cell.COVER_SANDBAG : Cell.COVER_HALF, false);
        }
        for (int z : new int[] { 22, 28, 36, 42 }) {
            placeCover(g, (z < 32) ? 29 : 31, 7, z, Cell.COVER_HALF, false);
            placeCover(g, (z < 32) ? 55 : 53, 7, z, Cell.COVER_HALF, false);
        }
        for (int x : new int[] { 36, 42, 48 }) {
            placeCover(g, x, 7, (x == 42) ? 24 : 25, Cell.COVER_SANDBAG, false);
            placeCover(g, x, 7, (x == 42) ? 40 : 39, Cell.COVER_SANDBAG, false);
        }
        // 2F 東西連接廊
        placeCover(g, 22, 7, 31, Cell.COVER_HALF, false);
        placeCover(g, 26, 7, 33, Cell.COVER_SANDBAG, false);
        placeCover(g, 62, 7, 31, Cell.COVER_HALF, false);
        placeCover(g, 58, 7, 33, Cell.COVER_SANDBAG, false);
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
            // 1F 外北與外南長廊（8 點）
            { 7, 1, 5 }, { 24, 1, 5 }, { 42, 1, 4 }, { 60, 1, 5 }, { 77, 1, 5 },
            { 7, 1, 59 }, { 24, 1, 59 }, { 42, 1, 60 }, { 60, 1, 59 }, { 77, 1, 59 },
            // 1F 紅藍基地內側與外圍幹道（8 點）
            { 4, 1, 29 }, { 4, 1, 35 }, { 80, 1, 29 }, { 80, 1, 35 },
            { 6, 1, 16 }, { 6, 1, 48 }, { 78, 1, 16 }, { 78, 1, 48 },
            // 1F 西／東交叉道與機房／貨倉（10 點）
            { 18, 1, 12 }, { 18, 1, 32 }, { 18, 1, 52 },
            { 66, 1, 12 }, { 66, 1, 32 }, { 66, 1, 52 },
            { 28, 1, 10 }, { 56, 1, 10 }, { 28, 1, 54 }, { 56, 1, 54 },
            // 1F 中路與據點周邊（6 點）
            { 30, 1, 24 }, { 30, 1, 40 }, { 54, 1, 24 }, { 54, 1, 40 },
            { 38, 1, 11 }, { 46, 1, 53 },
            // 2F 上層戰術天橋（10 點，總計 44 點）
            { 17, 7, 12 }, { 17, 7, 32 }, { 17, 7, 52 },
            { 67, 7, 12 }, { 67, 7, 32 }, { 67, 7, 52 },
            { 30, 7, 18 }, { 54, 7, 18 }, { 30, 7, 46 }, { 54, 7, 46 }
        };
        List<LocalSpawn> out = new ArrayList<>(coords.length);
        for (int[] c : coords) {
            out.add(new LocalSpawn(c[0] + 0.5, c[1], c[2] + 0.5));
        }
        return out;
    }
}
