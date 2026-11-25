package com.provinggrounds.core;

import com.provinggrounds.config.PGConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 檔案: Wallbang.java
 * 目的: 計畫書 7 F：判斷一次擊殺是不是「穿牆擊殺」（伺服器端自己判斷，不依賴 TACZ）。
 *   從擊殺者眼睛到被擊殺者身體中心畫一條直線，只看方塊的碰撞箱（不看實體、液體）：
 *   路上有一個「不可穿透」的方塊 → 穿牆。可穿透方塊 = TACZ 的子彈忽略清單（方塊標籤 tacz:bullet_ignore）
 *   加上設定 wallbang_passable_blocks（玻璃、玻璃片、樹葉、鐵柵欄、柵欄、柵欄門、告示牌、地毯、雪片、蜘蛛網）。
 *   限制：這是近似判斷（直線，不是子彈真正的彈道；拋物線、跳彈、爆炸都不準）。
 */
public final class Wallbang {

    private Wallbang() {}

    private static final TagKey<Block> TACZ_IGNORE = TagKey.create(Registries.BLOCK, new ResourceLocation("tacz", "bullet_ignore"));

    private static List<? extends String> cachedFrom;
    private static final Set<Block> BLOCKS = new HashSet<>();
    private static final List<TagKey<Block>> TAGS = new ArrayList<>();

    /** 設定改了就重新整理清單（"minecraft:glass" 或 "#minecraft:leaves"）。 */
    private static void refresh() {
        List<? extends String> now;
        try { now = PGConfig.SERVER.wallbangPassable.get(); } catch (IllegalStateException e) { now = List.of(); }
        if (now == cachedFrom) return;
        cachedFrom = now;
        BLOCKS.clear();
        TAGS.clear();
        for (String s : now) {
            if (s.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(s.substring(1));
                if (id != null) TAGS.add(TagKey.create(Registries.BLOCK, id));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(s);
                Block b = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
                if (b != null && id.equals(ForgeRegistries.BLOCKS.getKey(b))) BLOCKS.add(b);
            }
        }
    }

    public static boolean passable(BlockState st) {
        refresh();
        if (st.is(TACZ_IGNORE) || BLOCKS.contains(st.getBlock())) return true;
        for (TagKey<Block> t : TAGS) if (st.is(t)) return true;
        return false;
    }

    /** 擊殺者與被擊殺者之間有不可穿透的方塊。 */
    public static boolean through(ServerPlayer killer, LivingEntity victim) {
        Level level = killer.level();
        if (level != victim.level()) return false;
        Vec3 from = killer.getEyePosition(), to = victim.getBoundingBox().getCenter();
        if (from.distanceToSqr(to) > 512 * 512) return false;
        Vec3 dir = to.subtract(from).normalize();
        Vec3 cur = from;
        for (int i = 0; i < 48; i++) {
            BlockHitResult hit = level.clip(new ClipContext(cur, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, killer));
            if (hit.getType() == HitResult.Type.MISS) return false;
            BlockPos pos = hit.getBlockPos();
            if (!passable(level.getBlockState(pos))) return true;
            // 可穿透：從這個方塊後面繼續找
            Vec3 next = hit.getLocation();
            int guard = 0;
            while (BlockPos.containing(next).equals(pos) && guard++ < 20) next = next.add(dir.scale(0.1));
            if (next.distanceToSqr(from) >= to.distanceToSqr(from)) return false;
            cur = next;
        }
        return false;
    }
}
