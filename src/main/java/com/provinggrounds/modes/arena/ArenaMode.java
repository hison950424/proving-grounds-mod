package com.provinggrounds.modes.arena;

import com.provinggrounds.ProvingGroundsMod;
import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.config.PGConfig;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.TeamManager;
import com.provinggrounds.data.PlayerDataManager;
import com.provinggrounds.modes.br.RespawnKit;
import com.provinggrounds.network.ArenaStatePacket;
import com.provinggrounds.network.PGNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 檔案: ArenaMode.java
 * 目的: 團隊死鬥、佔點、槍王之王共用的對戰框架（機制模組化：共用的事放這裡，規則放子類別）。
 *   - 參賽者名單、隊伍、職業、金錢（取代舊版的 gd656killicon 分數）、轉帳
 *   - UI 商店購買（只能在自己的基地）、基地範圍、防蹲點羊毛
 *   - 重生、補消耗品、無限彈藥箱、丟東西自動撿回
 *   - 每 10 tick 同步 HUD（{@link ArenaStatePacket}）、比賽結束與還原
 * 執行者：伺服器（ArenaManager 每 tick 呼叫 tick()）。
 */
public abstract class ArenaMode {

    public static final int RED = 1, BLUE = 2;
    private static final UUID HEALTH_MOD = UUID.fromString("2f6b1c84-9d3e-4a57-b0c2-71e5d8a9f314");
    private static final UUID CLASS_SPEED_MOD = UUID.fromString("8a4c2f91-7b3d-4e65-a12c-5d9e0f4b8c22");

    /** 一位參賽者的對戰資料。 */
    public static final class ArenaPlayer {
        public final UUID id;
        public final String name;
        public int team;           // 1 紅、2 藍；槍王之王用 TeamManager 的隊伍編號（0 = 孤狼）
        public String teamKey;     // 槍王之王的進度鍵（孤狼每人一個）
        public ArenaClass cls = ArenaClass.FREELANCER;
        public int money, kills, deaths;
        public long lastTransferTick = -100000;
        public int respawnKitTicks = -1;  // 重生後幾 tick 發裝備
        public boolean alive = true;       // 佔點：本回合還活著
        public int stillTicks;             // 哨兵：蹲著不動的 tick 數
        public int pointEarn;              // 佔點：本回合佔點收入
        @Nullable public Vec3 lastPos;
        // 計畫書 5
        @Nullable public Vec3 deathPos;     // 死亡位置（重生倒數時在這裡旁觀）
        public int respawnAt = -1;          // 倒數結束的 tick；-1 = 沒在倒數
        public int protectUntil;            // 重生保護到哪個 tick
        public int campTicks;               // 在敵方重生區裡待了幾 tick
        public int offlineSince = -1;       // 掉線的 tick；-1 = 在線
        public int streak;                  // 連殺數
        // 計畫書 9：九大職業計時與團隊死鬥特殊道具累計
        public int reconSec, supportSec;    // 偵察兵 20 秒掃描、支援兵 30 秒補投擲物
        public int tdmSpecialKills;         // 團隊死鬥累計擊殺（每 3 殺獲得 1 個特殊道具）
        public int tdmDryDeaths;            // 團隊死鬥連續死亡未獲特殊道具次數（滿 3 次保底 1 個）

        ArenaPlayer(UUID id, String name) { this.id = id; this.name = name; }
    }

    /** HUD 上的一行分數。 */
    public record ScoreLine(int color, String label, int value, int target, String extra) {}

    protected final MinecraftServer server;
    protected final ServerLevel level;
    protected final ArenaConfig cfg = PGConfig.SERVER.arena;
    protected final Map<UUID, ArenaPlayer> players = new LinkedHashMap<>();
    protected int ticks;
    private int endTicks = -1;
    /** 團隊模式倒地（計畫書 7 E）：開關在大廳設定，開局時設好。 */
    public boolean downedEnabled;
    public final ArenaDowned downed = new ArenaDowned(this);
    private boolean savedKeepInventory, savedImmediateRespawn;

    protected ArenaMode(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
    }

    // ── 子類別要提供的規則 ─────────────────────────────────────────────────

    public abstract GameManager.GameMode kind();
    public abstract String title();
    /** 開局（玩家已經加入、傳送前）。 */
    protected abstract void onStart();
    /** 每 tick（比賽進行中）。 */
    protected abstract void tickRules();
    /** 有人擊殺了別人（隊友誤殺、自殺不會呼叫）。 */
    protected abstract void onKill(ServerPlayer killer, ArenaPlayer k, ServerPlayer victim, ArenaPlayer v);
    /** 有人死了（先於 onKill）。 */
    protected void onDeath(ServerPlayer victim, ArenaPlayer v) {}
    /** 重生後（已傳送）：預設發職業消耗品。 */
    protected void onRespawned(ServerPlayer p, ArenaPlayer ap) {}
    /** 重生點。 */
    protected abstract Vec3 spawnOf(ArenaPlayer ap);
    /** HUD：標題下面的分數。 */
    protected abstract List<ScoreLine> scoreLines(ArenaPlayer viewer);
    /** HUD：計時（秒，-1 = 不顯示）與它的說明。 */
    protected int timerSeconds() { return -1; }
    protected String timerLabel() { return ""; }
    /** HUD：額外一行（例如佔點進度）。 */
    protected String statusLine(ArenaPlayer viewer) { return ""; }

    public boolean usesShop() { return true; }
    public boolean usesClasses() { return true; }
    protected boolean keepInventory() { return true; }
    /** 能不能丟東西（佔點可以丟槍給隊友）。 */
    protected boolean allowToss(ItemStack stack) { return false; }
    /** 金錢上限（0 = 沒有上限）。 */
    protected int moneyCap() { return 0; }
    /** 現在能不能買東西（另外還要在基地裡）。 */
    protected boolean shopOpen() { return true; }
    /** 開局前 10 秒選角／選特殊道具期間（團隊死鬥開局準備、佔點第一回合準備）。 */
    public boolean loadoutOpen() { return false; }

    // ── 開局 ────────────────────────────────────────────────────────────────

    /** 開局。回傳錯誤訊息，成功回傳 null。 */
    @Nullable
    public final String start(List<ServerPlayer> participants) {
        if (participants.isEmpty()) return "沒有參賽者。";
        assignTeams(participants);
        GameRules rules = server.getGameRules();
        savedKeepInventory = rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
        savedImmediateRespawn = rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(keepInventory(), server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, server);
        GameManager.get().startGame(kind());
        TeamManager.get().syncVanillaTeams(server, participants);
        com.provinggrounds.special.SpecialWorld.cleanup(server); // 上一場留下的特殊道具效果（地雷、煙霧、領域…）先清掉
        beginBlockLedger();
        for (ServerPlayer p : participants) {
            ArenaPlayer ap = players.get(p.getUUID());
            GameManager.get().addPlayer(p);
            com.provinggrounds.venue.VenueManager.leave(p);
            com.provinggrounds.special.KnockbackShield.restore(p);
            ap.money = cfg.startMoney.get();
            if (p.isPassenger()) p.stopRiding();
            p.getInventory().clearContent();
            com.provinggrounds.special.SpecialSlot.clear(p); // 大廳留下來的特殊物品不帶進比賽
            p.removeAllEffects();
            p.setGameMode(GameType.ADVENTURE);
            p.setCamera(p);
        }
        onStart();
        for (ServerPlayer p : participants) {
            ArenaPlayer ap = players.get(p.getUUID());
            prepare(p, ap, true);
            teleport(p, spawnOf(ap));
            giveStartSpecial(p);
        }
        syncHud(); // 立即同步一次，讓開局 10 秒選角介面馬上彈出
        broadcastTitle(Component.literal(title()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.literal(loadoutOpen() ? "準備期間 10 秒：請選擇職業" + (com.provinggrounds.special.SpecialConfig.enabledSafe() ? "與特殊道具" : "")
                : (usesShop() ? "在基地裡按 M 打開商店" : "擊殺敵人升級武器")).withStyle(ChatFormatting.YELLOW), 60);
        playAll(SoundEvents.WITHER_SPAWN, 0.5f, 0.5f);
        ProvingGroundsMod.LOGGER.info("Arena {} started with {} players", kind(), participants.size());
        return null;
    }

    /** 分隊：預設紅藍兩隊（已在紅藍隊的人不動，其他人補到人少的那隊）。槍王之王覆寫。 */
    protected void assignTeams(List<ServerPlayer> participants) {
        TeamManager tm = TeamManager.get();
        List<ServerPlayer> rest = new ArrayList<>();
        int red = 0, blue = 0;
        for (ServerPlayer p : participants) {
            ArenaPlayer ap = new ArenaPlayer(p.getUUID(), p.getGameProfile().getName());
            if (usesClasses()) ap.cls = com.provinggrounds.core.LobbyManager.get().chosenClass(p.getUUID()); // 大廳預選的職業（計畫書 6 C1）
            players.put(p.getUUID(), ap);
            int t = tm.getTeam(p).map(x -> x.id).orElse(-1);
            if (t == RED) red++;
            else if (t == BLUE) blue++;
            else rest.add(p);
        }
        Collections.shuffle(rest);
        for (ServerPlayer p : rest) {
            int t = red <= blue ? RED : BLUE;
            if (t == RED) red++; else blue++;
            tm.assignTeam(p, TeamManager.Team.of(t));
        }
        for (ServerPlayer p : participants) {
            ArenaPlayer ap = players.get(p.getUUID());
            ap.team = tm.getTeam(p).map(x -> x.id).orElse(RED);
            ap.teamKey = "team:" + ap.team;
        }
    }

    /** 職業屬性、起始槍（first 或換職業時）、消耗品、彈藥箱、補血。 */
    protected void prepare(ServerPlayer p, ArenaPlayer ap, boolean giveGun) {
        applyHealth(p, usesClasses() ? ap.cls.maxHealth : 100);
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 1, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        if (usesClasses()) {
            if (giveGun) {
                ItemStack gun = GunCompat.createLoadedGun(new net.minecraft.resources.ResourceLocation(ap.cls.gun), ap.cls.fireMode);
                give(p, gun);
            }
            refillConsumables(p, ap, false);
        }
        ensureOne(p, cfg.ammoBox.get());
        p.setHealth(p.getMaxHealth());
    }

    protected static void applyHealth(ServerPlayer p, double max) {
        AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(HEALTH_MOD);
        double extra = max - attr.getBaseValue();
        if (extra != 0) attr.addTransientModifier(new AttributeModifier(HEALTH_MOD, "pg_arena_health", extra, AttributeModifier.Operation.ADDITION));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** 職業的固有消耗品：沒有就補 1 個；limit = 超過 1 個時收回多的（佔點）。 */
    protected void refillConsumables(ServerPlayer p, ArenaPlayer ap, boolean limit) {
        for (String spec : ap.cls.consumables) {
            ItemStack template = RespawnKit.parse(spec);
            if (template.isEmpty()) continue;
            int have = count(p, template);
            if (have == 0) give(p, template.copy());
            else if (limit && have > 1) remove(p, template, have - 1);
        }
    }

    // ── 每 tick ─────────────────────────────────────────────────────────────

    public final void tick() {
        ticks++;
        if (endTicks >= 0) {
            if (--endTicks <= 0) finish();
            else if (ticks % 10 == 0) syncHud();
            return;
        }
        if (GameManager.get().getState() == GameManager.GameState.END) { // 參賽者全部離線
            endMatch(Component.literal("沒有參賽者了"), Set.of());
            return;
        }
        if (ticks % 20 == 0) removeTimedOut();
        if (downedEnabled) downed.tick();
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            if (ap.respawnAt > 0) {
                if (ticks >= ap.respawnAt) { ap.respawnAt = -1; doRespawn(p, ap); }
                else if (ticks % 10 == 0) p.displayClientMessage(Component.literal((ap.respawnAt - ticks + 19) / 20 + " 秒後重生").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            if (ap.respawnKitTicks > 0 && --ap.respawnKitTicks == 0) {
                ap.respawnKitTicks = -1;
                onRespawned(p, ap);
            }
            if (ticks % 10 == 0 && usesAntiCamp() && cfg.antiCamp.get() && p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE && p.isAlive()) antiCamp(p, ap);
            if (usesClasses() && p.isAlive() && !p.isSpectator()) refreshClassSpeed(p, ap);
            if (ticks % 20 == 0 && p.isAlive() && !p.isSpectator()) {
                ensureOne(p, cfg.ammoBox.get());
                removeLooseAmmo(p);
                if (usesClasses()) classPassive(p, ap);
                if (inBase(p, ap) && p.getHealth() < p.getMaxHealth()) p.heal(5f); // 基地內快速回血
            }
        }
        tickRules();
        if (ticks % 10 == 0) syncHud();
    }

    /** 職業移動速度加成（每 tick 更新，支援刺客拔刀 +30%、突擊兵 +20%、偵察兵 +10%、重裝兵 -15%）。 */
    private void refreshClassSpeed(ServerPlayer p, ArenaPlayer ap) {
        AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        double want = ap.cls.speedBonus;
        if (ap.cls.meleeSpeedBonus != 0 && com.provinggrounds.events.MeleeDrawSpeed.drawn(p)) {
            want += ap.cls.meleeSpeedBonus;
        }
        AttributeModifier cur = attr.getModifier(CLASS_SPEED_MOD);
        if (Math.abs(want) < 1e-6) {
            if (cur != null) attr.removeModifier(CLASS_SPEED_MOD);
            return;
        }
        if (cur != null && Math.abs(cur.getAmount() - want) < 1e-6) return;
        attr.removeModifier(CLASS_SPEED_MOD);
        attr.addTransientModifier(new AttributeModifier(CLASS_SPEED_MOD, "pg_class_speed", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 職業被動（每秒；計畫書 9 重製）。 */
    private void classPassive(ServerPlayer p, ArenaPlayer ap) {
        if (loadoutOpen()) return; // 開局選角準備期不觸發掃描與補給
        switch (ap.cls) {
            case RECON -> {
                // 偵察兵：每 20 秒發光最近的敵人 1 秒（自己與同隊可見）
                if (++ap.reconSec >= 20) {
                    ap.reconSec = 0;
                    ServerPlayer nearest = null;
                    double best = Double.MAX_VALUE;
                    for (ServerPlayer o : online()) {
                        ArenaPlayer oa = players.get(o.getUUID());
                        if (oa == null || sameSide(ap, oa) || !o.isAlive() || o.isSpectator()) continue;
                        if (kind() == GameManager.GameMode.DOM && !oa.alive) continue;
                        double d = o.distanceToSqr(p);
                        if (d < best) { best = d; nearest = o; }
                    }
                    if (nearest != null) {
                        com.provinggrounds.special.GlowManager.add(nearest, com.provinggrounds.special.GlowManager.Audience.TEAM, p, true, 1.0, false);
                        p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.3f, 1.8f);
                    }
                }
            }
            case SUPPORT -> {
                // 支援兵：身邊 4 格內的所有隊友（包含自己）每 30 秒補一顆隨機投擲物
                if (++ap.supportSec >= 30) {
                    ap.supportSec = 0;
                    List<String> pool = ArenaClass.Kits.SUPPORT_THROWABLES;
                    for (ServerPlayer mate : online()) {
                        ArenaPlayer ma = players.get(mate.getUUID());
                        if (ma == null || !sameSide(ap, ma) || !mate.isAlive() || mate.isSpectator()) continue;
                        if (mate.distanceToSqr(p) > 16.0) continue; // 4 格內
                        String tid = pool.get(level.random.nextInt(pool.size()));
                        ItemStack stack = RespawnKit.parse(ArenaClass.Kits.throwable(tid));
                        if (!stack.isEmpty()) {
                            give(mate, stack);
                            mate.displayClientMessage(Component.literal("[支援兵] 補給一顆隨機投擲物").withStyle(ChatFormatting.AQUA), true);
                            mate.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
                        }
                    }
                }
            }
            case MEDIC -> {
                // 醫護兵：身邊 4 格內的隊友每秒回 1 HP
                for (ServerPlayer mate : online()) {
                    ArenaPlayer ma = players.get(mate.getUUID());
                    if (mate == p || ma == null || !sameSide(ap, ma) || !mate.isAlive() || mate.isSpectator()) continue;
                    if (downedEnabled && downed.isDowned(mate.getUUID())) continue;
                    if (mate.distanceToSqr(p) <= 16.0 && mate.getHealth() < mate.getMaxHealth()) {
                        mate.heal(1f);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, mate.getX(), mate.getY() + 1.8, mate.getZ(), 1, 0.2, 0.1, 0.2, 0);
                    }
                }
            }
            default -> {}
        }
    }

    /**
     * 防蹲點（計畫書 5 的 H2；每 10 tick 一次）：和敵方重生點的水平距離（平方比較，不掃方塊）在範圍內 →
     * 先警告 anti_camp_warn 秒，之後每秒受傷（start、start+step、…，上限 max），並緩速、發光；離開就歸零。
     */
    private void antiCamp(ServerPlayer p, ArenaPlayer ap) {
        if (!inEnemySpawn(p, ap)) { ap.campTicks = 0; return; }
        ap.campTicks += 10;
        int warn = cfg.antiCampWarn.get() * 20;
        if (ap.campTicks <= warn) {
            int left = (warn - ap.campTicks + 19) / 20 + 1;
            p.displayClientMessage(Component.literal("敵方重生區！" + left + " 秒後開始受傷").withStyle(ChatFormatting.RED), true);
            return;
        }
        int after = ap.campTicks - warn; // 10、20、30…
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));
        if ((after - 10) % 20 != 0) return; // 每秒一次
        int n = (after - 10) / 20;
        int dmg = Math.min(cfg.antiCampMax.get(), cfg.antiCampStart.get() + cfg.antiCampStep.get() * n);
        p.displayClientMessage(Component.literal("離開敵方重生區！-" + dmg).withStyle(ChatFormatting.RED), true);
        p.hurt(p.damageSources().magic(), dmg);
    }

    /** 在敵方任一個重生點的防蹲點範圍內（水平距離；高度差 8 格內）。 */
    public boolean inEnemySpawn(ServerPlayer p, ArenaPlayer ap) {
        if (ap.team != RED && ap.team != BLUE) return false;
        double r = cfg.antiCampRadius.get();
        for (Vec3 s : teamSpawns(ap.team == RED ? BLUE : RED)) {
            double dx = p.getX() - s.x, dz = p.getZ() - s.z;
            if (dx * dx + dz * dz <= r * r && Math.abs(p.getY() - s.y) <= 8) return true;
        }
        return false;
    }

    /** 這個模式有沒有紅藍基地的防蹲點（槍王之王沒有）。 */
    /** 敵方重生區懲罰只在團隊死鬥有效（計畫書 6 A13）。 */
    protected boolean usesAntiCamp() { return kind() == GameManager.GameMode.TDM; }

    /** 在敵方重生區、而且設定不能開槍。 */
    public boolean blocksFire(UUID id) {
        ArenaPlayer ap = players.get(id);
        return ap != null && cfg.antiCampNoFire.get() && ap.campTicks > 0;
    }

    // ── 重生保護（計畫書 5 的 H2）：無敵但不能攻擊，開槍或時間到結束 ──

    public boolean isProtected(UUID id) {
        ArenaPlayer ap = players.get(id);
        return ap != null && ap.protectUntil > ticks;
    }

    public void endProtection(UUID id) {
        ArenaPlayer ap = players.get(id);
        if (ap != null) ap.protectUntil = 0;
    }

    // ── 掉線（計畫書 5 的 C4）：超過 disconnect_timeout 才移出比賽與隊伍，之前都算隊伍人數 ──

    private final Set<UUID> removed = new HashSet<>();

    public boolean wasRemoved(UUID id) { return removed.contains(id); }

    public void handleLogout(ServerPlayer p) {
        ArenaPlayer ap = players.get(p.getUUID());
        if (ap == null) return;
        ap.offlineSince = ticks;
        // 計畫書 7 D：比賽中活著的人掉線 → 原地留分身（重生倒數中、旁觀中不留）
        if (endTicks < 0 && !p.isSpectator() && p.isAlive() && ap.respawnAt < 0 && (kind() != GameManager.GameMode.DOM || ap.alive)) {
            com.provinggrounds.core.DisconnectBodies.spawn(p, "arena", downedEnabled && downed.isDowned(p.getUUID()));
            com.provinggrounds.core.DisconnectBodies.notifyTeam(server, p.getUUID(), ap.name + " 掉線了（原地留下分身）");
        }
    }

    /** 測試指令：模擬掉線（計畫書 7 G）。 */
    public boolean debugGoOffline(ServerPlayer p) {
        ArenaPlayer ap = players.get(p.getUUID());
        if (ap == null || ap.offlineSince >= 0) return false;
        handleLogout(p);
        downed.onLogout(p);
        com.provinggrounds.core.DisconnectBodies.setSimulated(p.getUUID(), true);
        p.setGameMode(GameType.SPECTATOR);
        return true;
    }

    /** 掉線分身被殺（計畫書 7 D）：等於這位玩家死亡一次（擊殺、金錢、分數照常）；回來之前不重生，回來後走重生倒數。 */
    public void handleBodyDeath(com.provinggrounds.core.DisconnectBodies.OfflineState s, @Nullable ServerPlayer killer) {
        ArenaPlayer v = players.get(s.owner);
        if (v == null || endTicks >= 0) return;
        v.deaths++;
        v.deathPos = s.pos;
        v.campTicks = 0;
        int lostStreak = v.streak;
        v.streak = 0;
        onDeath(null, v);
        ArenaPlayer k = killer == null ? null : players.get(killer.getUUID());
        boolean counted = k != null && !sameSide(k, v);
        if (counted) {
            k.kills++;
            k.streak++;
        }
        com.provinggrounds.core.KillEvents.arenaKillOffline(this, server, counted ? killer : null, counted ? k : null, v, lostStreak);
        if (!counted) return;
        onKill(killer, k, null, v);
        onClassKill(killer, k);
        if (com.provinggrounds.special.SpecialConfig.enabledSafe()) {
            if (kind() == GameManager.GameMode.TDM) grantTdmKillSpecial(killer, k);
            else if (kind() == GameManager.GameMode.GK) {
                ItemStack sp = com.provinggrounds.special.SpecialLoot.roll(level);
                if (!sp.isEmpty()) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, s.pos.x, s.pos.y + 0.5, s.pos.z, sp));
            }
        }
        if (kind() == GameManager.GameMode.TDM) v.tdmDryDeaths++;
    }

    private void removeTimedOut() {
        int limit = cfg.disconnectTimeout.get() * 20;
        for (ArenaPlayer ap : new ArrayList<>(players.values())) {
            if (ap.offlineSince < 0 || ticks - ap.offlineSince < limit) continue;
            players.remove(ap.id);
            removed.add(ap.id);
            com.provinggrounds.core.DisconnectBodies.remove(server, ap.id); // 掉線分身也消失（計畫書 7 D）
            for (ServerPlayer mate : online()) {
                ArenaPlayer m = players.get(mate.getUUID());
                if (m != null && sameSide(m, ap)) mate.sendSystemMessage(Component.literal(ap.name + " 因掉線過久被移出").withStyle(ChatFormatting.GRAY));
            }
            onRemoved(ap);
        }
    }

    /** 掉線過久被移出後（槍王之王重算門檻）。 */
    protected void onRemoved(ArenaPlayer ap) {}

    /** 測試指令（/pg arena debug；計畫書 5 的 C7）。各模式覆寫自己支援的動作。 */
    public String debug(String action, String arg, int n) {
        return "這個模式不支援 " + action;
    }


    private void removeLooseAmmo(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(GunCompat.AMMO)) inv.setItem(i, ItemStack.EMPTY); // 有無限彈藥箱，不需要散裝子彈
        }
    }

    // ── 事件（ArenaManager 轉過來）──────────────────────────────────────────

    public final void handleDeath(ServerPlayer victim, @Nullable ServerPlayer killer) {
        ArenaPlayer v = players.get(victim.getUUID());
        if (v == null || endTicks >= 0) return;
        v.deaths++;
        v.deathPos = victim.position();
        v.campTicks = 0;
        int lostStreak = v.streak;
        v.streak = 0;
        onDeath(victim, v);
        ArenaPlayer k = killer == null || killer == victim ? null : players.get(killer.getUUID());
        boolean counted = k != null && !sameSide(k, v);
        if (counted) {
            k.kills++;
            k.streak++;
        }
        com.provinggrounds.core.KillEvents.arenaKill(this, counted ? killer : null, counted ? k : null, victim, v, lostStreak);
        if (!counted) return;
        onKill(killer, k, victim, v);
        onClassKill(killer, k);
        if (kind() == GameManager.GameMode.TDM) v.tdmDryDeaths++;
        if (com.provinggrounds.special.SpecialConfig.enabledSafe()) { // 特殊道具模式（大廳開關）
            // 計畫書 9：團隊死鬥每累計 3 殺獲得 1 個特殊道具（連死 3 次未獲得則於重生保底 1 個）；佔點整場一種；槍王之王照舊掉在地上
            if (kind() == GameManager.GameMode.TDM) grantTdmKillSpecial(killer, k);
            else if (kind() == GameManager.GameMode.GK) {
                ItemStack sp = com.provinggrounds.special.SpecialLoot.roll(level);
                if (!sp.isEmpty()) victim.spawnAtLocation(sp);
            }
        }
    }

    /** 職業擊殺效果：突擊兵回 20 HP、工兵刷新手榴彈與瞬爆手榴彈。 */
    private void onClassKill(ServerPlayer killer, ArenaPlayer k) {
        if (!usesClasses() || killer == null || !killer.isAlive()) return;
        if (k.cls == ArenaClass.ASSAULT) {
            killer.heal(20f);
            killer.displayClientMessage(Component.literal("[突擊兵] 擊殺回復 +20 HP").withStyle(ChatFormatting.GREEN), true);
        } else if (k.cls == ArenaClass.ENGINEER) {
            refillConsumables(killer, k, false);
            killer.displayClientMessage(Component.literal("[工兵] 擊殺刷新手榴彈與瞬爆手榴彈").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** 團隊死鬥：每累計擊殺 3 人獲得 1 個隨機特殊道具。 */
    private void grantTdmKillSpecial(ServerPlayer killer, ArenaPlayer k) {
        k.tdmSpecialKills++;
        if (k.tdmSpecialKills >= 3) {
            k.tdmSpecialKills = 0;
            k.tdmDryDeaths = 0;
            com.provinggrounds.special.DomGear.giveRandom(killer, "累計擊殺 3 人");
        }
    }

    /** 隊伍顏色（RGB）：給擊殺紀錄用。 */
    public int colorOf(ArenaPlayer ap) {
        return rgb(teamColor(ap.team));
    }

    public final void handleRespawn(ServerPlayer p) {
        ArenaPlayer ap = players.get(p.getUUID());
        if (ap == null) return;
        if (endTicks >= 0) { teleport(p, spawnOf(ap)); return; }
        respawn(p, ap);
    }

    /** 重生：死亡後先在死亡位置旁觀 respawn_delay 秒（計畫書 5），再傳回重生點。佔點覆寫（回合中變旁觀）。 */
    protected void respawn(ServerPlayer p, ArenaPlayer ap) {
        int delay = respawnDelaySeconds();
        if (delay > 0 && ap.deathPos != null) {
            ap.respawnAt = ticks + delay * 20;
            p.setGameMode(GameType.SPECTATOR);
            teleport(p, ap.deathPos.add(0, 1.5, 0));
            ap.deathPos = null;
            return;
        }
        ap.deathPos = null;
        doRespawn(p, ap);
    }

    /** 真正重生：冒險模式、傳回重生點、10 tick 後發職業消耗品、重生保護。 */
    protected void doRespawn(ServerPlayer p, ArenaPlayer ap) {
        p.setGameMode(GameType.ADVENTURE);
        teleport(p, spawnOf(ap));
        prepare(p, ap, false);
        ap.respawnKitTicks = 10;
        // 團隊死鬥保底：連續死亡 3 次都沒有拿到特殊道具 → 獲得 1 個
        if (kind() == GameManager.GameMode.TDM && com.provinggrounds.special.SpecialConfig.enabledSafe() && ap.tdmDryDeaths >= 3) {
            ap.tdmDryDeaths = 0;
            com.provinggrounds.special.DomGear.giveRandom(p, "連死 3 次保底");
        }
        int protect = cfg.spawnProtect.get();
        if (protect > 0) ap.protectUntil = ticks + protect * 20;
    }

    protected int respawnDelaySeconds() { return cfg.respawnDelay.get(); }

    public void handleLogin(ServerPlayer p) {
        ArenaPlayer ap = players.get(p.getUUID());
        com.provinggrounds.core.DisconnectBodies.setSimulated(p.getUUID(), false);
        var body = com.provinggrounds.core.DisconnectBodies.get(server, p.getUUID());
        if (ap == null) { if (body != null) com.provinggrounds.core.DisconnectBodies.remove(server, p.getUUID()); return; }
        ap.offlineSince = -1;
        ap.respawnAt = -1;
        GameManager.get().addPlayer(p);
        TeamManager.get().syncVanillaTeams(server, online());
        if (body != null && "arena".equals(body.mode) && !body.dead) {
            // 計畫書 7 D：分身還活著 → 回到分身的位置與血量繼續（倒地狀態不保留，回來是站著的）
            com.provinggrounds.core.DisconnectBodies.restore(p);
            ap.deathPos = null;
            p.setGameMode(GameType.ADVENTURE);
            com.provinggrounds.core.DisconnectBodies.notifyTeam(server, p.getUUID(), ap.name + " 回來了");
            return;
        }
        if (body != null) {
            // 分身已被殺：照死亡後的規則（死亡位置旁觀 → 重生倒數）
            com.provinggrounds.core.DisconnectBodies.remove(server, p.getUUID());
            com.provinggrounds.core.DisconnectBodies.notifyTeam(server, p.getUUID(), ap.name + " 回來了");
        } else {
            ap.deathPos = null;
        }
        respawn(p, ap);
    }

    /** 丟東西：不能丟的立刻撿回來。回傳 true = 要取消丟出。「欸幹你手上有蟑螂」強制丟出時例外。 */
    public boolean handleToss(ServerPlayer p, ItemStack stack) {
        if (!players.containsKey(p.getUUID())) return false;
        if (com.provinggrounds.special.Plan9Specials.isForcedDrop(p)) return false;
        return !allowToss(stack);
    }

    public boolean isParticipant(UUID id) { return players.containsKey(id); }

    @Nullable
    public ArenaPlayer player(UUID id) { return players.get(id); }

    protected boolean sameSide(ArenaPlayer a, ArenaPlayer b) {
        return a.teamKey != null && a.teamKey.equals(b.teamKey);
    }

    // ── 金錢、商店、轉帳、職業 ──────────────────────────────────────────────

    /** 加錢（負數 = 扣錢，不會小於 0）。reason 不是 null 就在物品欄上方顯示。 */
    protected void addMoney(@Nullable ServerPlayer p, ArenaPlayer ap, int amount, @Nullable String reason) {
        if (amount == 0) return;
        int cap = moneyCap();
        ap.money = Math.max(0, ap.money + amount);
        if (cap > 0 && ap.money > cap) ap.money = cap;
        if (p != null && reason != null) {
            p.displayClientMessage(Component.literal((amount > 0 ? "+$" : "-$") + Math.abs(amount) + " " + reason)
                .withStyle(amount > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        }
    }

    protected void addMoneyTeam(int team, int amount, String reason, boolean chat) {
        for (ArenaPlayer ap : players.values()) {
            if (ap.team != team) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(ap.id);
            addMoney(p, ap, amount, chat ? null : reason);
            if (chat && p != null) {
                p.sendSystemMessage(Component.literal("[金錢] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal((amount >= 0 ? "+$" : "-$") + Math.abs(amount) + " " + reason).withStyle(ChatFormatting.GREEN)));
            }
        }
    }

    public boolean inBase(ServerPlayer p, ArenaPlayer ap) {
        if (!usesShop() || (ap.team != RED && ap.team != BLUE)) return false;
        int r = cfg.baseRadius.get();
        for (Vec3 s : teamSpawns(ap.team)) {
            if (Math.abs(p.getX() - s.x) <= r + 0.5 && Math.abs(p.getZ() - s.z) <= r + 0.5
                && p.getY() >= s.y - 1 && p.getY() <= s.y + 4) return true;
        }
        return false;
    }

    public boolean canShop(ServerPlayer p, ArenaPlayer ap) {
        return usesShop() && shopOpen() && p.isAlive() && !p.isSpectator() && inBase(p, ap);
    }

    /** 防具套裝（計畫書 7 A8）：材質的商品 ID 前綴與顯示名稱；部位依序是 帽、胸甲、褲、靴。 */
    public static final String[][] ARMOR_SETS = { { "leather", "皮革" }, { "chainmail", "鎖鏈" }, { "golden", "金" },
        { "iron", "鐵" }, { "diamond", "鑽石" }, { "netherite", "獄髓" } };
    public static final String[] ARMOR_PIECES = { "helmet", "chestplate", "leggings", "boots" };

    /** 某個材質在商店裡的四件（沒有的部位是 null）。 */
    public static ArenaData.ShopItem[] armorSet(int set) {
        ArenaData.ShopItem[] out = new ArenaData.ShopItem[4];
        if (set < 0 || set >= ARMOR_SETS.length) return out;
        for (ArenaData.ShopItem i : ArenaData.shop()) {
            for (int k = 0; k < 4; k++) if (i.id().equals(ARMOR_SETS[set][0] + "_" + ARMOR_PIECES[k])) out[k] = i;
        }
        return out;
    }

    /** 購買整套：價格 = 四件總和（不打折；自由人八折照舊），每一件照單件購買的規則穿上（舊的放回背包）。 */
    public void buySet(ServerPlayer p, int set) {
        ArenaPlayer ap = players.get(p.getUUID());
        if (ap == null || !usesShop()) return;
        if (!canShop(p, ap)) { deny(p, "必須在自己的基地內才能購買！"); return; }
        ArenaData.ShopItem[] pieces = armorSet(set);
        int total = 0, n = 0;
        for (ArenaData.ShopItem i : pieces) if (i != null) { total += shopPrice(p, ap, i); n++; }
        if (n == 0) return;
        if (ap.money < total) { deny(p, "金錢不足！還差 $" + (total - ap.money)); return; }
        ap.money -= total;
        for (ArenaData.ShopItem item : pieces) {
            if (item == null) continue;
            ItemStack stack = RespawnKit.parse(item.item() + " " + item.count());
            if (!(stack.getItem() instanceof ArmorItem a)) { ap.money += shopPrice(p, ap, item); continue; } // 伺服器缺少物品：退錢
            EquipmentSlot slot = a.getEquipmentSlot();
            ItemStack old = p.getItemBySlot(slot);
            p.setItemSlot(slot, stack);
            if (!old.isEmpty()) give(p, old);
        }
        p.sendSystemMessage(Component.literal("[商店] 已購買整套" + ARMOR_SETS[set][1] + "防具（-$" + total + "）").withStyle(ChatFormatting.GREEN));
        p.playNotifySound(SoundEvents.ARMOR_EQUIP_DIAMOND, SoundSource.MASTER, 1f, 1f);
        syncHud();
    }

    public void buy(ServerPlayer p, int index) {
        ArenaPlayer ap = players.get(p.getUUID());
        List<ArenaData.ShopItem> shop = ArenaData.shop();
        if (ap == null || !usesShop() || index < 0 || index >= shop.size()) return;
        if (!canShop(p, ap)) { deny(p, "必須在自己的基地內才能購買！"); return; }
        ArenaData.ShopItem item = shop.get(index);
        if (usesClasses() && !ap.cls.canBuyCategory(item.category())) {
            deny(p, "你的職業（" + ap.cls.displayName + "）無法購買" + item.category() + "！");
            return;
        }
        int price = shopPrice(p, ap, item);
        if (ap.money < price) { deny(p, "金錢不足！還差 $" + (price - ap.money)); return; }
        ItemStack stack = RespawnKit.parse(item.item() + " " + item.count());
        if (stack.isEmpty()) { deny(p, "這個商品目前無法購買（伺服器缺少物品）"); return; }
        GunCompat.fillAmmo(stack);
        boolean armor = stack.getItem() instanceof ArmorItem;
        if (!armor && p.getInventory().getFreeSlot() < 0) { deny(p, "背包已滿！"); return; }
        ap.money -= price;
        if (armor) {
            EquipmentSlot slot = ((ArmorItem) stack.getItem()).getEquipmentSlot();
            ItemStack old = p.getItemBySlot(slot);
            p.setItemSlot(slot, stack);
            if (!old.isEmpty()) give(p, old);
        } else {
            give(p, stack);
        }
        p.sendSystemMessage(Component.literal("[商店] 成功購買 " + item.name() + "！（-$" + price + "）").withStyle(ChatFormatting.GREEN));
        p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1f, 1.2f);
        syncHud();
    }

    /** 團隊死鬥＋特殊道具模式：只有開局時免費發給所有人一個隨機特殊道具（計畫書 9）。 */
    private void giveStartSpecial(ServerPlayer p) {
        if (kind() == GameManager.GameMode.TDM && com.provinggrounds.special.SpecialConfig.enabledSafe()) {
            com.provinggrounds.special.DomGear.giveRandom(p, "開局");
        }
    }

    /** 商店價格：職業折扣，再加上佔點特殊物品的加價（選越強越貴；計畫書 8）。 */
    protected int shopPrice(ServerPlayer p, ArenaPlayer ap, ArenaData.ShopItem item) {
        return com.provinggrounds.special.DomGear.applyMarkup(item.priceFor(priceClass(ap)), com.provinggrounds.special.DomGear.activeTier(p));
    }

    /** 算價格用的職業（沒有職業的模式 = null，不打折）。 */
    @Nullable
    private ArenaClass priceClass(ArenaPlayer ap) { return usesClasses() ? ap.cls : null; }

    /** 花錢買不是商店清單裡的東西（佔點的特殊物品；計畫書 8）。檢查基地、金錢，成功就扣錢、提示、同步 HUD。 */
    public boolean trySpend(ServerPlayer p, int price, String what) {
        ArenaPlayer ap = players.get(p.getUUID());
        if (ap == null || !usesShop()) return false;
        if (!canShop(p, ap)) { deny(p, "必須在自己的基地內才能購買！"); return false; }
        if (ap.money < price) { deny(p, "金錢不足！還差 $" + (price - ap.money)); return false; }
        ap.money -= price;
        p.sendSystemMessage(Component.literal("[商店] 成功購買 " + what + "！（-$" + price + "）").withStyle(ChatFormatting.GREEN));
        p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1f, 1.2f);
        syncHud();
        return true;
    }

    public void transfer(ServerPlayer p, UUID targetId, int amount) {
        ArenaPlayer ap = players.get(p.getUUID()), tp = players.get(targetId);
        if (ap == null || tp == null || ap == tp || !usesShop()) return;
        if (!sameSide(ap, tp)) { deny(p, "只能轉帳給隊友"); return; }
        long wait = ap.lastTransferTick + cfg.transferCooldown.get() * 20L - ticks;
        if (wait > 0) { deny(p, "轉帳冷卻中，還要 " + ((wait + 19) / 20) + " 秒"); return; }
        int amt = amount <= 0 ? ap.money : Math.min(amount, ap.money);
        if (amt <= 0) { deny(p, "沒有錢可以轉"); return; }
        int cap = moneyCap();
        if (cap > 0) amt = Math.min(amt, cap - tp.money);
        if (amt <= 0) { deny(p, tp.name + " 的錢已經到上限了"); return; }
        ap.money -= amt;
        tp.money += amt;
        ap.lastTransferTick = ticks;
        p.sendSystemMessage(Component.literal("[轉帳] 已轉 $" + amt + " 給 " + tp.name).withStyle(ChatFormatting.GREEN));
        ServerPlayer t = server.getPlayerList().getPlayer(targetId);
        if (t != null) {
            t.sendSystemMessage(Component.literal("[轉帳] " + ap.name + " 轉給你 $" + amt).withStyle(ChatFormatting.GREEN));
            t.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1f, 1.5f);
        }
        syncHud();
    }

    public void setClass(ServerPlayer p, int ordinal) {
        ArenaPlayer ap = players.get(p.getUUID());
        if (ap == null || !usesClasses()) return;
        if (!loadoutOpen()) { deny(p, "比賽開始後無法更換職業！"); return; }
        ArenaClass c = ArenaClass.byOrdinal(ordinal);
        if (c == ap.cls) return;
        // 拿掉舊職業的起始槍與固有消耗品，換成新的
        removeGun(p, ap.cls.gun);
        for (String spec : ap.cls.consumables) {
            ItemStack t = RespawnKit.parse(spec);
            if (!t.isEmpty()) remove(p, t, Integer.MAX_VALUE);
        }
        ap.cls = c;
        com.provinggrounds.core.LobbyManager.get().rememberClass(p.getUUID(), c);
        prepare(p, ap, true);
        refreshClassSpeed(p, ap);
        p.sendSystemMessage(Component.literal("[職業] 已選擇 " + c.displayName + "（" + c.description + "）").withStyle(ChatFormatting.AQUA));
        p.playNotifySound(SoundEvents.ARMOR_EQUIP_IRON, SoundSource.MASTER, 1f, 1f);
        syncHud();
    }

    // ── 結束 ────────────────────────────────────────────────────────────────

    public boolean isEnding() { return endTicks >= 0; }

    /** 比賽結束：顯示結果，幾秒後回大廳。winners = 獲勝的玩家（平手是空的）。 */
    protected void endMatch(Component headline, Set<UUID> winners) {
        if (endTicks >= 0) return;
        endTicks = cfg.endSeconds.get() * 20;
        GameManager.get().endGame();
        broadcastTitle(headline, Component.literal(scoreSummary()).withStyle(ChatFormatting.WHITE), 100);
        // 團隊死鬥、佔點的獲勝音效音量 50%（計畫書 6 A3）
        playAll(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, kind() == GameManager.GameMode.GK ? 1f : 0.5f, 1f);
        server.getPlayerList().broadcastSystemMessage(Component.literal("[PG] ").withStyle(ChatFormatting.GOLD)
            .append(headline.copy()).append(Component.literal("　" + scoreSummary()).withStyle(ChatFormatting.GRAY)), false);
        // 擊殺榜前三
        List<ArenaPlayer> top = new ArrayList<>(players.values());
        top.sort((a, b) -> Integer.compare(b.kills, a.kills));
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            ArenaPlayer ap = top.get(i);
            server.getPlayerList().broadcastSystemMessage(Component.literal("  " + (i + 1) + ". " + ap.name + "　擊殺 " + ap.kills + "／死亡 " + ap.deaths)
                .withStyle(i == 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false);
        }
        for (UUID id : winners) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) PlayerDataManager.get().addWin(p);
        }
        for (ServerPlayer p : online()) {
            p.setInvulnerable(true);
            p.setGameMode(GameType.ADVENTURE);
            p.setCamera(p);
        }
        ProvingGroundsMod.LOGGER.info("Arena {} ended: {}", kind(), headline.getString());
    }

    /** 結算用的一行分數（例如「紅隊 100 : 87 藍隊」）。 */
    protected abstract String scoreSummary();

    /** 還原所有參賽者、回大廳。 */
    /**
     * 方塊還原：記錄對戰場地（兩隊重生點、槍王之王重生點的範圍外加 48 格）裡所有方塊的變化，結束時還原。
     * 不記整個主世界，管理員在別處蓋東西不會被還原。
     */
    private void beginBlockLedger() {
        List<Vec3> pts = new ArrayList<>(teamSpawns(RED));
        pts.addAll(teamSpawns(BLUE));
        pts.addAll(ArenaData.gkSpawns());
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vec3 v : pts) { minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x); minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z); }
        final double x0 = minX - 48, x1 = maxX + 48, z0 = minZ - 48, z1 = maxZ + 48;
        com.provinggrounds.data.BlockLedger.get(server).restoreAll(level); // 上一場沒還原到的先修回去
        com.provinggrounds.data.BlockLedger.beginMatch(server, pos -> pos.getX() >= x0 && pos.getX() <= x1 && pos.getZ() >= z0 && pos.getZ() <= z1,
            (int) Math.floor(x0), (int) Math.floor(z0), (int) Math.ceil(x1), (int) Math.ceil(z1));
    }

    public final void finish() {
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(savedKeepInventory, server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(savedImmediateRespawn, server);
        downed.clearAll();
        onFinish();
        // 特殊道具效果全部結束（領域沒收的東西先還回去、地雷煙霧雪牆清掉），之後才清背包回大廳（大逃殺本來就有，團隊模式漏了）
        com.provinggrounds.special.SpecialWorld.cleanup(server);
        com.provinggrounds.data.BlockLedger.endMatch(server); // 場地方塊還原

        for (ServerPlayer p : online()) restorePlayer(p);
        ArenaManager.clear(this);
        GameManager.get().reset();
    }

    /** 結束時清理場地（屏障、據點地板…）。 */
    protected void onFinish() {}

    public static void restorePlayer(ServerPlayer p) {
        p.setInvulnerable(false);
        p.getInventory().clearContent();
        p.removeAllEffects();
        AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.removeModifier(HEALTH_MOD);
        AttributeInstance spd = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.removeModifier(CLASS_SPEED_MOD);
        p.setGameMode(GameType.ADVENTURE);
        p.setCamera(p);
        PGNetwork.sendTo(p, ArenaStatePacket.NONE);
        List<com.provinggrounds.venue.Venue> venues = com.provinggrounds.venue.VenueManager.venues();
        com.provinggrounds.venue.Venue lobby = venues.stream().filter(v -> v.has("clear_all")).findFirst().orElse(venues.isEmpty() ? null : venues.get(0));
        if (lobby != null) com.provinggrounds.venue.VenueManager.enter(p, lobby, false);
        else p.setHealth(p.getMaxHealth());
    }

    // ── HUD ─────────────────────────────────────────────────────────────────

    /** 世界標記（計畫書 5）：預設是敵方重生區（防蹲點邊界）；佔點再加開放中的據點。 */
    protected List<ArenaStatePacket.Marker> markers(ArenaPlayer viewer) {
        List<ArenaStatePacket.Marker> out = new ArrayList<>();
        if (!usesAntiCamp() || !cfg.antiCamp.get() || (viewer.team != RED && viewer.team != BLUE)) return out;
        int enemy = viewer.team == RED ? BLUE : RED;
        for (Vec3 s : teamSpawns(enemy)) {
            out.add(new ArenaStatePacket.Marker((byte) 1, "", s.x, s.y, s.z, cfg.antiCampRadius.get(), 0xFF3030, false));
        }
        return out;
    }

    /** 戰績面板：所有參賽者（擊殺多的在前）。 */
    private List<ArenaStatePacket.Row> board() {
        List<ArenaPlayer> all = new ArrayList<>(players.values());
        all.sort((a, b) -> a.team != b.team ? Integer.compare(a.team, b.team) : b.kills != a.kills ? Integer.compare(b.kills, a.kills) : Integer.compare(a.deaths, b.deaths));
        List<ArenaStatePacket.Row> out = new ArrayList<>();
        for (ArenaPlayer a : all) {
            out.add(new ArenaStatePacket.Row(a.name, colorOf(a), a.kills, a.deaths, usesShop() ? a.money : -1, a.offlineSince < 0));
        }
        return out;
    }

    protected void syncHud() {
        List<ArenaStatePacket.Row> board = board();
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            List<ArenaStatePacket.Mate> mates = new ArrayList<>();
            for (ArenaPlayer o : players.values()) {
                if (o != ap && sameSide(o, ap)) mates.add(new ArenaStatePacket.Mate(o.id, o.name, o.money));
            }
            long wait = Math.max(0, ap.lastTransferTick + cfg.transferCooldown.get() * 20L - ticks);
            PGNetwork.sendTo(p, new ArenaStatePacket(kindByte(), title(), timerSeconds(), timerLabel(), statusLine(ap),
                scoreLines(ap), ap.money, ap.cls.ordinal(), usesShop(), usesClasses(), canShop(p, ap), loadoutOpen(), ap.team,
                (int) ((wait + 19) / 20), ap.kills, ap.deaths, mates, markers(ap), board));

        }
    }

    private byte kindByte() {
        return switch (kind()) { case TDM -> 1; case DOM -> 2; case GK -> 3; default -> 0; };
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    protected List<ServerPlayer> online() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : players.keySet()) {
            if (com.provinggrounds.core.DisconnectBodies.isSimulated(id)) continue; // 測試指令的「模擬掉線」
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    protected List<ServerPlayer> online(int team) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : online()) if (players.get(p.getUUID()).team == team) out.add(p);
        return out;
    }

    protected Vec3 teamSpawn(int team) {
        Vec3 v = ArenaConfig.vec(team == BLUE ? cfg.blueSpawn.get() : cfg.redSpawn.get());
        return v != null ? v : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
    }

    /** 這隊所有重生點（主重生點＋額外的；計畫書 5 的 H2）。 */
    public List<Vec3> teamSpawns(int team) {
        List<Vec3> out = new ArrayList<>();
        out.add(teamSpawn(team));
        for (String s : team == BLUE ? cfg.blueSpawnsExtra.get() : cfg.redSpawnsExtra.get()) {
            Vec3 v = ArenaConfig.vec(s);
            if (v != null) out.add(v);
        }
        return out;
    }

    /** 隨機一個重生點。 */
    protected Vec3 randomTeamSpawn(int team) {
        List<Vec3> all = teamSpawns(team);
        return all.get(level.random.nextInt(all.size()));
    }


    protected void teleport(ServerPlayer p, Vec3 pos) {
        if (p.isPassenger()) p.stopRiding();
        p.teleportTo(level, pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
    }

    /** 發物品：照快捷欄規則放進專用格（槍 1、2，近戰 3，補品 4，投擲物 5；計畫書 6 C2），放不下才丟在腳邊。 */
    protected static void give(ServerPlayer p, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (com.provinggrounds.modes.br.HotbarRules.active(p)) com.provinggrounds.modes.br.HotbarRules.insert(p, stack);
        if (!stack.isEmpty() && !p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack, false);
    }

    /** 背包裡沒有就補 1 個（彈藥箱、醫療包）；多的收掉。 */
    protected static void ensureOne(ServerPlayer p, String spec) {
        if (spec == null || spec.isBlank()) return;
        ItemStack t = RespawnKit.parse(spec);
        if (t.isEmpty()) return;
        int have = count(p, t);
        if (have == 0) give(p, t);
        else if (have > 1) remove(p, t, have - 1);
    }

    /** 物品相同、而且 template 的 NBT 都在裡面（數量不算）。 */
    protected static boolean matches(ItemStack stack, ItemStack template) {
        if (stack.isEmpty() || !stack.is(template.getItem())) return false;
        return template.getTag() == null || NbtUtils.compareNbt(template.getTag(), stack.getTag(), true);
    }

    protected static int count(ServerPlayer p, ItemStack template) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (matches(inv.getItem(i), template)) n += inv.getItem(i).getCount();
        return n;
    }

    protected static void remove(ServerPlayer p, ItemStack template, int amount) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!matches(s, template)) continue;
            int take = Math.min(amount, s.getCount());
            s.shrink(take);
            amount -= take;
        }
    }

    protected static void removeGun(ServerPlayer p, String gunId) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.resources.ResourceLocation id = GunCompat.gunId(inv.getItem(i));
            if (id != null && id.toString().equals(gunId)) { inv.setItem(i, ItemStack.EMPTY); return; }
        }
    }

    protected static boolean hasGun(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (GunCompat.gunId(inv.getItem(i)) != null) return true;
        return false;
    }

    protected void fill(List<BlockPos[]> boxes, BlockState state, boolean onlyAirOrBarrier) {
        for (BlockPos[] b : boxes) {
            for (BlockPos pos : BlockPos.betweenClosed(b[0], b[1])) {
                BlockState cur = level.getBlockState(pos);
                if (onlyAirOrBarrier && !(cur.isAir() || cur.is(Blocks.BARRIER))) continue;
                level.setBlockAndUpdate(pos, state);
            }
        }
    }

    protected void clearSupplyChests() {
        for (String s : cfg.supplyChests.get()) {
            Vec3 v = ArenaConfig.vec(s);
            if (v == null) continue;
            if (level.getBlockEntity(BlockPos.containing(v)) instanceof Container c) c.clearContent();
        }
    }

    /** 清掉場地附近地上的物品（以兩隊重生點為中心）。 */
    protected void clearFloorItems() {
        Vec3 a = teamSpawn(RED), b = teamSpawn(BLUE);
        AABB box = new AABB(a, b).inflate(80, 40, 80);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) e.discard();
    }

    protected static void deny(ServerPlayer p, String msg) {
        p.sendSystemMessage(Component.literal("[PG] " + msg).withStyle(ChatFormatting.RED));
        p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 1f, 0.5f);
    }

    protected void broadcast(Component msg) {
        for (ServerPlayer p : online()) p.sendSystemMessage(msg);
    }

    protected void broadcastTitle(Component title, Component subtitle, int stay) {
        for (ServerPlayer p : online()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, stay, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    protected void playAll(SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer p : online()) p.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    protected static String teamName(int team) {
        return team == RED ? "紅隊" : team == BLUE ? "藍隊" : TeamManager.Team.of(team).displayName;
    }

    protected static ChatFormatting teamColor(int team) {
        return TeamManager.Team.of(team).color;
    }

    protected static int rgb(ChatFormatting f) {
        Integer c = f.getColor();
        return c == null ? 0xFFFFFF : c;
    }
}
