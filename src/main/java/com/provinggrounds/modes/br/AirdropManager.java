package com.provinggrounds.modes.br;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.block.CrateBlockEntity;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.data.AirdropLedger;
import com.provinggrounds.registry.PGBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 檔案: AirdropManager.java
 * 目的: BR 空投（Phase 1c）。每局一個實例，由 BattleRoyaleMode 在 ACTIVE 階段每 tick 呼叫。
 *
 * 規則（簡報）：
 *   時間窗：第 2 階段縮圈結束 → 最後一階段縮圈結束。
 *   數量：開窗時決定掉 1 或 2 個（two_chance），時間在窗內隨機；兩個之間至少間隔 min_gap_seconds，
 *         且同一時間只會有一個在掉落。
 *   落點：當前圈內隨機（圈半徑 × radius_factor 以內）。
 *   落下：從 spawn_height 緩降、發光，落地變成空投箱（br:crates/airdrop），全服廣播座標。
 *
 * 時間窗長度用「下限」估算：之後每個階段 = 等待（間隔）+ 縮圈（至少一個間隔），
 * 所以排定的時間一定落在真正的時間窗內。
 *
 * 掉落中的箱子是原版方塊展示實體（block_display，只有外觀、沒有物理），伺服器每 tick 移動它。
 * 掉落期間強制載入該區塊，避免附近沒人時區塊卸載。落點記在 AirdropLedger，結束時清除。
 */
public class AirdropManager {

    /** 掉落中展示實體的標籤（清理用）。 */
    public static final String ENTITY_TAG = "pg_airdrop";

    private static final class Drop {
        final int x, z;
        double y;
        BlockPos target;
        @Nullable Entity display;

        Drop(int x, double y, int z, BlockPos target) {
            this.x = x; this.y = y; this.z = z; this.target = target;
        }
    }

    private final ServerLevel level;
    private final RandomSource random;
    /** 排定的投放時間（開窗後第幾 tick），由小到大。 */
    private final Deque<Integer> schedule = new ArrayDeque<>();
    private boolean windowOpened = false;
    private int windowTicks = 0;
    @Nullable private Drop falling;
    /** 信號槍召喚的空投（不計入每場空投上限，可以和一般空投同時掉）。 */
    private final List<Drop> flares = new ArrayList<>();

    public AirdropManager(ServerLevel level) {
        this.level = level;
        this.random = level.getRandom();
    }

    public boolean isFalling() { return falling != null; }

    /** 地圖標記用：已落地的空投箱位置（falling = false）與正在掉的空投落點（falling = true）。 */
    public record MapPoint(int x, int z, boolean falling) {}

    public List<MapPoint> mapPoints() {
        List<MapPoint> list = new ArrayList<>();
        for (BlockPos pos : AirdropLedger.get(level.getServer()).entries()) {
            if (falling != null && pos.equals(falling.target)) continue;
            if (flares.stream().anyMatch(f -> pos.equals(f.target))) continue;
            list.add(new MapPoint(pos.getX(), pos.getZ(), false));
        }
        if (falling != null) list.add(new MapPoint(falling.x, falling.z, true));
        for (Drop f : flares) list.add(new MapPoint(f.x, f.z, true));
        return list;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 時間窗
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * 開啟時間窗（第 2 階段縮圈結束時呼叫一次）。
     * @param remainingStages 開窗後還剩幾個階段（總階段數 − 2）
     * @param intervalSeconds 階段間隔秒數
     */
    public void openWindow(int remainingStages, int intervalSeconds) {
        if (windowOpened || remainingStages <= 0) return;
        windowOpened = true;
        windowTicks = 0;

        PGConfig c = PGConfig.SERVER;
        int length = remainingStages * 2 * intervalSeconds * 20; // 時間窗長度下限
        int gap = c.brAirdropMinGapSeconds.get() * 20;
        int count = random.nextDouble() < c.brAirdropTwoChance.get() ? 2 : 1;
        if (count == 2 && length <= gap) count = 1; // 窗太短放不下兩個

        if (count == 1) {
            schedule.add(random.nextInt(Math.max(1, length)));
        } else {
            // 兩個時間在 [0, 長度 − 間隔] 均勻抽，排序後第二個往後推一個間隔 → 間隔一定足夠
            int span = Math.max(1, length - gap);
            int a = random.nextInt(span), b = random.nextInt(span);
            schedule.add(Math.min(a, b));
            schedule.add(Math.max(a, b) + gap);
        }
        ProvingGroundsMod.LOGGER.info("Airdrop window opened: length={}s count={} times={}",
            length / 20, count, schedule.stream().map(t -> t / 20 + "s").toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 每 tick
    // ═════════════════════════════════════════════════════════════════════════

    public void tick(Circle circle) {
        if (falling != null) tickFalling();
        tickFlares();
        if (!windowOpened) return;
        windowTicks++;
        // 時間到、且目前沒有空投在掉 → 投下（確保一次只掉一個）
        if (circle.inFinal() || circle.finished()) { schedule.clear(); return; } // 決賽圈不再掉空投（計畫書 7 B）
        if (falling == null && !schedule.isEmpty() && windowTicks >= schedule.peek()) {
            schedule.poll();
            spawn(circle.centerX(), circle.centerZ(), circle.radius());
        }
    }

    /** 管理員測試用：立刻投下一個（不影響排定的空投）。已有空投在掉落時回傳 false。 */
    public boolean forceDrop(Circle circle) {
        if (falling != null) return false;
        spawn(circle.centerX(), circle.centerZ(), circle.radius());
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 投下 → 掉落 → 落地
    // ═════════════════════════════════════════════════════════════════════════

    private void spawn(double cx, double cz, double radius) {
        PGConfig c = PGConfig.SERVER;
        double maxDist = Math.max(0, radius * c.brAirdropRadiusFactor.get());
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(random.nextDouble()) * maxDist; // 圓內均勻分布
        int x = Mth.floor(cx + Math.cos(angle) * dist);
        int z = Mth.floor(cz + Math.sin(angle) * dist);

        level.setChunkForced(x >> 4, z >> 4, true); // 掉落期間保持區塊載入
        BlockPos target = groundAt(x, z);
        int startY = Math.max(target.getY() + 1, Math.min(c.brAirdropHeight.get(), level.getMaxBuildHeight() - 2));
        AirdropLedger.get(level.getServer()).add(target);

        Drop d = new Drop(x, startY, z, target);
        d.display = createDisplay(x, startY, z);
        falling = d;

        broadcast(Component.literal("[空投] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal(String.format("空投投下！座標：X=%d  Z=%d", x, z)).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("  正在掉落中...").withStyle(ChatFormatting.GRAY)));
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1f, 1.5f);
        }
        ProvingGroundsMod.LOGGER.info("Airdrop spawned at {} {} {} (target y={})", x, startY, z, target.getY());
    }

    /**
     * 信號槍（計畫書 4 的 18 號）：在玩家的 x、z 召喚一個空投，不計入每場空投上限，並通知全體玩家。
     * 空投位置照常顯示在地圖上。
     */
    public void flareDrop(net.minecraft.server.level.ServerPlayer p) {
        PGConfig c = PGConfig.SERVER;
        int x = Mth.floor(p.getX()), z = Mth.floor(p.getZ());
        level.setChunkForced(x >> 4, z >> 4, true);
        BlockPos target = groundAt(x, z);
        int startY = Math.max(target.getY() + 1, Math.min(c.brAirdropHeight.get(), level.getMaxBuildHeight() - 2));
        AirdropLedger.get(level.getServer()).add(target);
        Drop d = new Drop(x, startY, z, target);
        d.display = createDisplay(x, startY, z);
        flares.add(d);
        broadcast(Component.literal("[空投] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal(String.format("有人發射了信號槍，正在召喚空投！座標：X=%d  Z=%d", x, z)).withStyle(ChatFormatting.YELLOW)));
        for (net.minecraft.server.level.ServerPlayer o : level.getServer().getPlayerList().getPlayers()) {
            o.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1f, 1.2f);
        }
        level.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 2, p.getZ(), 40, 0.2, 3, 0.2, 0.05);
    }

    private void tickFlares() {
        for (java.util.Iterator<Drop> it = flares.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            d.y -= PGConfig.SERVER.brAirdropFallSpeed.get() / 20.0;
            if (d.y <= d.target.getY()) {
                it.remove();
                land(d);
                continue;
            }
            if (d.display != null && d.display.isAlive()) d.display.moveTo(d.x, d.y, d.z, 0, 0);
            if (level.getGameTime() % 2 == 0) level.sendParticles(ParticleTypes.CLOUD, d.x + 0.5, d.y + 1.1, d.z + 0.5, 1, 0.15, 0.05, 0.15, 0.0);
        }
    }

    private void tickFalling() {
        Drop d = falling;
        if (d == null) return;
        d.y -= PGConfig.SERVER.brAirdropFallSpeed.get() / 20.0;
        if (d.y <= d.target.getY()) {
            land(d);
            return;
        }
        if (d.display != null && d.display.isAlive()) {
            d.display.moveTo(d.x, d.y, d.z, 0, 0);
        }
        if (level.getGameTime() % 2 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, d.x + 0.5, d.y + 1.1, d.z + 0.5, 1, 0.15, 0.05, 0.15, 0.0);
        }
    }

    private void land(Drop d) {
        if (falling == d) falling = null;
        if (d.display != null) d.display.discard();

        // 掉落期間地形可能改變，重新找一次落點
        BlockPos pos = groundAt(d.x, d.z);
        AirdropLedger ledger = AirdropLedger.get(level.getServer());
        ledger.remove(d.target);
        ledger.add(pos);

        level.setBlock(pos, PGBlocks.CRATE_AIRDROP.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CrateBlockEntity crate) {
            crate.setLootTable(LootSpawner.TABLE_AIRDROP, random.nextLong());
        }
        level.setChunkForced(d.x >> 4, d.z >> 4, false);

        // 落地特效（沿用舊版：爆炸粒子 + 狼煙 + 爆炸音效）
        double px = pos.getX() + 0.5, py = pos.getY() + 0.5, pz = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, px, py, pz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, px, py, pz, 30, 0.3, 1, 0.3, 0.01);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.MASTER, 0.5f, 1.2f);

        broadcast(Component.literal("[空投] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal(String.format("空投已落地！座標：X=%d  Z=%d", pos.getX(), pos.getZ()))
                .withStyle(ChatFormatting.YELLOW)));
        ProvingGroundsMod.LOGGER.info("Airdrop landed at {}", pos.toShortString());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 清理（遊戲開始、結束、強制停止時）
    // ═════════════════════════════════════════════════════════════════════════

    /** 照 AirdropLedger 清掉所有空投箱與掉落中的展示實體，並解除強制載入。 */
    public static void cleanupAll(ServerLevel level) {
        AirdropLedger ledger = AirdropLedger.get(level.getServer());
        int count = 0;
        for (BlockPos pos : new ArrayList<>(ledger.entries())) {
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (level.getBlockState(pos).is(PGBlocks.CRATE_AIRDROP.get())) {
                if (level.getBlockEntity(pos) instanceof CrateBlockEntity crate) crate.wipe();
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            AABB column = new AABB(pos.getX() - 1, level.getMinBuildHeight(), pos.getZ() - 1,
                                   pos.getX() + 2, level.getMaxBuildHeight(), pos.getZ() + 2);
            for (Entity e : level.getEntities((Entity) null, column, e -> e.getTags().contains(ENTITY_TAG))) {
                e.discard();
            }
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
            count++;
        }
        ledger.clear();
        if (count > 0) ProvingGroundsMod.LOGGER.info("Airdrop cleanup: {} removed.", count);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 小工具
    // ═════════════════════════════════════════════════════════════════════════

    /** 找 (x, z) 的落點：地表最高的實心方塊上方第一個可取代的位置（樹葉上也算）。 */
    private BlockPos groundAt(int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, Math.max(y, level.getMinBuildHeight()), z);
        while (!level.getBlockState(p).canBeReplaced() && p.getY() < level.getMaxBuildHeight() - 2) p.move(0, 1, 0);
        return p.immutable();
    }

    /** 建立發光的空投箱展示實體（和 /summon block_display 相同的方式）。 */
    @Nullable
    private Entity createDisplay(int x, double y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(PGBlocks.CRATE_AIRDROP.get().defaultBlockState()));
        tag.putBoolean("Glowing", true);
        tag.putInt("glow_color_override", 0xFFD040);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(ENTITY_TAG));
        tag.put("Tags", tags);
        Entity e = EntityType.loadEntityRecursive(tag, level, ent -> {
            ent.moveTo(x, y, z, 0, 0);
            return ent;
        });
        if (e == null || !level.addFreshEntity(e)) {
            ProvingGroundsMod.LOGGER.warn("Airdrop display entity could not be created.");
            return null;
        }
        return e;
    }

    private void broadcast(Component msg) {
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }
}
