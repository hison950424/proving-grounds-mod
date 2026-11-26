package com.provinggrounds.modes.arena;

import com.provinggrounds.config.PGConfig;
import com.provinggrounds.core.DownedCore;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.ProneManager;
import com.provinggrounds.network.KillNoticePacket;
import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.special.SpecialRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 檔案: ArenaDowned.java
 * 目的: 團隊模式（團隊死鬥、佔點、槍王之王）的倒地（計畫書 7 E；大廳「倒地」開關，預設關）。
 *   共用部分在 core/DownedCore（倒地效果、解除、準心判斷）、ActionLock／BRDowned（不能用物品、不能回血、趴下）。
 *   規則：
 *     - 致命傷時，同隊還有至少一位沒倒地的存活隊友 → 倒地；否則直接死亡（槍王之王的孤狼沒有隊友 → 直接死亡）。
 *     - 倒地：流血（每秒扣 br_bleed_per_second），扣到 0 就真的死亡；倒地中再受致命傷也真的死亡。
 *     - 扶起：同隊用 F（準心對著倒地隊友），讀條 br_revive_seconds 秒，準心離開或離太遠就中斷；扶起後血量 br_revive_health。
 *     - 擊殺紀錄：倒地時顯示「擊倒」；最終死亡時才計擊殺、金錢、隊伍分數、槍王晉級（照一般死亡流程，擊殺算在擊倒的人）。
 *     - 佔點：倒地算存活，但全隊都倒地或死亡 = 全滅，倒地的人立刻全部死亡。
 *   客戶端知道自己倒地：倒地期間身上有「倒地」效果（原版會把自己的效果同步給自己）；別人看到趴下：ProneManager 的同步名單。
 */
public final class ArenaDowned {

    private static final class State {
        int bleedTicks = 20;
        @Nullable UUID downedBy;
        @Nullable UUID reviver;
        int reviveTicks;
    }

    private final ArenaMode mode;
    private final Map<UUID, State> downed = new HashMap<>();
    /** 扶人的意圖：扶起者 → 倒地隊友（按 F 開始）。 */
    private final Map<UUID, UUID> intents = new HashMap<>();
    /** 正在處理最終死亡的人（不要再倒地一次）。 */
    private final Set<UUID> finishing = new HashSet<>();

    ArenaDowned(ArenaMode mode) { this.mode = mode; }

    public boolean isDowned(UUID id) { return downed.containsKey(id); }

    public Set<UUID> ids() { return downed.keySet(); }

    private static PGConfig c() { return PGConfig.SERVER; }

    /** 有沒有可以扶自己的隊友（同隊、在線、活著、沒倒地、不是旁觀）。 */
    private boolean hasStandingMate(ArenaMode.ArenaPlayer v) {
        for (ServerPlayer p : mode.online()) {
            ArenaMode.ArenaPlayer ap = mode.player(p.getUUID());
            if (ap == null || ap == v || !mode.sameSide(ap, v)) continue;
            if (p.isSpectator() || !p.isAlive() || downed.containsKey(p.getUUID())) continue;
            if (mode.kind() == GameManager.GameMode.DOM && !ap.alive) continue;
            return true;
        }
        return false;
    }

    /** 致命傷（ArenaManager 在死亡事件最前面呼叫）。回傳 true = 改成倒地，取消這次死亡。 */
    public boolean onLethal(ServerPlayer victim, DamageSource source) {
        ArenaMode.ArenaPlayer v = mode.player(victim.getUUID());
        if (v == null || finishing.contains(victim.getUUID()) || downed.containsKey(victim.getUUID())) return false;
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return false;
        if (!hasStandingMate(v)) return false;
        State s = new State();
        ServerPlayer attacker = source.getEntity() instanceof ServerPlayer a && a != victim ? a : null;
        s.downedBy = attacker != null ? attacker.getUUID() : null;
        downed.put(victim.getUUID(), s);
        DownedCore.applyDowned(victim, c().brDownedHealth.get().floatValue());
        mark(victim);
        ProneManager.markDirty();
        // 擊殺紀錄：「擊倒」（最終死亡時才算擊殺）
        ArenaMode.ArenaPlayer k = attacker != null ? mode.player(attacker.getUUID()) : null;
        String kName = attacker != null ? attacker.getGameProfile().getName() : "";
        ItemStack weapon = attacker != null ? attacker.getMainHandItem().copyWithCount(1) : ItemStack.EMPTY;
        for (ServerPlayer p : mode.online()) {
            PGNetwork.sendTo(p, new KillNoticePacket(true, false, kName, k != null ? mode.colorOf(k) : 0xFFFFFF, v.name, mode.colorOf(v),
                weapon, KillNoticePacket.KNOCK, 0));
        }
        victim.displayClientMessage(Component.literal("你倒地了！等待隊友按 F 扶起").withStyle(ChatFormatting.RED), true);
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
        return true;
    }

    /** 「倒地」效果：讓客戶端知道自己倒地（擋輸入、顯示圖示）。 */
    private static void mark(ServerPlayer p) {
        if (!p.hasEffect(SpecialRegistry.DOWNED_MARK.get())) {
            p.addEffect(new MobEffectInstance(SpecialRegistry.DOWNED_MARK.get(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        }
    }

    private void clear(@Nullable ServerPlayer p, UUID id) {
        downed.remove(id);
        intents.values().removeIf(id::equals);
        ProneManager.markDirty();
        if (p != null) {
            DownedCore.clearDowned(p);
            p.removeEffect(SpecialRegistry.DOWNED_MARK.get());
        }
    }

    /** 真的死亡：用「擊倒的人」當擊殺者走一般死亡流程（擊殺、金錢、分數在這時候才算）。 */
    public void finalKill(ServerPlayer p) {
        State s = downed.get(p.getUUID());
        clear(p, p.getUUID());
        ServerPlayer by = s != null && s.downedBy != null ? mode.server.getPlayerList().getPlayer(s.downedBy) : null;
        var holder = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC_KILL);
        DamageSource src = by != null ? new DamageSource(holder, by) : new DamageSource(holder);
        finishing.add(p.getUUID());
        try {
            p.hurt(src, Float.MAX_VALUE);
            if (p.isAlive()) p.kill(); // 萬一被其他效果擋下
        } finally {
            finishing.remove(p.getUUID());
        }
    }

    /** 按 F：準心對著範圍內的倒地隊友就開始扶。 */
    public boolean startReviveByKey(ServerPlayer helper, @Nullable Entity looked) {
        if (!(looked instanceof ServerPlayer dp) || !downed.containsKey(dp.getUUID()) || downed.containsKey(helper.getUUID())) return false;
        ArenaMode.ArenaPlayer h = mode.player(helper.getUUID()), d = mode.player(dp.getUUID());
        if (h == null || d == null || !mode.sameSide(h, d)) return false;
        if (helper.distanceTo(dp) > c().brReviveRange.get()) return false;
        intents.put(helper.getUUID(), dp.getUUID());
        return true;
    }

    public void tick() {
        PGConfig cfg = c();
        int baseTotal = Math.max(1, (int) Math.round(cfg.arena.arenaReviveSeconds.get() * 20.0));
        double range = cfg.brReviveRange.get();
        Set<UUID> busy = new HashSet<>();
        for (UUID id : new ArrayList<>(downed.keySet())) {
            State s = downed.get(id);
            ServerPlayer dp = mode.server.getPlayerList().getPlayer(id);
            if (s == null) continue;
            if (dp == null || !dp.isAlive() || dp.isSpectator()) { clear(dp, id); continue; }
            mark(dp);
            ServerPlayer helper = findHelper(dp, range, busy);
            if (helper != null) {
                busy.add(helper.getUUID());
                if (!helper.getUUID().equals(s.reviver)) { s.reviver = helper.getUUID(); s.reviveTicks = 0; }
                int total = reviveTicksFor(helper, baseTotal);
                s.reviveTicks++; // 扶的期間流血暫停
                int pct = Math.min(100, s.reviveTicks * 100 / total);
                if (s.reviveTicks % 5 == 0) {
                    helper.displayClientMessage(Component.literal("扶起中 " + pct + "%").withStyle(ChatFormatting.GREEN), true);
                    dp.displayClientMessage(Component.literal("隊友扶起中 " + pct + "%").withStyle(ChatFormatting.GREEN), true);
                }
                if (s.reviveTicks >= total) revive(dp, helper);
                continue;
            }
            s.reviver = null;
            s.reviveTicks = 0;
            if (--s.bleedTicks <= 0) {
                s.bleedTicks = 20;
                float hp = dp.getHealth() - cfg.brBleedPerSecond.get().floatValue();
                if (hp <= 0) { finalKill(dp); continue; }
                dp.setHealth(hp);
                int left = (int) Math.ceil(hp / cfg.brBleedPerSecond.get());
                dp.displayClientMessage(Component.literal("倒地中：" + left + " 秒內要被隊友扶起").withStyle(ChatFormatting.RED), true);
            }
        }
        if (mode.kind() == GameManager.GameMode.DOM && mode.ticks % 10 == 0) checkWipes();
    }

    @Nullable
    private ServerPlayer findHelper(ServerPlayer dp, double range, Set<UUID> busy) {
        for (Map.Entry<UUID, UUID> e : new ArrayList<>(intents.entrySet())) {
            if (!e.getValue().equals(dp.getUUID()) || busy.contains(e.getKey())) continue;
            ServerPlayer hp = mode.server.getPlayerList().getPlayer(e.getKey());
            if (hp == null || !hp.isAlive() || hp.isSpectator() || downed.containsKey(hp.getUUID())) { intents.remove(e.getKey()); continue; }
            if (hp.distanceTo(dp) > range) {
                intents.remove(e.getKey());
                hp.displayClientMessage(Component.literal("離太遠，扶起中斷").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            if (!DownedCore.aimsAtEntity(hp, dp, range)) { // 準心離開就中斷（受傷、移動不中斷）
                intents.remove(e.getKey());
                hp.displayClientMessage(Component.literal("準心離開隊友，扶起中斷").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            return hp;
        }
        return null;
    }

    private void revive(ServerPlayer dp, ServerPlayer helper) {
        clear(dp, dp.getUUID());
        intents.remove(helper.getUUID());
        dp.setHealth((float) Math.min(c().brReviveHealth.get(), dp.getMaxHealth()));
        dp.displayClientMessage(Component.literal(helper.getGameProfile().getName() + " 把你扶起來了").withStyle(ChatFormatting.GREEN), true);
        helper.displayClientMessage(Component.literal("已扶起 " + dp.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
        dp.level().playSound(null, dp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** 佔點：某一隊沒有站著的人（都倒地或死亡）→ 倒地的人全部死亡。 */
    private void checkWipes() {
        List<ServerPlayer> toKill = new ArrayList<>();
        for (UUID id : downed.keySet()) {
            ServerPlayer p = mode.server.getPlayerList().getPlayer(id);
            ArenaMode.ArenaPlayer ap = mode.player(id);
            if (p != null && ap != null && !hasStandingMate(ap)) toKill.add(p);
        }
        for (ServerPlayer p : toKill) if (downed.containsKey(p.getUUID())) finalKill(p);
    }

    /** 扶起所需 tick：醫護兵只需 2 秒（40 tick），其他職業照 arena.revive_seconds_v1（5.5 秒 = 110 tick）。 */
    private int reviveTicksFor(ServerPlayer helper, int baseTotal) {
        ArenaMode.ArenaPlayer hp = mode.player(helper.getUUID());
        if (hp != null && mode.usesClasses() && hp.cls.fastReviveTicks() > 0) return hp.cls.fastReviveTicks();
        return baseTotal;
    }

    /** 比賽結束、模式停止：全部解除。 */
    public void clearAll() {
        for (UUID id : new ArrayList<>(downed.keySet())) clear(mode.server.getPlayerList().getPlayer(id), id);
        intents.clear();
    }

    /** 登出：解除倒地（回來時照一般重生）。 */
    public void onLogout(ServerPlayer p) {
        if (downed.containsKey(p.getUUID())) clear(p, p.getUUID());
        intents.remove(p.getUUID());
    }
}
