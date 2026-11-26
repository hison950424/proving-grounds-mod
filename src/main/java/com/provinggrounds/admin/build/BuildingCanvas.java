package com.provinggrounds.admin.build;

import com.provinggrounds.registry.PGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: BuildingCanvas.java
 * 目的: 3D 方向旋轉建築畫布引擎：
 *   - 局部座標 (lx, ly, lz)：lx 左右（+右）、ly 上下（0 = 一樓地面）、lz 前後（0 = 正門、往建築內部遞增）。
 *   - 依玩家面向（NORTH / SOUTH / EAST / WEST）自動旋轉所有方塊座標與方向屬性（樓梯、門、梯子等）。
 *   - 自動往下補齊地基（最深 12 格），在斜坡或丘陵蓋房子也不會懸空。
 *   - 自動記錄每個被覆蓋的原始方塊狀態，支援一鍵完整復原；並收集所有物資箱生成點自動登記進大逃殺地圖。
 */
public final class BuildingCanvas {

    private final ServerLevel level;
    private final BlockPos origin;
    private final Direction facing; // 指向建築內部（玩家面對的方向）
    private final Direction right;
    private final Rotation rotation;
    private final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
    private final LinkedHashSet<BlockPos> lootPoints = new LinkedHashSet<>();
    private BlockState foundationBlock = Blocks.STONE_BRICKS.defaultBlockState();

    public BuildingCanvas(ServerLevel level, BlockPos origin, Direction facing) {
        this.level = level;
        this.origin = origin;
        this.facing = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        this.right = this.facing.getClockWise();
        this.rotation = switch (this.facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** 計算放置基準點（玩家前方 2 格；若正在空中飛行，自動往下對齊 24 格內的實心地面）。預覽框與實際生成共用。 */
    public static BlockPos anchorFor(Player p) {
        Direction dir = p.getDirection();
        BlockPos base = p.blockPosition().relative(dir, 2);
        Level lv = p.level();
        if (!p.onGround()) {
            for (int dy = 0; dy <= 24; dy++) {
                BlockPos check = base.below(dy);
                BlockState below = lv.getBlockState(check.below());
                if (isSolidGround(below)) {
                    return check;
                }
            }
        }
        return base;
    }

    private static boolean isSolidGround(BlockState s) {
        if (s.isAir() || s.liquid() || s.canBeReplaced()) return false;
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)) return false;
        return s.blocksMotion();
    }

    public BuildingCanvas foundation(Block block) {
        this.foundationBlock = block.defaultBlockState();
        return this;
    }

    /** 局部座標轉世界座標。 */
    public BlockPos toWorld(int lx, int ly, int lz) {
        return origin.offset(
            right.getStepX() * lx + facing.getStepX() * lz,
            ly,
            right.getStepZ() * lx + facing.getStepZ() * lz
        );
    }

    /** 放置單一方塊（自動依面向旋轉方向屬性，並保存原始方塊供復原）。 */
    public void set(int lx, int ly, int lz, BlockState state) {
        BlockPos pos = toWorld(lx, ly, lz);
        if (!level.isInWorldBounds(pos)) return;
        previous.putIfAbsent(pos, level.getBlockState(pos));
        BlockState rotated = state.rotate(rotation);
        level.setBlock(pos, rotated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    public void set(int lx, int ly, int lz, Block block) {
        set(lx, ly, lz, block.defaultBlockState());
    }

    public void air(int lx, int ly, int lz) {
        set(lx, ly, lz, Blocks.AIR.defaultBlockState());
    }

    /** 清除整個佔地空間內的雜草、樹葉與舊方塊。 */
    public void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        box(x0, y0, z0, x1, y1, z1, Blocks.AIR.defaultBlockState());
    }

    /** 實心方體。 */
    public void box(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        box(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
    }

    public void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++)
                    set(x, y, z, state);
    }

    /** 鋪設地板並自動在下方補齊山坡地基（只對 y <= 0 補地基）。 */
    public void floor(int x0, int y, int z0, int x1, int z1, Block block) {
        floor(x0, y, z0, x1, z1, block.defaultBlockState());
    }

    public void floor(int x0, int y, int z0, int x1, int z1, BlockState state) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                set(x, y, z, state);
                if (y <= 0) fillFoundationDown(x, y - 1, z);
            }
        }
    }

    /** 往下補地基直到碰到實心地面（最深 16 格）。 */
    public void fillFoundationDown(int lx, int startY, int lz) {
        for (int dy = 0; dy < 16; dy++) {
            BlockPos pos = toWorld(lx, startY - dy, lz);
            if (!level.isInWorldBounds(pos)) break;
            BlockState cur = level.getBlockState(pos);
            if (isSolidGround(cur)) break;
            previous.putIfAbsent(pos, cur);
            level.setBlock(pos, foundationBlock, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** 四面外牆（不含地板與屋頂）。 */
    public void walls(int x0, int y0, int z0, int x1, int y1, int z1, Block wall) {
        walls(x0, y0, z0, x1, y1, z1, wall.defaultBlockState());
    }

    public void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                set(x, y, minZ, wall);
                set(x, y, maxZ, wall);
            }
            for (int z = minZ; z <= maxZ; z++) {
                set(minX, y, z, wall);
                set(maxX, y, z, wall);
            }
        }
    }

    /** 四角立柱。 */
    public void cornerPillars(int x0, int y0, int z0, int x1, int y1, int z1, Block pillar) {
        pillar(x0, y0, y1, z0, pillar);
        pillar(x1, y0, y1, z0, pillar);
        pillar(x0, y0, y1, z1, pillar);
        pillar(x1, y0, y1, z1, pillar);
    }

    public void pillar(int lx, int y0, int y1, int lz, Block block) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) set(lx, y, lz, block);
    }

    /** 屋頂矮牆／女兒牆（每隔一格加高可當射擊掩體）。 */
    public void parapet(int x0, int y, int z0, int x1, int z1, Block block, boolean crenellated) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int x = minX; x <= maxX; x++) {
            set(x, y, minZ, block);
            set(x, y, maxZ, block);
            if (crenellated && (x - minX) % 2 == 0) {
                set(x, y + 1, minZ, block);
                set(x, y + 1, maxZ, block);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            set(minX, y, z, block);
            set(maxX, y, z, block);
            if (crenellated && (z - minZ) % 2 == 0) {
                set(minX, y + 1, z, block);
                set(maxX, y + 1, z, block);
            }
        }
    }

    /** 門洞（高 3 格，進出流暢不卡頭）。 */
    public void doorway(int x0, int yBase, int z, int width) {
        for (int dx = 0; dx < width; dx++)
            for (int dy = 0; dy < 3; dy++)
                air(x0 + dx, yBase + dy, z);
    }

    /** 左右牆面上的門洞。 */
    public void doorwaySide(int x, int yBase, int z0, int width) {
        for (int dz = 0; dz < width; dz++)
            for (int dy = 0; dy < 3; dy++)
                air(x, yBase + dy, z0 + dz);
    }

    /** 雙開門（基準朝向 NORTH 往建築內）。 */
    public void doubleDoor(int lxLeft, int yBase, int lz, Block doorBlock) {
        BlockState lowerL = doorBlock.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperL = lowerL.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState lowerR = doorBlock.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperR = lowerR.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        set(lxLeft, yBase, lz, lowerL);
        set(lxLeft, yBase + 1, lz, upperL);
        set(lxLeft + 1, yBase, lz, lowerR);
        set(lxLeft + 1, yBase + 1, lz, upperR);
    }

    /** 玻璃窗（水平沿 X）。 */
    public void windowX(int x0, int y0, int z, int x1, int y1, Block glass) {
        for (int y = y0; y <= y1; y++)
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                set(x, y, z, glass);
    }

    /** 玻璃窗（水平沿 Z）。 */
    public void windowZ(int x, int y0, int z0, int z1, int y1, Block glass) {
        for (int y = y0; y <= y1; y++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                set(x, y, z, glass);
    }

    /** 樓梯（canonicalDir = 樓梯往上升的方向，NORTH = 往建築內、SOUTH = 往正門、EAST = 往右、WEST = 往左）。 */
    public void stair(int lx, int ly, int lz, Block stairBlock, Direction canonicalDir) {
        set(lx, ly, lz, stairBlock.defaultBlockState().setValue(StairBlock.FACING, canonicalDir));
    }

    public void topSlab(int lx, int ly, int lz, Block slabBlock) {
        set(lx, ly, lz, slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
    }

    public void bottomSlab(int lx, int ly, int lz, Block slabBlock) {
        set(lx, ly, lz, slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
    }

    /** 沿 Z 方向上升的室內樓梯（同時自動清出頭頂通行空間）。 */
    public void stairsAlongZ(int lx, int yStart, int zStart, int steps, int dzStep, Block stairBlock) {
        Direction dir = dzStep > 0 ? Direction.NORTH : Direction.SOUTH;
        for (int i = 0; i < steps; i++) {
            int y = yStart + i;
            int z = zStart + i * dzStep;
            stair(lx, y, z, stairBlock, dir);
            air(lx, y + 1, z);
            air(lx, y + 2, z);
            air(lx, y + 3, z);
        }
    }

    /** 沿 X 方向上升的室內樓梯（同時清出頭頂空間）。 */
    public void stairsAlongX(int xStart, int yStart, int lz, int steps, int dxStep, Block stairBlock) {
        Direction dir = dxStep > 0 ? Direction.EAST : Direction.WEST;
        for (int i = 0; i < steps; i++) {
            int y = yStart + i;
            int x = xStart + i * dxStep;
            stair(x, y, lz, stairBlock, dir);
            air(x, y + 1, lz);
            air(x, y + 2, lz);
            air(x, y + 3, lz);
        }
    }

    /**
     * 完整樓層連接樓梯（沿 Z 方向上升）：務必在鋪完上層樓板之後呼叫。
     * 最後一階精準落在 y = upperFloorY 與上層地板齊平，自動切開上層開口且不會挖空最後一階。
     */
    public void staircaseZ(int x0, int x1, int lowerFloorY, int upperFloorY, int zStart, int dzStep,
                           Block stairBlock, @Nullable Block landingBlock, @Nullable Block underBlock) {
        int steps = upperFloorY - lowerFloorY;
        if (steps <= 0) return;
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        Direction dir = dzStep > 0 ? Direction.NORTH : Direction.SOUTH;
        int zEntry = zStart - dzStep;
        for (int x = minX; x <= maxX; x++) {
            for (int cy = lowerFloorY + 1; cy <= lowerFloorY + 3; cy++) air(x, cy, zEntry);
        }
        for (int i = 0; i < steps; i++) {
            int y = lowerFloorY + 1 + i;
            int z = zStart + i * dzStep;
            int topClear = (i == 0 && steps > 4) ? Math.min(y + 3, upperFloorY - 1) : Math.max(y + 3, upperFloorY + 2);
            for (int x = minX; x <= maxX; x++) {
                if (underBlock != null && i > 0) set(x, y - 1, z, underBlock);
                stair(x, y, z, stairBlock, dir);
                for (int cy = y + 1; cy <= topClear; cy++) air(x, cy, z);
            }
        }
        int zLanding = zStart + steps * dzStep;
        for (int x = minX; x <= maxX; x++) {
            if (landingBlock != null) set(x, upperFloorY, zLanding, landingBlock);
            for (int cy = upperFloorY + 1; cy <= upperFloorY + 3; cy++) air(x, cy, zLanding);
        }
    }

    /**
     * 完整樓層連接樓梯（沿 X 方向上升）：務必在鋪完上層樓板之後呼叫。
     * 最後一階精準落在 y = upperFloorY 與上層地板齊平，自動切開上層開口且不會挖空最後一階。
     */
    public void staircaseX(int z0, int z1, int lowerFloorY, int upperFloorY, int xStart, int dxStep,
                           Block stairBlock, @Nullable Block landingBlock, @Nullable Block underBlock) {
        int steps = upperFloorY - lowerFloorY;
        if (steps <= 0) return;
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        Direction dir = dxStep > 0 ? Direction.EAST : Direction.WEST;
        int xEntry = xStart - dxStep;
        for (int z = minZ; z <= maxZ; z++) {
            for (int cy = lowerFloorY + 1; cy <= lowerFloorY + 3; cy++) air(xEntry, cy, z);
        }
        for (int i = 0; i < steps; i++) {
            int y = lowerFloorY + 1 + i;
            int x = xStart + i * dxStep;
            int topClear = (i == 0 && steps > 4) ? Math.min(y + 3, upperFloorY - 1) : Math.max(y + 3, upperFloorY + 2);
            for (int z = minZ; z <= maxZ; z++) {
                if (underBlock != null && i > 0) set(x, y - 1, z, underBlock);
                stair(x, y, z, stairBlock, dir);
                for (int cy = y + 1; cy <= topClear; cy++) air(x, cy, z);
            }
        }
        int xLanding = xStart + steps * dxStep;
        for (int z = minZ; z <= maxZ; z++) {
            if (landingBlock != null) set(xLanding, upperFloorY, z, landingBlock);
            for (int cy = upperFloorY + 1; cy <= upperFloorY + 3; cy++) air(xLanding, cy, z);
        }
    }

    /** 1 格高櫃檯／長桌掩體。 */
    public void counter(int x0, int z0, int x1, int z1, int yBase, Block body) {
        box(x0, yBase, z0, x1, yBase, z1, body);
    }

    /** 1.5 格蹲射掩體（下實心方塊＋上下半磚檯面）。 */
    public void tallCover(int x0, int z0, int x1, int z1, int yBase, Block base, Block topSlab) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                set(x, yBase, z, base);
                bottomSlab(x, yBase + 1, z, topSlab);
            }
        }
    }

    /** 工作桌＋座椅（chairFacing 為座椅面對書桌的方向，例如 NORTH = 面向 +Z）。 */
    public void deskUnit(int lx, int yBase, int lz, Block deskBlock, Block chairStair, Direction chairFacing) {
        set(lx, yBase, lz, deskBlock);
        int cx = lx - switch (chairFacing) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
        int cz = lz - switch (chairFacing) {
            case NORTH -> 1;
            case SOUTH -> -1;
            default -> 0;
        };
        stair(cx, yBase, cz, chairStair, chairFacing);
    }

    /** 機櫃／配電柱掩體。 */
    public void serverRack(int lx, int yBase, int lz, int height, Block body, Block accent) {
        for (int dy = 0; dy < height; dy++) {
            set(lx, yBase + dy, lz, (dy == height - 1) ? accent : body);
        }
    }

    /** 室內花圃／綠植景觀掩體（外框＋泥土＋植物頂）。 */
    public void planter(int x0, int z0, int x1, int z1, int yBase, Block border, Block soil, Block foliage) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                boolean edge = (x == minX || x == maxX || z == minZ || z == maxZ) && (maxX - minX >= 2 && maxZ - minZ >= 2);
                if (edge) {
                    set(x, yBase, z, border);
                } else {
                    set(x, yBase, z, soil);
                    set(x, yBase + 1, z, foliage);
                }
            }
        }
    }

    /** 雙格病床／床鋪掩體（沿 X 或 Z 擺放，取長邊方向放枕頭）。 */
    public void bedUnit(int x0, int z0, int x1, int z1, int yBase, Block blanket, Block pillow) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        box(minX, yBase, minZ, maxX, yBase, maxZ, blanket);
        if (maxZ - minZ >= maxX - minX) {
            for (int x = minX; x <= maxX; x++) set(x, yBase, maxZ, pillow);
        } else {
            for (int z = minZ; z <= maxZ; z++) set(minX, yBase, z, pillow);
        }
    }

    /** 沿 X 軸排列的沙發長椅（finalizeShapes 後端點自動轉角）。 */
    public void sofaX(int x0, int x1, int yBase, int lz, Block stairBlock, Direction faceDir) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) stair(x, yBase, lz, stairBlock, faceDir);
    }

    /** 沿 Z 軸排列的沙發長椅。 */
    public void sofaZ(int lx, int yBase, int z0, int z1, Block stairBlock, Direction faceDir) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) stair(lx, yBase, z, stairBlock, faceDir);
    }

    /** 嵌入式天花板照明網格。 */
    public void ceilingLights(int x0, int x1, int yCeiling, int z0, int z1, int stepX, int stepZ, Block lightBlock) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int x = minX; x <= maxX; x += Math.max(1, stepX)) {
            for (int z = minZ; z <= maxZ; z += Math.max(1, stepZ)) {
                set(x, yCeiling, z, lightBlock);
            }
        }
    }

    /** 停放車輛掩體（3×5 或 5×3：黑石輪胎＋彩色車身＋青色玻璃窗＋半磚車頂）。 */
    public void parkedCar(int cx, int yBase, int cz, Block bodyColor, boolean alongZ) {
        if (alongZ) {
            set(cx - 1, yBase, cz - 1, Blocks.BLACK_CONCRETE);
            set(cx + 1, yBase, cz - 1, Blocks.BLACK_CONCRETE);
            set(cx - 1, yBase, cz + 1, Blocks.BLACK_CONCRETE);
            set(cx + 1, yBase, cz + 1, Blocks.BLACK_CONCRETE);
            box(cx - 1, yBase, cz - 2, cx + 1, yBase, cz + 2, bodyColor);
            set(cx - 1, yBase, cz - 1, Blocks.BLACK_CONCRETE);
            set(cx + 1, yBase, cz - 1, Blocks.BLACK_CONCRETE);
            set(cx - 1, yBase, cz + 1, Blocks.BLACK_CONCRETE);
            set(cx + 1, yBase, cz + 1, Blocks.BLACK_CONCRETE);
            box(cx - 1, yBase + 1, cz - 1, cx + 1, yBase + 1, cz + 1, Blocks.LIGHT_GRAY_STAINED_GLASS);
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    bottomSlab(x, yBase + 2, z, Blocks.SMOOTH_STONE_SLAB);
                }
            }
        } else {
            box(cx - 2, yBase, cz - 1, cx + 2, yBase, cz + 1, bodyColor);
            set(cx - 1, yBase, cz - 1, Blocks.BLACK_CONCRETE);
            set(cx - 1, yBase, cz + 1, Blocks.BLACK_CONCRETE);
            set(cx + 1, yBase, cz - 1, Blocks.BLACK_CONCRETE);
            set(cx + 1, yBase, cz + 1, Blocks.BLACK_CONCRETE);
            box(cx - 1, yBase + 1, cz - 1, cx + 1, yBase + 1, cz + 1, Blocks.LIGHT_GRAY_STAINED_GLASS);
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    bottomSlab(x, yBase + 2, z, Blocks.SMOOTH_STONE_SLAB);
                }
            }
        }
    }

    /** 規則結構柱陣掩體。 */
    public void columnGrid(int x0, int x1, int y0, int y1, int z0, int z1, int stepX, int stepZ, Block pillarBlock) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int x = minX; x <= maxX; x += Math.max(1, stepX)) {
            for (int z = minZ; z <= maxZ; z += Math.max(1, stepZ)) {
                pillar(x, y0, y1, z, pillarBlock);
            }
        }
    }

    /** L 型 1.5 格蹲射戰術掩體。 */
    public void bunkerCoverL(int lx, int yBase, int lz, int dx, int dz, Block base, Block topSlab) {
        int xEnd = lx + dx;
        int zEnd = lz + dz;
        tallCover(Math.min(lx, xEnd), lz, Math.max(lx, xEnd), lz, yBase, base, topSlab);
        tallCover(lx, Math.min(lz, zEnd), lx, Math.max(lz, zEnd), yBase, base, topSlab);
    }

    /** 直梯（facingDir = 梯子朝外面向的方向，例如 SOUTH 代表貼在內側北牆面、面向前方玩家）。 */
    public void ladder(int lx, int y0, int y1, int lz, Direction facingDir) {
        BlockState st = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facingDir);
        for (int y = y0; y <= y1; y++) set(lx, y, lz, st);
    }

    /** 本模組電梯方塊直井（每一層的地板放一顆電梯，站上去跳／蹲直接上下樓）。 */
    public void elevator(int lx, int lz, int... floorYs) {
        for (int y : floorYs) {
            set(lx, y, lz, PGBlocks.ELEVATOR.get());
            air(lx, y + 1, lz);
            air(lx, y + 2, lz);
        }
    }

    /** 和風翹角瓦頂（層層內縮，外圈出簷 1 格）。 */
    public void pagodaRoof(int x0, int yBase, int z0, int x1, int z1, int layers, Block stairBlock, Block capBlock) {
        int minX = Math.min(x0, x1) - 1, maxX = Math.max(x0, x1) + 1;
        int minZ = Math.min(z0, z1) - 1, maxZ = Math.max(z0, z1) + 1;
        for (int layer = 0; layer < layers; layer++) {
            int y = yBase + layer;
            int lx0 = minX + layer, lx1 = maxX - layer;
            int lz0 = minZ + layer, lz1 = maxZ - layer;
            if (lx0 > lx1 || lz0 > lz1) break;
            if (layer == layers - 1 || lx1 - lx0 <= 1 || lz1 - lz0 <= 1) {
                box(lx0, y, lz0, lx1, y, lz1, capBlock);
                break;
            }
            for (int x = lx0; x <= lx1; x++) {
                stair(x, y, lz0, stairBlock, Direction.NORTH);
                stair(x, y, lz1, stairBlock, Direction.SOUTH);
            }
            for (int z = lz0 + 1; z < lz1; z++) {
                stair(lx0, y, z, stairBlock, Direction.EAST);
                stair(lx1, y, z, stairBlock, Direction.WEST);
            }
            set(lx0, y, lz0, capBlock);
            set(lx1, y, lz0, capBlock);
            set(lx0, y, lz1, capBlock);
            set(lx1, y, lz1, capBlock);
            if (lx0 + 1 <= lx1 - 1 && lz0 + 1 <= lz1 - 1) {
                box(lx0 + 1, y, lz0 + 1, lx1 - 1, y, lz1 - 1, capBlock);
            }
        }
    }

    /** 雙坡人字斜屋頂（沿 Z 方向延伸，左右兩側斜坡）。 */
    public void gableRoofZ(int x0, int yBase, int z0, int x1, int z1, Block stairBlock, Block fillBlock) {
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        int half = (maxX - minX) / 2;
        for (int step = 0; step <= half; step++) {
            int y = yBase + step;
            int lx = minX + step, rx = maxX - step;
            for (int z = minZ; z <= maxZ; z++) {
                if (lx == rx) {
                    set(lx, y, z, fillBlock);
                } else {
                    stair(lx, y, z, stairBlock, Direction.EAST);
                    stair(rx, y, z, stairBlock, Direction.WEST);
                }
            }
            if (lx + 1 <= rx - 1) {
                for (int x = lx + 1; x <= rx - 1; x++) {
                    set(x, y, minZ, fillBlock);
                    set(x, y, maxZ, fillBlock);
                }
            }
        }
    }

    /** 吊掛／落地燈籠。 */
    public void lantern(int lx, int ly, int lz, boolean hanging) {
        set(lx, ly, lz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging));
    }

    /** 登記一個物資箱生成點（自動確保該格與上方一格為空氣，避免卡方塊）。 */
    public void loot(int lx, int ly, int lz) {
        air(lx, ly, lz);
        air(lx, ly + 1, lz);
        lootPoints.add(toWorld(lx, ly, lz));
    }

    /** 更新周邊形狀連接（兩輪 updateFromNeighbourShapes 確保玻璃片、石牆、鐵欄杆、柵欄、樓梯轉角正確相連）。 */
    public void finalizeShapes() {
        for (BlockPos pos : previous.keySet()) {
            BlockState cur = level.getBlockState(pos);
            if (!cur.isAir()) {
                BlockState updated = Block.updateFromNeighbourShapes(cur, level, pos);
                if (!updated.isAir() && updated != cur) {
                    level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        List<BlockPos> descY = new ArrayList<>(previous.keySet());
        descY.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        for (BlockPos pos : descY) {
            BlockState cur = level.getBlockState(pos);
            if (!cur.isAir()) {
                BlockState updated = Block.updateFromNeighbourShapes(cur, level, pos);
                if (!updated.isAir() && updated != cur) {
                    level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        for (BlockPos pos : previous.keySet()) {
            BlockState st = level.getBlockState(pos);
            level.sendBlockUpdated(pos, st, st, Block.UPDATE_ALL);
        }
    }

    public Map<BlockPos, BlockState> previousBlocks() { return previous; }
    public Set<BlockPos> lootPoints() { return lootPoints; }
    public ServerLevel level() { return level; }
    public BlockPos origin() { return origin; }
    public Direction facing() { return facing; }
}
