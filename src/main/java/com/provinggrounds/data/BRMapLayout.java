package com.provinggrounds.data;

import com.provinggrounds.admin.build.BuildingBlueprint;
import com.provinggrounds.admin.build.BuildingCatalog;
import com.provinggrounds.data.BRMapPresets.AltarHint;
import com.provinggrounds.data.BRMapPresets.DistrictDef;
import com.provinggrounds.data.BRMapPresets.Preset;
import com.provinggrounds.data.BRMapPresets.RegionBiome;
import com.provinggrounds.data.BRMapPresets.Theme;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 檔案: BRMapLayout.java
 * 目的: 計算大逃殺程序化地圖的確定性建築群佈局、道路網、復活祭壇位置、野外掩體點位，
 *       並提供零依賴的地形高程／水面／道路查詢函式。
 */
public final class BRMapLayout {

    private static final int BUILDING_GAP = 6;
    private static final int CIRCLE_MARGIN = 16;

    public record PlacedBuilding(
        BuildingBlueprint bp,
        String districtName,
        RegionBiome biome,
        int originX,
        int y,
        int originZ,
        Direction facing,
        int minX,
        int maxX,
        int minZ,
        int maxZ
    ) {
        public boolean intersectsWithGap(int oMinX, int oMaxX, int oMinZ, int oMaxZ, int gap) {
            return !(maxX + gap < oMinX || oMaxX + gap < minX || maxZ + gap < oMinZ || oMaxZ + gap < minZ);
        }

        public int centerX() { return (minX + maxX) / 2; }
        public int centerZ() { return (minZ + maxZ) / 2; }
    }

    public record DistrictBox(
        String name,
        RegionBiome biome,
        int centerX,
        int centerZ,
        int y,
        int minX,
        int maxX,
        int minZ,
        int maxZ
    ) {}

    public record RoadSeg(int x0, int z0, int x1, int z1, int halfWidth) {}

    public record CoverSpot(int x, int z, RegionBiome biome, int kind) {}

    public static final class Plan {
        public final Preset preset;
        public final int centerX;
        public final int centerZ;
        public final List<PlacedBuilding> buildings;
        public final List<DistrictBox> districts;
        public final List<RoadSeg> roads;
        public final List<BlockPos> altars;
        public final List<CoverSpot> covers;

        Plan(Preset preset, int centerX, int centerZ, List<PlacedBuilding> buildings,
             List<DistrictBox> districts, List<RoadSeg> roads, List<BlockPos> altars, List<CoverSpot> covers) {
            this.preset = preset;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.buildings = List.copyOf(buildings);
            this.districts = List.copyOf(districts);
            this.roads = List.copyOf(roads);
            this.altars = List.copyOf(altars);
            this.covers = List.copyOf(covers);
        }

        /** 計算指定世界座標的自然高程（平滑揉合城區平台、祭壇廣場、河道與邊界海灣／峭壁）。 */
        public int surfaceY(int wx, int wz) {
            double relX = wx - (double) centerX;
            double relZ = wz - (double) centerZ;
            double base = naturalHeight(relX, relZ);

            double distWeightSum = 0.0;
            double distWeightedY = 0.0;
            double maxDistrictBlend = 0.0;
            for (DistrictBox d : districts) {
                double dx = Math.max(0.0, Math.max(d.minX() - wx, wx - d.maxX()));
                double dz = Math.max(0.0, Math.max(d.minZ() - wz, wz - d.maxZ()));
                double dist = Math.hypot(dx, dz);
                if (dist <= 0.001) {
                    return d.y();
                }
                if (dist < 22.0) {
                    double t = 1.0 - dist / 22.0;
                    double blend = t * t * (3.0 - 2.0 * t);
                    distWeightSum += blend;
                    distWeightedY += blend * d.y();
                    if (blend > maxDistrictBlend) maxDistrictBlend = blend;
                }
            }
            if (maxDistrictBlend > 0.0) {
                double plateau = distWeightedY / distWeightSum;
                base = base * (1.0 - maxDistrictBlend) + plateau * maxDistrictBlend;
            }

            // 祭壇廣場周圍平滑（半徑 4 格全平、向外 10 格柔順過渡）
            for (BlockPos a : altars) {
                double d = Math.hypot(wx - (a.getX() + 0.5), wz - (a.getZ() + 0.5));
                if (d <= 4.5) return a.getY() - 1;
                if (d < 14.5) {
                    double t = 1.0 - (d - 4.5) / 10.0;
                    double w = t * t * (3.0 - 2.0 * t);
                    base = base * (1.0 - w) + (a.getY() - 1) * w;
                }
            }
            return Math.max(40, Math.min(110, (int) Math.round(base)));
        }

        private double naturalHeight(double relX, double relZ) {
            long s = preset.seed();
            double n1 = BRMapPresets.noise2D(relX * 0.0085, relZ * 0.0085, s) - 0.5;
            double n2 = BRMapPresets.noise2D(relX * 0.024 + 50.0, relZ * 0.024 - 30.0, s ^ 0x777L) - 0.5;
            double n3 = BRMapPresets.noise2D(relX * 0.065 - 15.0, relZ * 0.065 + 42.0, s ^ 0xABCDEFL) - 0.5;
            double hill = 70.0 + n1 * 12.0 + n2 * 5.0 + n3 * 1.8;

            double r = Math.hypot(relX, relZ);
            double edgeR = preset.radius() - 10.0;
            Theme th = preset.theme();

            if (th == Theme.HARBOR) {
                // 南側港灣海域（y < 62 形成海岸水域）
                if (relZ > 75.0) {
                    double bay = Math.min(1.0, (relZ - 75.0) / 120.0);
                    hill -= bay * 10.5;
                }
            } else if (th == Theme.DESERT) {
                // 東南–西北乾涸沙丘與中央微凹綠洲水脈
                double dune = Math.sin((relX + relZ) * 0.055) * 1.6;
                hill += dune + 1.5;
                if (Math.hypot(relX - 95.0, relZ + 95.0) < 38.0) {
                    hill -= 9.0; // 東北綠洲蓄水湖
                }
            } else if (th == Theme.ARCTIC) {
                // 北側高雪山脊 + 西南結冰海灣
                if (relZ < -120.0) hill += Math.min(1.0, (-relZ - 120.0) / 250.0) * 6.5;
                if (Math.hypot(relX - 180.0, relZ - 95.0) < 55.0) hill -= 9.5;
            } else if (th == Theme.IMPERIAL) {
                // 蜿蜒河谷穿過中東交界
                double riverX = 165.0 + Math.sin(relZ * 0.012) * 55.0;
                double dRiv = Math.abs(relX - riverX);
                if (dRiv < 24.0) {
                    double t = 1.0 - dRiv / 24.0;
                    hill -= t * t * 12.0;
                }
            } else if (th == Theme.CONTINENT) {
                // 十字寬河道劃分各大陸板塊 + 最南側遠洋海水
                double rx = Math.abs(relX - Math.sin(relZ * 0.008) * 65.0);
                double rz = Math.abs(relZ - Math.cos(relX * 0.008) * 65.0);
                double dCanal = Math.min(rx, rz);
                if (dCanal < 26.0 && r > 125.0) {
                    double t = 1.0 - dCanal / 26.0;
                    hill -= t * t * 12.5;
                }
                if (relZ > 365.0) {
                    hill -= Math.min(1.0, (relZ - 365.0) / 180.0) * 10.0;
                }
            }

            if (r > edgeR) {
                double k = Math.min(1.0, (r - edgeR) / 24.0);
                if (th == Theme.HARBOR || th == Theme.CONTINENT) {
                    hill -= k * 14.0; // 外圈海水圍島
                } else {
                    hill += k * 14.0; // 外圈自然環山圍牆
                }
            }
            return hill;
        }

        /** 是否位於主幹道或外環道路寬度內。 */
        public boolean isRoad(int wx, int wz) {
            return roadDistance(wx, wz) <= 0.0;
        }

        /** 回傳點距離最近道路外緣的負數（<= 0 代表在路上，> 0 代表距離路邊的格數）。 */
        public double roadDistance(int wx, int wz) {
            double best = Double.MAX_VALUE;
            for (RoadSeg seg : roads) {
                double d = pointToSegmentDist(wx + 0.5, wz + 0.5, seg.x0() + 0.5, seg.z0() + 0.5, seg.x1() + 0.5, seg.z1() + 0.5) - seg.halfWidth();
                if (d < best) best = d;
            }
            return best;
        }

        public RegionBiome biomeAtWorld(int wx, int wz) {
            return BRMapPresets.biomeAt(preset, wx - centerX, wz - centerZ);
        }
    }

    private BRMapLayout() {}

    /** 依預設藍圖計算所有建築、道路、祭壇與野外掩體的確定性平面規劃。 */
    public static Plan buildPlan(Preset preset, int centerX, int centerZ) {
        List<PlacedBuilding> placed = new ArrayList<>();
        List<DistrictBox> districtBoxes = new ArrayList<>();

        for (DistrictDef d : preset.districts()) {
            int dcx = centerX + d.relX();
            int dcz = centerZ + d.relZ();
            List<PlacedBuilding> distPlaced = new ArrayList<>();

            // 先把較大型建築排在內環，小型建築嵌在外側縫隙，緊密湊成街廓
            List<String> ordered = new ArrayList<>(d.buildingIds());
            ordered.sort(Comparator.comparingInt((String id) -> {
                BuildingBlueprint bp = BuildingCatalog.byId(id);
                return bp == null ? 0 : bp.width() * bp.depth();
            }).reversed());

            for (int idx = 0; idx < ordered.size(); idx++) {
                String bid = ordered.get(idx);
                BuildingBlueprint bp = BuildingCatalog.byId(bid);
                if (bp == null) continue;
                PlacedBuilding chosen = findTightSlot(preset, centerX, centerZ, dcx, dcz, d, bp, idx, placed);
                if (chosen != null) {
                    placed.add(chosen);
                    distPlaced.add(chosen);
                }
            }

            if (!distPlaced.isEmpty()) {
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (PlacedBuilding b : distPlaced) {
                    minX = Math.min(minX, b.minX());
                    maxX = Math.max(maxX, b.maxX());
                    minZ = Math.min(minZ, b.minZ());
                    maxZ = Math.max(maxZ, b.maxZ());
                }
                districtBoxes.add(new DistrictBox(
                    d.name(), d.biome(), dcx, dcz, d.elevation(),
                    minX - 6, maxX + 6, minZ - 6, maxZ + 6
                ));
            }
        }

        // 建立放射主幹道＋外環環狀公路
        List<RoadSeg> roads = buildRoads(centerX, centerZ, preset.districts());

        // 避開建築佔地放置所有復活祭壇，並計算貼合地表的高度
        Plan tempPlan = new Plan(preset, centerX, centerZ, placed, districtBoxes, roads, List.of(), List.of());
        List<BlockPos> altars = placeAltars(preset, centerX, centerZ, placed, tempPlan);

        // 散布野外掩體（避開建築、道路與祭壇廣場）
        Plan withAltars = new Plan(preset, centerX, centerZ, placed, districtBoxes, roads, altars, List.of());
        List<CoverSpot> covers = placeOutdoorCovers(preset, centerX, centerZ, placed, withAltars);

        return new Plan(preset, centerX, centerZ, placed, districtBoxes, roads, altars, covers);
    }

    @Nullable
    private static PlacedBuilding findTightSlot(Preset preset, int mapCx, int mapCz, int dcx, int dcz,
                                                DistrictDef d, BuildingBlueprint bp, int idx, List<PlacedBuilding> allPlaced) {
        double maxCornerR = preset.radius() - CIRCLE_MARGIN;
        Direction[] dirs = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);

        // 在區域中心周圍 ±145 格內以 4 格步進由近而遠掃描，尋找第一個不重疊且位在邊界內的緊密槽位
        PlacedBuilding best = null;
        double bestScore = Double.MAX_VALUE;
        for (int shell = 6; shell <= 148; shell += 4) {
            for (int offX = -shell; offX <= shell; offX += 4) {
                for (int offZ = -shell; offZ <= shell; offZ += 4) {
                    if (Math.max(Math.abs(offX), Math.abs(offZ)) != shell) continue;
                    int ox = dcx + offX;
                    int oz = dcz + offZ;
                    // 朝向預設指向區域中心街道，轉彎時也允許旋轉尋找更緊密縫隙
                    Direction primary = inwardFacing(dcx - ox, dcz - oz, dirs[(idx) % 4]);
                    Direction[] tryDirs = { primary, primary.getClockWise(), primary.getCounterClockWise(), primary.getOpposite() };
                    for (Direction f : tryDirs) {
                        int[] box = footprint(bp, ox, oz, f);
                        if (!allCornersWithin(box, mapCx, mapCz, maxCornerR)) continue;
                        if (overlapsAny(box, allPlaced, BUILDING_GAP)) continue;
                        double cx = 0.5 * (box[0] + box[1]);
                        double cz = 0.5 * (box[2] + box[3]);
                        // 與區域中心廣場保持適度退縮，中間留出約 10x10 的街口交匯處
                        double distFromCenter = Math.hypot(cx - dcx, cz - dcz);
                        if (distFromCenter < 12.0) continue;
                        double score = distFromCenter + (f == primary ? 0.0 : 3.5);
                        if (score < bestScore) {
                            bestScore = score;
                            best = new PlacedBuilding(bp, d.name(), d.biome(), ox, d.elevation(), oz, f, box[0], box[1], box[2], box[3]);
                        }
                        break; // primary 沒問題就不用試同座標其他旋轉
                    }
                }
            }
            if (best != null && shell >= bestScore * 0.75) break;
        }
        return best;
    }

    private static Direction inwardFacing(int dx, int dz, Direction fallback) {
        if (Math.abs(dx) < 3 && Math.abs(dz) < 3) return fallback;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** 精確對應 BuildingCanvas.toWorld 的佔地邊界 {minX, maxX, minZ, maxZ}。 */
    public static int[] footprint(BuildingBlueprint bp, int originX, int originZ, Direction facing) {
        Direction right = facing.getClockWise();
        int halfW = (bp.width() + 1) / 2 + 1;
        int minLx = -halfW;
        int maxLx = halfW;
        int minLz = -1;
        int maxLz = bp.depth();

        int[] xs = { minLx, maxLx };
        int[] zs = { minLz, maxLz };
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int lx : xs) {
            for (int lz : zs) {
                int wx = originX + right.getStepX() * lx + facing.getStepX() * lz;
                int wz = originZ + right.getStepZ() * lx + facing.getStepZ() * lz;
                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wz < minZ) minZ = wz;
                if (wz > maxZ) maxZ = wz;
            }
        }
        return new int[] { minX, maxX, minZ, maxZ };
    }

    private static boolean allCornersWithin(int[] box, int cx, int cz, double maxR) {
        double maxSq = maxR * maxR;
        int[] bx = { box[0], box[1] };
        int[] bz = { box[2], box[3] };
        for (int x : bx) {
            for (int z : bz) {
                double dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz > maxSq) return false;
            }
        }
        return true;
    }

    private static boolean overlapsAny(int[] box, List<PlacedBuilding> list, int gap) {
        for (PlacedBuilding b : list) {
            if (b.intersectsWithGap(box[0], box[1], box[2], box[3], gap)) return true;
        }
        return false;
    }

    private static List<RoadSeg> buildRoads(int cx, int cz, List<DistrictDef> districts) {
        List<RoadSeg> segs = new ArrayList<>();
        if (districts.isEmpty()) return segs;
        DistrictDef hub = districts.get(0);
        int hx = cx + hub.relX();
        int hz = cz + hub.relZ();

        List<DistrictDef> outer = new ArrayList<>();
        for (int i = 1; i < districts.size(); i++) {
            DistrictDef d = districts.get(i);
            segs.add(new RoadSeg(hx, hz, cx + d.relX(), cz + d.relZ(), 3));
            outer.add(d);
        }
        if (outer.size() >= 2) {
            outer.sort(Comparator.comparingDouble(d -> Math.atan2(d.relZ(), d.relX())));
            for (int i = 0; i < outer.size(); i++) {
                DistrictDef a = outer.get(i);
                DistrictDef b = outer.get((i + 1) % outer.size());
                segs.add(new RoadSeg(cx + a.relX(), cz + a.relZ(), cx + b.relX(), cz + b.relZ(), 2));
            }
        }
        return segs;
    }

    private static List<BlockPos> placeAltars(Preset preset, int mapCx, int mapCz, List<PlacedBuilding> placed, Plan terrain) {
        List<BlockPos> result = new ArrayList<>();
        double maxR = preset.radius() - 22.0;
        for (AltarHint hint : preset.altars()) {
            int baseAx = mapCx + hint.relX();
            int baseAz = mapCz + hint.relZ();
            int[] found = findClearPoint(baseAx, baseAz, mapCx, mapCz, maxR, 8, placed, result, 36);
            int ax = found[0];
            int az = found[1];
            int groundY = Math.max(preset.waterLevel() + 1, terrain.surfaceY(ax, az));
            result.add(new BlockPos(ax, groundY + 1, az));
        }
        return result;
    }

    private static int[] findClearPoint(int startX, int startZ, int mapCx, int mapCz, double maxR, int buildingGap,
                                        List<PlacedBuilding> placed, List<BlockPos> existingAltars, int minAltarSep) {
        double maxSq = maxR * maxR;
        double minAltarSq = minAltarSep * (double) minAltarSep;
        for (int r = 0; r <= 120; r += 6) {
            for (int dx = -r; dx <= r; dx += 6) {
                for (int dz = -r; dz <= r; dz += 6) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = startX + dx;
                    int z = startZ + dz;
                    double rx = x - mapCx, rz = z - mapCz;
                    if (rx * rx + rz * rz > maxSq) continue;
                    boolean hitBld = false;
                    for (PlacedBuilding b : placed) {
                        if (x >= b.minX() - buildingGap && x <= b.maxX() + buildingGap
                                && z >= b.minZ() - buildingGap && z <= b.maxZ() + buildingGap) {
                            hitBld = true;
                            break;
                        }
                    }
                    if (hitBld) continue;
                    boolean nearAltar = false;
                    for (BlockPos a : existingAltars) {
                        double adx = a.getX() - x, adz = a.getZ() - z;
                        if (adx * adx + adz * adz < minAltarSq) {
                            nearAltar = true;
                            break;
                        }
                    }
                    if (!nearAltar) return new int[] { x, z };
                }
            }
        }
        return new int[] { startX, startZ };
    }

    private static List<CoverSpot> placeOutdoorCovers(Preset preset, int mapCx, int mapCz,
                                                      List<PlacedBuilding> placed, Plan terrain) {
        List<CoverSpot> list = new ArrayList<>();
        int lim = preset.radius() - 22;
        double maxSq = lim * (double) lim;
        int step = 30;
        for (int gx = -lim; gx <= lim; gx += step) {
            for (int gz = -lim; gz <= lim; gz += step) {
                int jx = (int) Math.round((BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x101L) - 0.5) * 14.0);
                int jz = (int) Math.round((BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x202L) - 0.5) * 14.0);
                int wx = mapCx + gx + jx;
                int wz = mapCz + gz + jz;
                double rx = wx - mapCx, rz = wz - mapCz;
                if (rx * rx + rz * rz > maxSq) continue;
                if (terrain.roadDistance(wx, wz) < 5.0) continue;
                int sy = terrain.surfaceY(wx, wz);
                if (sy <= preset.waterLevel()) continue;

                boolean skip = false;
                for (PlacedBuilding b : placed) {
                    if (wx >= b.minX() - 6 && wx <= b.maxX() + 6 && wz >= b.minZ() - 6 && wz <= b.maxZ() + 6) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                for (BlockPos a : terrain.altars) {
                    if (Math.hypot(a.getX() - wx, a.getZ() - wz) < 11.0) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;

                RegionBiome biome = terrain.biomeAtWorld(wx, wz);
                int kind = (int) Math.floor(BRMapPresets.hash01(wx, wz, preset.seed() ^ 0x303L) * 4.0) % 4;
                list.add(new CoverSpot(wx, wz, biome, kind));
            }
        }
        return list;
    }

    private static double pointToSegmentDist(double px, double pz, double x0, double z0, double x1, double z1) {
        double vx = x1 - x0;
        double vz = z1 - z0;
        double wx = px - x0;
        double wz = pz - z0;
        double lenSq = vx * vx + vz * vz;
        if (lenSq <= 1e-6) return Math.hypot(px - x0, pz - z0);
        double t = Math.max(0.0, Math.min(1.0, (wx * vx + wz * vz) / lenSq));
        return Math.hypot(px - (x0 + t * vx), pz - (z0 + t * vz));
    }
}
