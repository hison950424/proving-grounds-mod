package com.provinggrounds.data;

import com.provinggrounds.admin.build.BuildingBlueprint;
import com.provinggrounds.admin.build.BuildingCatalog;
import com.provinggrounds.admin.build.WildStructures;
import com.provinggrounds.admin.build.WildStructures.MicroKind;
import com.provinggrounds.admin.build.WildStructures.SmallKind;
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
 * 目的: 計算大逃殺程序化地圖的三層密集佈局（大城區＋小房區聚落＋野外戰術微據點）、
 *       放射／環狀公路與支線小路、復活祭壇安全點位，以及高密度野外林地／岩群／掩體點位。
 */
public final class BRMapLayout {

    private static final int MAJOR_GAP = 6;
    private static final int SMALL_GAP = 4;
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
    }

    public record SmallBuilding(
        SmallKind kind,
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

    public record CompoundBox(
        RegionBiome biome,
        int centerX,
        int centerZ,
        int y,
        int minX,
        int maxX,
        int minZ,
        int maxZ
    ) {}

    public record MicroPoi(
        MicroKind kind,
        RegionBiome biome,
        int x,
        int y,
        int z,
        Direction facing
    ) {}

    public record RoadSeg(int x0, int z0, int x1, int z1, int halfWidth, boolean countryPath) {}

    /**
     * 野外掩體種類：
     *   0 = 軍車／吉普車、1 = L 形沙包／石磚掩體、2 = 可穿行貨櫃／斷垣、
     *   3 = 多棵成簇樹林＋倒木、4 = 巨石岩脊群、5 = 長條戰術矮牆掩體。
     */
    public record CoverSpot(int x, int z, RegionBiome biome, int kind) {}

    public static final class Plan {
        public final Preset preset;
        public final int centerX;
        public final int centerZ;
        public final List<PlacedBuilding> buildings;
        public final List<SmallBuilding> smallBuildings;
        public final List<DistrictBox> districts;
        public final List<CompoundBox> compounds;
        public final List<RoadSeg> roads;
        public final List<BlockPos> altars;
        public final List<MicroPoi> microPois;
        public final List<CoverSpot> covers;

        Plan(Preset preset, int centerX, int centerZ, List<PlacedBuilding> buildings,
             List<SmallBuilding> smallBuildings, List<DistrictBox> districts, List<CompoundBox> compounds,
             List<RoadSeg> roads, List<BlockPos> altars, List<MicroPoi> microPois, List<CoverSpot> covers) {
            this.preset = preset;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.buildings = List.copyOf(buildings);
            this.smallBuildings = List.copyOf(smallBuildings);
            this.districts = List.copyOf(districts);
            this.compounds = List.copyOf(compounds);
            this.roads = List.copyOf(roads);
            this.altars = List.copyOf(altars);
            this.microPois = List.copyOf(microPois);
            this.covers = List.copyOf(covers);
        }

        /** 計算指定世界座標的自然高程（平滑揉合大城區、小房區、微據點、祭壇廣場、河道與邊界）。 */
        public int surfaceY(int wx, int wz) {
            double relX = wx - (double) centerX;
            double relZ = wz - (double) centerZ;
            double base = evalNaturalHeight(preset, relX, relZ);

            double distWeightSum = 0.0;
            double distWeightedY = 0.0;
            double maxDistrictBlend = 0.0;
            for (DistrictBox d : districts) {
                if (wx < d.minX() - 22 || wx > d.maxX() + 22 || wz < d.minZ() - 22 || wz > d.maxZ() + 22) continue;
                double dx = Math.max(0.0, Math.max(d.minX() - wx, wx - d.maxX()));
                double dz = Math.max(0.0, Math.max(d.minZ() - wz, wz - d.maxZ()));
                double dist = Math.hypot(dx, dz);
                if (dist <= 0.001) return d.y();
                if (dist < 22.0) {
                    double t = 1.0 - dist / 22.0;
                    double blend = t * t * (3.0 - 2.0 * t);
                    distWeightSum += blend;
                    distWeightedY += blend * d.y();
                    if (blend > maxDistrictBlend) maxDistrictBlend = blend;
                }
            }
            if (maxDistrictBlend > 0.0) {
                base = base * (1.0 - maxDistrictBlend) + (distWeightedY / distWeightSum) * maxDistrictBlend;
            }

            // 小房區院落平滑過渡（院內全平、向外 14 格自然接坡）
            double compWeightSum = 0.0;
            double compWeightedY = 0.0;
            double maxCompBlend = 0.0;
            for (CompoundBox c : compounds) {
                if (wx < c.minX() - 14 || wx > c.maxX() + 14 || wz < c.minZ() - 14 || wz > c.maxZ() + 14) continue;
                double dx = Math.max(0.0, Math.max(c.minX() - wx, wx - c.maxX()));
                double dz = Math.max(0.0, Math.max(c.minZ() - wz, wz - c.maxZ()));
                double dist = Math.hypot(dx, dz);
                if (dist <= 0.001) return c.y();
                if (dist < 14.0) {
                    double t = 1.0 - dist / 14.0;
                    double blend = t * t * (3.0 - 2.0 * t);
                    compWeightSum += blend;
                    compWeightedY += blend * c.y();
                    if (blend > maxCompBlend) maxCompBlend = blend;
                }
            }
            if (maxCompBlend > 0.0) {
                base = base * (1.0 - maxCompBlend) + (compWeightedY / compWeightSum) * maxCompBlend;
            }

            // 野區微據點平地
            for (MicroPoi m : microPois) {
                if (Math.abs(wx - m.x()) > 13 || Math.abs(wz - m.z()) > 13) continue;
                double dx = Math.max(0.0, Math.abs(wx - m.x()) - 5.5);
                double dz = Math.max(0.0, Math.abs(wz - m.z()) - 5.5);
                double dist = Math.hypot(dx, dz);
                if (dist <= 0.001) return m.y();
                if (dist < 7.0) {
                    double t = 1.0 - dist / 7.0;
                    double w = t * t * (3.0 - 2.0 * t);
                    base = base * (1.0 - w) + m.y() * w;
                }
            }

            // 祭壇廣場平地（半徑 4.5 格全平、向外 10 格柔順過渡）
            for (BlockPos a : altars) {
                if (Math.abs(wx - a.getX()) > 15 || Math.abs(wz - a.getZ()) > 15) continue;
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

        /** 回傳點距離最近道路外緣的距離（<= 0 代表在路上）。 */
        public double roadDistance(int wx, int wz) {
            RoadSeg hit = nearestRoad(wx, wz);
            if (hit == null) return Double.MAX_VALUE;
            return pointToSegmentDist(wx + 0.5, wz + 0.5, hit.x0() + 0.5, hit.z0() + 0.5, hit.x1() + 0.5, hit.z1() + 0.5)
                - hit.halfWidth();
        }

        @Nullable
        public RoadSeg nearestRoad(int wx, int wz) {
            RoadSeg bestSeg = null;
            double best = Double.MAX_VALUE;
            for (RoadSeg seg : roads) {
                double minX = Math.min(seg.x0(), seg.x1()) - 12;
                double maxX = Math.max(seg.x0(), seg.x1()) + 12;
                double minZ = Math.min(seg.z0(), seg.z1()) - 12;
                double maxZ = Math.max(seg.z0(), seg.z1()) + 12;
                if (wx < minX || wx > maxX || wz < minZ || wz > maxZ) continue;
                double d = pointToSegmentDist(wx + 0.5, wz + 0.5, seg.x0() + 0.5, seg.z0() + 0.5, seg.x1() + 0.5, seg.z1() + 0.5)
                    - seg.halfWidth();
                if (d < best) {
                    best = d;
                    bestSeg = seg;
                }
            }
            return bestSeg;
        }

        public RegionBiome biomeAtWorld(int wx, int wz) {
            return BRMapPresets.biomeAt(preset, wx - centerX, wz - centerZ);
        }
    }

    private BRMapLayout() {}

    static double evalNaturalHeight(Preset preset, double relX, double relZ) {
        long s = preset.seed();
        double n1 = BRMapPresets.noise2D(relX * 0.0085, relZ * 0.0085, s) - 0.5;
        double n2 = BRMapPresets.noise2D(relX * 0.024 + 50.0, relZ * 0.024 - 30.0, s ^ 0x777L) - 0.5;
        double n3 = BRMapPresets.noise2D(relX * 0.065 - 15.0, relZ * 0.065 + 42.0, s ^ 0xABCDEFL) - 0.5;
        double hill = 70.0 + n1 * 12.0 + n2 * 5.0 + n3 * 1.8;

        double r = Math.hypot(relX, relZ);
        double edgeR = preset.radius() - 10.0;
        Theme th = preset.theme();

        if (th == Theme.HARBOR) {
            if (relZ > 75.0) {
                double bay = Math.min(1.0, (relZ - 75.0) / 120.0);
                hill -= bay * 10.5;
            }
        } else if (th == Theme.DESERT) {
            double dune = Math.sin((relX + relZ) * 0.055) * 1.6;
            hill += dune + 1.5;
            if (Math.hypot(relX - 95.0, relZ + 95.0) < 38.0) {
                hill -= 9.0;
            }
        } else if (th == Theme.ARCTIC) {
            if (relZ < -120.0) hill += Math.min(1.0, (-relZ - 120.0) / 250.0) * 6.5;
            if (Math.hypot(relX - 180.0, relZ - 95.0) < 55.0) hill -= 9.5;
        } else if (th == Theme.IMPERIAL) {
            double riverX = 165.0 + Math.sin(relZ * 0.012) * 55.0;
            double dRiv = Math.abs(relX - riverX);
            if (dRiv < 24.0) {
                double t = 1.0 - dRiv / 24.0;
                hill -= t * t * 12.0;
            }
        } else if (th == Theme.CONTINENT) {
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
            if (th == Theme.HARBOR || th == Theme.CONTINENT) hill -= k * 14.0;
            else hill += k * 14.0;
        }
        return hill;
    }

    /** 依預設計算完整三層密集建築、道路支線、祭壇、微據點與高密度野外掩體配置。 */
    public static Plan buildPlan(Preset preset, int centerX, int centerZ) {
        List<PlacedBuilding> placed = new ArrayList<>();
        List<DistrictBox> districtBoxes = new ArrayList<>();

        // 第一層：大城區密集街廓
        for (DistrictDef d : preset.districts()) {
            int dcx = centerX + d.relX();
            int dcz = centerZ + d.relZ();
            List<PlacedBuilding> distPlaced = new ArrayList<>();

            List<String> ordered = new ArrayList<>(d.buildingIds());
            ordered.sort(Comparator.comparingInt((String id) -> {
                BuildingBlueprint bp = BuildingCatalog.byId(id);
                return bp == null ? 0 : bp.width() * bp.depth();
            }).reversed());

            for (int idx = 0; idx < ordered.size(); idx++) {
                BuildingBlueprint bp = BuildingCatalog.byId(ordered.get(idx));
                if (bp == null) continue;
                PlacedBuilding chosen = findTightMajorSlot(preset, centerX, centerZ, dcx, dcz, d, bp, idx, placed);
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

        // 建立主幹道與環狀公路
        List<RoadSeg> roads = buildMainRoads(centerX, centerZ, preset.districts());

        // 第二層：空白地帶自動填充小型衛星房區（2～3 棟小建物圍合＋支線小路）
        List<SmallBuilding> smallBuildings = new ArrayList<>();
        List<CompoundBox> compounds = new ArrayList<>();
        packSmallCompounds(preset, centerX, centerZ, placed, districtBoxes, roads, smallBuildings, compounds);

        // 復活祭壇：確保同時避開大城區建築與小房區建築
        Plan preAltar = new Plan(preset, centerX, centerZ, placed, smallBuildings, districtBoxes, compounds, roads, List.of(), List.of(), List.of());
        List<BlockPos> altars = placeAltars(preset, centerX, centerZ, placed, smallBuildings, preAltar);

        // 第三層：路旁檢查哨與荒野戰術微據點
        Plan preMicro = new Plan(preset, centerX, centerZ, placed, smallBuildings, districtBoxes, compounds, roads, altars, List.of(), List.of());
        List<MicroPoi> microPois = placeMicroPois(preset, centerX, centerZ, placed, smallBuildings, altars, preMicro);

        // 野外高密度成片樹林／岩丘／長條戰術掩體／車輛／貨櫃
        Plan preCover = new Plan(preset, centerX, centerZ, placed, smallBuildings, districtBoxes, compounds, roads, altars, microPois, List.of());
        List<CoverSpot> covers = placeOutdoorCovers(preset, centerX, centerZ, placed, smallBuildings, microPois, altars, preCover);

        return new Plan(preset, centerX, centerZ, placed, smallBuildings, districtBoxes, compounds, roads, altars, microPois, covers);
    }

    @Nullable
    private static PlacedBuilding findTightMajorSlot(Preset preset, int mapCx, int mapCz, int dcx, int dcz,
                                                     DistrictDef d, BuildingBlueprint bp, int idx, List<PlacedBuilding> allPlaced) {
        double maxCornerR = preset.radius() - CIRCLE_MARGIN;
        Direction[] dirs = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
        int dirBase = Math.floorMod(Long.hashCode(preset.seed() ^ (idx * 0x9E3779B9L + dcx * 31L + dcz * 17L)), 4);
        int scanSignX = (dirBase % 2 == 0) ? 1 : -1;
        int scanSignZ = (dirBase < 2) ? 1 : -1;
        PlacedBuilding best = null;
        double bestScore = Double.MAX_VALUE;
        for (int shell = 6; shell <= 148; shell += 4) {
            for (int rawX = -shell; rawX <= shell; rawX += 4) {
                int offX = rawX * scanSignX;
                for (int rawZ = -shell; rawZ <= shell; rawZ += 4) {
                    if (Math.max(Math.abs(rawX), Math.abs(rawZ)) != shell) continue;
                    int offZ = rawZ * scanSignZ;
                    int ox = dcx + offX;
                    int oz = dcz + offZ;
                    Direction inward = inwardFacing(dcx - ox, dcz - oz, dirs[(idx + dirBase) % 4]);
                    // 交錯變化朝向（面向廣場、面向街角切線、或朝外側街道）讓建築不再全朝同邊
                    Direction primary = switch ((idx + dirBase) & 3) {
                        case 0 -> inward;
                        case 1 -> inward.getClockWise();
                        case 2 -> inward.getCounterClockWise();
                        default -> dirs[dirBase];
                    };
                    Direction[] tryDirs = { primary, primary.getClockWise(), primary.getCounterClockWise(), primary.getOpposite() };
                    for (Direction f : tryDirs) {
                        int[] box = footprint(bp.width(), bp.depth(), ox, oz, f);
                        if (!allCornersWithin(box, mapCx, mapCz, maxCornerR)) continue;
                        if (overlapsAnyMajor(box, allPlaced, MAJOR_GAP)) continue;
                        double cx = 0.5 * (box[0] + box[1]);
                        double cz = 0.5 * (box[2] + box[3]);
                        double distFromCenter = Math.hypot(cx - dcx, cz - dcz);
                        if (distFromCenter < 12.0) continue;
                        double score = distFromCenter + (f == primary ? 0.0 : 3.5);
                        if (score < bestScore) {
                            bestScore = score;
                            best = new PlacedBuilding(bp, d.name(), d.biome(), ox, d.elevation(), oz, f, box[0], box[1], box[2], box[3]);
                        }
                        break;
                    }
                }
            }
            if (best != null && shell >= bestScore * 0.75) break;
        }
        return best;
    }

    private static void packSmallCompounds(Preset preset, int mapCx, int mapCz,
                                           List<PlacedBuilding> majors, List<DistrictBox> districts,
                                           List<RoadSeg> roads, List<SmallBuilding> outSmall, List<CompoundBox> outCompounds) {
        int lim = preset.radius() - 36;
        int step = 92;
        int maxCompounds = Math.max(4, preset.radius() / 32);
        double maxCornerR = preset.radius() - CIRCLE_MARGIN;
        Direction[] dirs = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);

        for (int gx = -lim; gx <= lim && outCompounds.size() < maxCompounds; gx += step) {
            int row = Math.floorDiv(gx + lim, step);
            int zShift = (row % 2 == 1) ? step / 2 : 0;
            for (int gz = -lim + zShift; gz <= lim && outCompounds.size() < maxCompounds; gz += step) {
                int jx = (int) Math.round((BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x444L) - 0.5) * 44.0);
                int jz = (int) Math.round((BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x555L) - 0.5) * 44.0);
                int cx = mapCx + gx + jx;
                int cz = mapCz + gz + jz;
                double relX = cx - mapCx;
                double relZ = cz - mapCz;
                if (Math.hypot(relX, relZ) > lim) continue;

                // 有機群聚噪聲：只在中高群聚區與隨機散落點放置小房區，避免平均網格感且大幅降低數量
                double cluster = BRMapPresets.noise2D(relX * 0.011 + 17.0, relZ * 0.011 - 29.0, preset.seed() ^ 0x411L);
                double sparseGate = BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x412L);
                if (cluster < 0.44 && sparseGate > 0.22) continue;
                if (cluster < 0.56 && sparseGate > 0.62) continue;

                // 避開水域（例如港口與護城河），只把房區蓋在陸地上
                double nh = evalNaturalHeight(preset, relX, relZ);
                if (nh <= preset.waterLevel() + 1.5) continue;

                // 避開各大城區腹地與主幹道正中央
                boolean nearDistrict = false;
                for (DistrictBox d : districts) {
                    if (cx >= d.minX() - 26 && cx <= d.maxX() + 26 && cz >= d.minZ() - 26 && cz <= d.maxZ() + 26) {
                        nearDistrict = true;
                        break;
                    }
                }
                if (nearDistrict) continue;

                double minCompDist = (cluster > 0.66) ? 56.0 : 88.0;
                boolean nearCompound = false;
                for (CompoundBox cb : outCompounds) {
                    if (Math.hypot(cb.centerX() - cx, cb.centerZ() - cz) < minCompDist) {
                        nearCompound = true;
                        break;
                    }
                }
                if (nearCompound) continue;

                double minRoadDist = Double.MAX_VALUE;
                int nearRoadX = cx, nearRoadZ = cz;
                for (RoadSeg r : roads) {
                    if (r.countryPath()) continue;
                    double[] proj = closestPointOnSegment(cx, cz, r.x0(), r.z0(), r.x1(), r.z1());
                    double d = Math.hypot(cx - proj[0], cz - proj[1]);
                    if (d < minRoadDist) {
                        minRoadDist = d;
                        nearRoadX = (int) Math.round(proj[0]);
                        nearRoadZ = (int) Math.round(proj[1]);
                    }
                }
                if (minRoadDist < 16.0) continue;

                int elev = Math.max(preset.waterLevel() + 2, (int) Math.round(nh));
                RegionBiome biome = BRMapPresets.biomeAt(preset, relX, relZ);
                SmallKind[] palette = WildStructures.smallKindsFor(biome);
                int wantCount = (cluster > 0.65 || BRMapPresets.hash01(cx, cz, preset.seed() ^ 0x666L) > 0.45) ? 3 : 2;

                List<SmallBuilding> group = new ArrayList<>();
                for (int k = 0; k < wantCount; k++) {
                    SmallKind kind = palette[(int) Math.floor(BRMapPresets.hash01(cx + k * 13L, cz - k * 7L, preset.seed() ^ 0x777L) * palette.length) % palette.length];
                    SmallBuilding slot = findSmallSlot(preset.seed(), mapCx, mapCz, cx, cz, elev, biome, kind, k, maxCornerR, dirs, majors, outSmall, group);
                    if (slot != null) {
                        group.add(slot);
                    }
                }

                if (group.size() >= 2) {
                    outSmall.addAll(group);
                    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                    for (SmallBuilding sb : group) {
                        minX = Math.min(minX, sb.minX());
                        maxX = Math.max(maxX, sb.maxX());
                        minZ = Math.min(minZ, sb.minZ());
                        maxZ = Math.max(maxZ, sb.maxZ());
                    }
                    outCompounds.add(new CompoundBox(biome, cx, cz, elev, minX - 4, maxX + 4, minZ - 4, maxZ + 4));
                    if (minRoadDist <= 165.0) {
                        roads.add(new RoadSeg(cx, cz, nearRoadX, nearRoadZ, 1, true));
                    }
                }
            }
        }
    }

    @Nullable
    private static SmallBuilding findSmallSlot(long seed, int mapCx, int mapCz, int cx, int cz, int elev,
                                               RegionBiome biome, SmallKind kind, int idx, double maxCornerR,
                                               Direction[] dirs, List<PlacedBuilding> majors,
                                               List<SmallBuilding> existingSmall, List<SmallBuilding> group) {
        int dirSeed = Math.floorMod(Long.hashCode(seed ^ (cx * 73856093L) ^ (cz * 19349663L) ^ idx), 4);
        int signX = (dirSeed % 2 == 0) ? 1 : -1;
        int signZ = (dirSeed < 2) ? 1 : -1;
        SmallBuilding best = null;
        double bestDist = Double.MAX_VALUE;
        for (int shell = 4; shell <= 36; shell += 4) {
            for (int rawX = -shell; rawX <= shell; rawX += 4) {
                int ox = rawX * signX;
                for (int rawZ = -shell; rawZ <= shell; rawZ += 4) {
                    if (Math.max(Math.abs(rawX), Math.abs(rawZ)) != shell) continue;
                    int oz = rawZ * signZ;
                    int wx = cx + ox;
                    int wz = cz + oz;
                    Direction inward = inwardFacing(cx - wx, cz - wz, dirs[(idx + dirSeed) % 4]);
                    Direction primary = switch ((idx + dirSeed) & 3) {
                        case 0 -> inward;
                        case 1 -> inward.getClockWise();
                        case 2 -> inward.getCounterClockWise();
                        default -> dirs[dirSeed];
                    };
                    int[] box = footprint(kind.width, kind.depth, wx, wz, primary);
                    if (!allCornersWithin(box, mapCx, mapCz, maxCornerR)) continue;
                    if (overlapsAnyMajor(box, majors, MAJOR_GAP)) continue;
                    if (overlapsAnySmall(box, existingSmall, SMALL_GAP)) continue;
                    if (overlapsAnySmall(box, group, SMALL_GAP)) continue;
                    double bCx = 0.5 * (box[0] + box[1]);
                    double bCz = 0.5 * (box[2] + box[3]);
                    double d = Math.hypot(bCx - cx, bCz - cz);
                    if (d < 5.0 || d >= bestDist) continue;
                    bestDist = d;
                    best = new SmallBuilding(kind, biome, wx, elev, wz, primary, box[0], box[1], box[2], box[3]);
                }
            }
            if (best != null) break;
        }
        return best;
    }

    private static Direction inwardFacing(int dx, int dz, Direction fallback) {
        if (Math.abs(dx) < 2 && Math.abs(dz) < 2) return fallback;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** 精確對應 BuildingCanvas.toWorld 的佔地邊界 {minX, maxX, minZ, maxZ}。 */
    public static int[] footprint(int width, int depth, int originX, int originZ, Direction facing) {
        Direction right = facing.getClockWise();
        int halfW = (width + 1) / 2 + 1;
        int[] xs = { -halfW, halfW };
        int[] zs = { -1, depth };
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

    private static boolean overlapsAnyMajor(int[] box, List<PlacedBuilding> list, int gap) {
        for (PlacedBuilding b : list) {
            if (b.intersectsWithGap(box[0], box[1], box[2], box[3], gap)) return true;
        }
        return false;
    }

    private static boolean overlapsAnySmall(int[] box, List<SmallBuilding> list, int gap) {
        for (SmallBuilding b : list) {
            if (b.intersectsWithGap(box[0], box[1], box[2], box[3], gap)) return true;
        }
        return false;
    }

    private static List<RoadSeg> buildMainRoads(int cx, int cz, List<DistrictDef> districts) {
        List<RoadSeg> segs = new ArrayList<>();
        if (districts.isEmpty()) return segs;
        DistrictDef hub = districts.get(0);
        int hx = cx + hub.relX();
        int hz = cz + hub.relZ();

        List<DistrictDef> outer = new ArrayList<>();
        for (int i = 1; i < districts.size(); i++) {
            DistrictDef d = districts.get(i);
            segs.add(new RoadSeg(hx, hz, cx + d.relX(), cz + d.relZ(), 3, false));
            outer.add(d);
        }
        if (outer.size() >= 2) {
            outer.sort(Comparator.comparingDouble(d -> Math.atan2(d.relZ(), d.relX())));
            for (int i = 0; i < outer.size(); i++) {
                DistrictDef a = outer.get(i);
                DistrictDef b = outer.get((i + 1) % outer.size());
                segs.add(new RoadSeg(cx + a.relX(), cz + a.relZ(), cx + b.relX(), cz + b.relZ(), 2, false));
            }
        }
        return segs;
    }

    private static List<BlockPos> placeAltars(Preset preset, int mapCx, int mapCz,
                                              List<PlacedBuilding> majors, List<SmallBuilding> smalls, Plan terrain) {
        List<BlockPos> result = new ArrayList<>();
        double maxR = preset.radius() - 22.0;
        for (AltarHint hint : preset.altars()) {
            int[] found = findClearPoint(mapCx + hint.relX(), mapCz + hint.relZ(), mapCx, mapCz, maxR, 8, majors, smalls, result, 36);
            int ax = found[0];
            int az = found[1];
            int groundY = Math.max(preset.waterLevel() + 1, terrain.surfaceY(ax, az));
            result.add(new BlockPos(ax, groundY + 1, az));
        }
        return result;
    }

    private static int[] findClearPoint(int startX, int startZ, int mapCx, int mapCz, double maxR, int buildingGap,
                                        List<PlacedBuilding> majors, List<SmallBuilding> smalls,
                                        List<BlockPos> existingAltars, int minAltarSep) {
        double maxSq = maxR * maxR;
        double minAltarSq = minAltarSep * (double) minAltarSep;
        for (int r = 0; r <= 128; r += 6) {
            for (int dx = -r; dx <= r; dx += 6) {
                for (int dz = -r; dz <= r; dz += 6) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = startX + dx;
                    int z = startZ + dz;
                    double rx = x - mapCx, rz = z - mapCz;
                    if (rx * rx + rz * rz > maxSq) continue;
                    if (pointHitsAnyBuilding(x, z, buildingGap, majors, smalls)) continue;
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

    private static List<MicroPoi> placeMicroPois(Preset preset, int mapCx, int mapCz,
                                                 List<PlacedBuilding> majors, List<SmallBuilding> smalls,
                                                 List<BlockPos> altars, Plan terrain) {
        List<MicroPoi> list = new ArrayList<>();
        int lim = preset.radius() - 26;
        double maxSq = lim * (double) lim;
        int step = 76;
        int maxMicro = Math.max(4, preset.radius() / 36);
        MicroKind[] roadsidePool = { MicroKind.CHECKPOINT, MicroKind.CONTAINER_YARD, MicroKind.CRASHED_CONVOY, MicroKind.WATER_TOWER };
        MicroKind[] wildPool = { MicroKind.FIELD_CAMP, MicroKind.RUIN_WALLS, MicroKind.TRENCH_POST, MicroKind.LUMBER_CAMP };

        for (int gx = -lim; gx <= lim && list.size() < maxMicro; gx += step) {
            for (int gz = -lim; gz <= lim && list.size() < maxMicro; gz += step) {
                int jx = (int) Math.round((BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x888L) - 0.5) * 30.0);
                int jz = (int) Math.round((BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x999L) - 0.5) * 30.0);
                int wx = mapCx + gx + jx;
                int wz = mapCz + gz + jz;
                double rx = wx - mapCx, rz = wz - mapCz;
                if (rx * rx + rz * rz > maxSq) continue;

                double rd = terrain.roadDistance(wx, wz);
                boolean roadside = rd <= 28.0;
                double cluster = BRMapPresets.noise2D(rx * 0.014 - 9.0, rz * 0.014 + 21.0, preset.seed() ^ 0x812L);
                double chance = BRMapPresets.hash01(wx, wz, preset.seed() ^ 0x813L);
                // 路旁哨站偏重於某些群落段，荒野點非均勻出現且數量減半
                if (!roadside && cluster < 0.48) continue;
                if (!roadside && chance > 0.55) continue;
                if (roadside && chance > 0.65) continue;

                int sy = terrain.surfaceY(wx, wz);
                if (sy <= preset.waterLevel() + 1) continue;
                if (rd < 4.5) continue;
                if (pointHitsAnyBuilding(wx, wz, 9, majors, smalls)) continue;

                boolean skip = false;
                for (BlockPos a : altars) {
                    if (Math.hypot(a.getX() - wx, a.getZ() - wz) < 15.0) { skip = true; break; }
                }
                if (skip) continue;
                for (MicroPoi m : list) {
                    if (Math.hypot(m.x() - wx, m.z() - wz) < 62.0) { skip = true; break; }
                }
                if (skip) continue;

                RegionBiome biome = terrain.biomeAtWorld(wx, wz);
                double pick = BRMapPresets.hash01(wx, wz, preset.seed() ^ 0xAAAL);
                MicroKind[] pool = roadside ? roadsidePool : wildPool;
                MicroKind kind = pool[(int) Math.floor(pick * pool.length) % pool.length];
                Direction facing = Direction.from2DDataValue(Math.floorMod(wx * 13 + wz * 29, 4));
                list.add(new MicroPoi(kind, biome, wx, sy, wz, facing));
            }
        }
        return list;
    }

    private static List<CoverSpot> placeOutdoorCovers(Preset preset, int mapCx, int mapCz,
                                                      List<PlacedBuilding> majors, List<SmallBuilding> smalls,
                                                      List<MicroPoi> microPois, List<BlockPos> altars, Plan terrain) {
        List<CoverSpot> list = new ArrayList<>();
        int lim = preset.radius() - 18;
        double maxSq = lim * (double) lim;
        int step = 20;
        for (int gx = -lim; gx <= lim; gx += step) {
            for (int gz = -lim; gz <= lim; gz += step) {
                int jx = (int) Math.round((BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x101L) - 0.5) * 14.0);
                int jz = (int) Math.round((BRMapPresets.hash01(gx, gz, preset.seed() ^ 0x202L) - 0.5) * 14.0);
                int wx = mapCx + gx + jx;
                int wz = mapCz + gz + jz;
                double rx = wx - mapCx, rz = wz - mapCz;
                if (rx * rx + rz * rz > maxSq) continue;

                // 非均勻密度噪聲：密林/亂石群密集(80%)、中度(35%)、開闊平原極少(12%)，避免全圖整齊平均塞滿
                double density = BRMapPresets.noise2D(rx * 0.016 + 33.0, rz * 0.016 - 19.0, preset.seed() ^ 0xC13L);
                double gate = BRMapPresets.hash01(wx, wz, preset.seed() ^ 0xC14L);
                double keepProb = density > 0.58 ? 0.80 : (density > 0.36 ? 0.36 : 0.12);
                if (gate > keepProb) continue;

                if (terrain.roadDistance(wx, wz) < 3.8) continue;
                int sy = terrain.surfaceY(wx, wz);
                if (sy <= preset.waterLevel()) continue;

                if (pointHitsAnyBuilding(wx, wz, 5, majors, smalls)) continue;
                boolean skip = false;
                for (MicroPoi m : microPois) {
                    if (Math.abs(m.x() - wx) <= 8 && Math.abs(m.z() - wz) <= 8) { skip = true; break; }
                }
                if (skip) continue;
                for (BlockPos a : altars) {
                    if (Math.hypot(a.getX() - wx, a.getZ() - wz) < 9.0) { skip = true; break; }
                }
                if (skip) continue;

                RegionBiome biome = terrain.biomeAtWorld(wx, wz);
                double cluster = BRMapPresets.noise2D(rx * 0.024, rz * 0.024, preset.seed() ^ 0xBBBL);
                double pick = BRMapPresets.hash01(wx, wz, preset.seed() ^ 0x303L);
                int kind;
                if (biome != RegionBiome.DESERT_SAND && biome != RegionBiome.RED_CANYON && cluster > 0.54 && pick < 0.68) {
                    kind = 3; // 成片森林樹叢帶
                } else if (cluster < 0.28 || pick < 0.20) {
                    kind = 4; // 多格巨石／山岩柱／冰丘
                } else if (pick < 0.36) {
                    kind = 5; // 長條戰術矮牆
                } else if (pick < 0.52) {
                    kind = 1; // L 型戰術沙包／石磚掩體
                } else if (pick < 0.66) {
                    kind = 0; // 軍車／吉普車
                } else if (pick < 0.80) {
                    kind = 2; // 貨櫃／斷垣
                } else if (pick < 0.90) {
                    kind = 6; // 乾草堆＋木柵掩體
                } else {
                    kind = 7; // 工業管線／鐵網掩體
                }
                list.add(new CoverSpot(wx, wz, biome, kind));
            }
        }
        return list;
    }

    private static boolean pointHitsAnyBuilding(int x, int z, int gap, List<PlacedBuilding> majors, List<SmallBuilding> smalls) {
        for (PlacedBuilding b : majors) {
            if (x >= b.minX() - gap && x <= b.maxX() + gap && z >= b.minZ() - gap && z <= b.maxZ() + gap) return true;
        }
        for (SmallBuilding b : smalls) {
            if (x >= b.minX() - gap && x <= b.maxX() + gap && z >= b.minZ() - gap && z <= b.maxZ() + gap) return true;
        }
        return false;
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

    private static double[] closestPointOnSegment(double px, double pz, double x0, double z0, double x1, double z1) {
        double vx = x1 - x0;
        double vz = z1 - z0;
        double lenSq = vx * vx + vz * vz;
        if (lenSq <= 1e-6) return new double[] { x0, z0 };
        double t = Math.max(0.0, Math.min(1.0, ((px - x0) * vx + (pz - z0) * vz) / lenSq));
        return new double[] { x0 + t * vx, z0 + t * vz };
    }
}
