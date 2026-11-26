package com.provinggrounds.modes.arena;

import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.core.GameManager;
import com.provinggrounds.core.TeamManager;
import com.provinggrounds.modes.br.RespawnKit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 檔案: GkMode.java
 * 目的: 槍王之王（照舊版 gamemode/gk）。所有隊伍（含孤狼）都能參加，沒有商店、沒有職業。
 *   開局把九個武器類別打亂順序，每類隨機選一把（所有隊伍一樣）；每階段也先選好 6 類配件各 2 個。
 *   隊伍累積擊殺「人數 × 倍率」就整隊換下一階段的武器；最後一階段完成就獲勝。
 *   重生在 126 個隨機重生點之一，血量 100；開局隨機一種防具（無／皮革／金／鐵，不會壞、脫不掉）。
 */
public final class GkMode extends ArenaMode {

    /** 一個隊伍的進度。 */
    private static final class Progress {
        final String key, label;
        final int color;
        int stage = 1, kills;
        Progress(String key, String label, int color) { this.key = key; this.label = label; this.color = color; }
    }

    private static final String[] ATTACH_TYPES = { "mag", "scope", "muzzle", "stock", "grip", "laser" };
    /** 槍王之王丟棄槍械的專屬拾取者 NBT 標記（除了丟棄者本人以外無法撿起）。 */
    private static final String GK_OWNER_TAG = "pg_gk_owner";

    private final int multiplier;
    private final List<ArenaData.GkCategory> order = new ArrayList<>();
    private final List<ArenaData.GkEntry> weapons = new ArrayList<>();      // 每階段的武器
    private final List<List<String>> stageAttachments = new ArrayList<>();  // 每階段的配件
    private final Map<String, Progress> progress = new LinkedHashMap<>();
    /** 每位玩家丟在地上尚未清除的武器實體 UUID。 */
    private final Map<UUID, List<UUID>> tossedDrops = new HashMap<>();
    /** 每位玩家目前連續「身上沒有武器」的秒數（滿 5 秒自動補發並清除剛剛丟棄的武器）。 */
    private final Map<UUID, Integer> unarmedSeconds = new HashMap<>();
    private int armorTier;

    public GkMode(MinecraftServer server, int multiplier) {
        super(server);
        this.multiplier = Math.max(1, multiplier);
    }

    @Override public GameManager.GameMode kind() { return GameManager.GameMode.GK; }
    @Override public String title() { return "槍王之王"; }
    @Override public boolean usesShop() { return false; }
    @Override public boolean usesClasses() { return false; }
    /** 槍王之王允許丟棄槍械（丟出後鎖定僅限本人撿取，無武器 5 秒自動補發並清除丟棄的槍）。 */
    @Override protected boolean allowToss(ItemStack stack) { return GunCompat.gunId(stack) != null; }

    @Override
    public void onTossed(ServerPlayer p, net.minecraft.world.entity.item.ItemEntity dropped) {
        if (GunCompat.gunId(dropped.getItem()) == null) return;
        dropped.getPersistentData().putUUID(GK_OWNER_TAG, p.getUUID());
        tossedDrops.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(dropped.getUUID());
    }

    @Override
    public boolean handlePickup(ServerPlayer p, net.minecraft.world.entity.item.ItemEntity item) {
        if (!item.getPersistentData().hasUUID(GK_OWNER_TAG)) return false;
        UUID owner = item.getPersistentData().getUUID(GK_OWNER_TAG);
        if (!owner.equals(p.getUUID())) return true; // 除了丟棄者本人以外的人無法撿起
        List<UUID> list = tossedDrops.get(owner);
        if (list != null) list.remove(item.getUUID());
        unarmedSeconds.remove(owner);
        return false;
    }

    /** 清除某位玩家先前丟在地上（被標記為自己專屬）的武器實體。 */
    private void discardTossedDrops(UUID owner) {
        List<UUID> list = tossedDrops.remove(owner);
        if (list != null) {
            for (UUID eid : list) {
                var e = level.getEntity(eid);
                if (e != null && !e.isRemoved()) e.discard();
            }
        }
        // 保險掃描：確保場上所有標記為該玩家丟出的武器都清乾淨
        for (var e : level.getEntities(net.minecraft.world.entity.EntityType.ITEM,
                it -> it.getPersistentData().hasUUID(GK_OWNER_TAG) && owner.equals(it.getPersistentData().getUUID(GK_OWNER_TAG)))) {
            e.discard();
        }
    }

    @Override
    protected void onFinish() {
        for (UUID id : new ArrayList<>(tossedDrops.keySet())) discardTossedDrops(id);
        tossedDrops.clear();
        unarmedSeconds.clear();
    }

    private int stages() { return order.size(); }

    /** 隊伍照大廳的分隊（沒分隊的人當孤狼，每人一隊）。 */
    @Override
    protected void assignTeams(List<ServerPlayer> participants) {
        TeamManager tm = TeamManager.get();
        for (ServerPlayer p : participants) {
            ArenaPlayer ap = new ArenaPlayer(p.getUUID(), p.getGameProfile().getName());
            TeamManager.Team t = tm.getTeam(p).orElse(TeamManager.Team.SOLO);
            if (t == TeamManager.Team.SOLO) tm.assignTeam(p, TeamManager.Team.SOLO);
            ap.team = t.id;
            ap.teamKey = tm.teamKey(p.getUUID());
            players.put(p.getUUID(), ap);
            Progress pr = progress.computeIfAbsent(ap.teamKey, k -> new Progress(k,
                t == TeamManager.Team.SOLO ? ap.name : t.displayName, rgb(t == TeamManager.Team.SOLO ? ChatFormatting.GRAY : t.color)));
        }
    }

    /**
     * 晉級門檻 ＝ 目前隊伍人數 × 倍率（計畫書 5 的 C4）。掉線的人在被移出比賽前都算人數；
     * 人數一變（移出、換隊）門檻就跟著變。
     */
    private int needed(Progress pr) {
        int members = 0;
        for (ArenaPlayer ap : players.values()) if (ap.teamKey.equals(pr.key)) members++;
        return Math.max(1, members * multiplier);
    }

    /** 擊殺數到門檻就晉級一階；多出來的擊殺帶到下一階段（同一刻兩個擊殺不會被吃掉）。回傳是否結束比賽。 */
    private boolean checkAdvance(Progress pr) {
        int need = needed(pr);
        if (pr.kills < need || isEnding()) return false;
        pr.kills -= need;
        return advance(pr);
    }

    @Override
    protected void onRemoved(ArenaPlayer ap) {
        // 人數變少：門檻在下一次 tick 的 checkAdvance 重算，已經達到就立刻晉級
    }

    @Override
    protected boolean usesAntiCamp() { return false; }

    // ── 測試指令（/pg arena debug）──

    @Override
    public String debug(String action, String arg, int n) {
        return switch (action) {
            case "advance" -> debugAdvance(arg) ? "已讓 " + arg + " 晉級" : "找不到隊伍 " + arg + "（可用隊伍名稱或孤狼玩家名）";
            case "addkills" -> debugAddKills(arg, n) ? "已加 " + n + " 擊殺給 " + arg : "找不到隊伍 " + arg;
            default -> super.debug(action, arg, n);
        };
    }

    public boolean debugAdvance(String teamKeyOrName) {

        for (Progress pr : progress.values()) {
            if (pr.key.equalsIgnoreCase(teamKeyOrName) || pr.label.equalsIgnoreCase(teamKeyOrName)) { pr.kills = 0; advance(pr); return true; }
        }
        return false;
    }

    public boolean debugAddKills(String teamKeyOrName, int n) {
        for (Progress pr : progress.values()) {
            if (pr.key.equalsIgnoreCase(teamKeyOrName) || pr.label.equalsIgnoreCase(teamKeyOrName)) { pr.kills += n; return true; }
        }
        return false;
    }

    /** TACZ 目前載入的所有槍 ID（拿不到就是空集合，不做過濾）。 */
    private static java.util.Set<String> knownGuns() {
        return com.provinggrounds.compat.GunCompat.allGunIds();
    }

    @Override
    protected void onStart() {
        Random rand = new Random();

        order.addAll(ArenaData.gkCategories());
        Collections.shuffle(order, rand);
        Map<String, List<String>> pools = ArenaData.gkAttachments();
        java.util.Set<String> known = knownGuns();
        for (ArenaData.GkCategory c : new ArrayList<>(order)) {
            // 武器清單裡有 TACZ 沒有的槍（槍包沒裝或改名）：略過並寫 log，不要崩潰（計畫書 5 的 C6）
            List<ArenaData.GkEntry> ok = new ArrayList<>();
            for (ArenaData.GkEntry e : c.items()) {
                if (e.gun() != null && !known.isEmpty() && !known.contains(e.gun())) {
                    com.provinggrounds.ProvingGroundsMod.LOGGER.warn("GK: unknown gun id {} in category {}, skipped.", e.gun(), c.name());
                    continue;
                }
                ok.add(e);
            }
            if (ok.isEmpty()) {
                com.provinggrounds.ProvingGroundsMod.LOGGER.warn("GK: category {} has no usable weapon, removed from this match.", c.name());
                order.remove(c);
                continue;
            }
            weapons.add(ok.get(rand.nextInt(ok.size())));

            List<String> att = new ArrayList<>();
            if (c.takesAttachments()) {
                for (String type : ATTACH_TYPES) {
                    List<String> pool = pools.getOrDefault(type, List.of());
                    for (int i = 0; i < 2 && !pool.isEmpty(); i++) att.add(pool.get(rand.nextInt(pool.size())));
                }
            }
            stageAttachments.add(att);
        }
        armorTier = rand.nextInt(4); // 0 無、1 皮革、2 金、3 鐵
        clearFloorItems();
    }

    @Override
    protected void prepare(ServerPlayer p, ArenaPlayer ap, boolean giveGun) {
        super.prepare(p, ap, giveGun);
        ensureOne(p, cfg.gkMedkit.get());
        if (giveGun) {
            giveArmor(p);
            giveStage(p, ap);
        }
    }

    private void giveArmor(ServerPlayer p) {
        Item[][] sets = {
            {},
            { Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
            { Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS },
            { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS } };
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        Item[] set = sets[Math.max(0, Math.min(3, armorTier))];
        for (int i = 0; i < set.length; i++) {
            ItemStack s = new ItemStack(set[i]);
            s.getOrCreateTag().putBoolean("Unbreakable", true);
            s.enchant(Enchantments.BINDING_CURSE, 1);
            p.setItemSlot(slots[i], s);
        }
    }

    /** 清掉舊階段的武器、發這一階段的武器與配件。 */
    private void giveStage(ServerPlayer p, ArenaPlayer ap) {
        Progress pr = progress.get(ap.teamKey);
        int idx = Math.max(0, Math.min(stages() - 1, (pr == null ? 1 : pr.stage) - 1));
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()));
            if (GunCompat.gunId(s) != null || id.equals("tacz:attachment") || id.equals("lrtactical:melee") || id.equals("lrtactical:throwable")) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (stages() == 0) return;
        ItemStack weapon = weaponStack(weapons.get(idx));
        if (!weapon.isEmpty()) {
            // 放在手上那一格
            if (inv.getSelected().isEmpty()) inv.setItem(inv.selected, weapon);
            else give(p, weapon);
        }
        for (String a : stageAttachments.get(idx)) give(p, RespawnKit.parse(a));
    }

    private static ItemStack weaponStack(ArenaData.GkEntry e) {
        if (e.gun() != null) {
            ResourceLocation id = ResourceLocation.tryParse(e.gun());
            return id == null ? ItemStack.EMPTY : GunCompat.createLoadedGun(id, null);
        }
        return RespawnKit.parse(e.item() + " " + e.count());
    }

    @Override
    protected void tickRules() {
        for (Progress pr : new ArrayList<>(progress.values())) if (checkAdvance(pr)) return;
        if (ticks % 20 != 0) return;
        // 每秒檢查：投擲物階段補彈、醫療包補滿、以及沒有武器 5 秒自動補發本階段武器並清除丟棄武器
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            Progress pr = progress.get(ap.teamKey);
            if (pr == null || stages() == 0 || p.isSpectator() || !p.isAlive()) {
                unarmedSeconds.remove(p.getUUID());
                continue;
            }
            ArenaData.GkEntry e = weapons.get(Math.min(stages() - 1, pr.stage - 1));
            if (e.gun() == null && e.item() != null && e.item().startsWith("lrtactical:throwable")) {
                ItemStack t = RespawnKit.parse(e.item());
                if (!t.isEmpty() && count(p, t) == 0) give(p, t);
                unarmedSeconds.remove(p.getUUID());
            } else {
                boolean armed = e.gun() != null
                    ? hasGun(p)
                    : (e.item() != null && count(p, RespawnKit.parse(e.item())) > 0);
                if (armed) {
                    unarmedSeconds.remove(p.getUUID());
                } else {
                    int sec = unarmedSeconds.merge(p.getUUID(), 1, Integer::sum);
                    if (sec >= 5) {
                        unarmedSeconds.remove(p.getUUID());
                        discardTossedDrops(p.getUUID());
                        giveStage(p, ap);
                        p.displayClientMessage(Component.literal("已自動補發本階段武器並清除丟棄的武器").withStyle(ChatFormatting.GOLD), true);
                    } else {
                        p.displayClientMessage(Component.literal("沒有武器：" + (5 - sec) + " 秒後自動補發本階段武器").withStyle(ChatFormatting.YELLOW), true);
                    }
                }
            }
            ensureOne(p, cfg.gkMedkit.get());
        }
    }

    @Override
    protected void onKill(ServerPlayer killer, ArenaPlayer k, ServerPlayer victim, ArenaPlayer v) {
        Progress pr = progress.get(k.teamKey);
        if (pr == null) return;
        pr.kills++;
        checkAdvance(pr);
    }

    /** 晉級一階（整隊換武器）；完成最後階段就獲勝。回傳是否結束比賽。 */
    private boolean advance(Progress pr) {
        pr.stage++;
        if (pr.stage > stages()) {
            Set<UUID> winners = new HashSet<>();
            for (ArenaPlayer ap : players.values()) if (ap.teamKey.equals(pr.key)) winners.add(ap.id);
            endMatch(Component.literal(pr.label + " 成為槍王！").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), winners);
            return true;
        }
        String cat = order.get(pr.stage - 1).name();
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            if (!ap.teamKey.equals(pr.key)) continue;
            unarmedSeconds.remove(p.getUUID());
            discardTossedDrops(p.getUUID());
            giveStage(p, ap);
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.MASTER, 1f, 1.2f);
            p.displayClientMessage(Component.literal("晉級！第 " + pr.stage + " 階段：" + cat).withStyle(ChatFormatting.GOLD), true);
        }
        broadcast(Component.literal("[槍王] " + pr.label + " 晉級到第 " + pr.stage + "／" + stages() + " 階段（" + cat + "）").withStyle(ChatFormatting.YELLOW));
        return false;
    }

    /** 重生：隨機重生點、血量 100、補彈藥箱與醫療包（武器 keepInventory 保留）。 */
    @Override
    protected void doRespawn(ServerPlayer p, ArenaPlayer ap) {
        super.doRespawn(p, ap);
        unarmedSeconds.remove(p.getUUID());
        if (!hasGun(p) && count(p, RespawnKit.parse("lrtactical:melee")) == 0) {
            discardTossedDrops(p.getUUID());
            giveStage(p, ap);
        }
    }

    @Override
    protected Vec3 spawnOf(ArenaPlayer ap) {
        List<Vec3> spawns = ArenaData.gkSpawns();
        if (spawns.isEmpty()) return teamSpawn(RED);
        return spawns.get(level.random.nextInt(spawns.size()));
    }

    @Override
    protected List<ScoreLine> scoreLines(ArenaPlayer viewer) {
        List<Progress> list = new ArrayList<>(progress.values());
        list.sort((a, b) -> a.stage != b.stage ? Integer.compare(b.stage, a.stage) : Integer.compare(b.kills * needed(a), a.kills * needed(b)));
        List<ScoreLine> out = new ArrayList<>();
        Progress mine = progress.get(viewer.teamKey);
        for (int i = 0; i < list.size() && out.size() < 6; i++) out.add(line(list.get(i)));
        if (mine != null && list.indexOf(mine) >= 6) out.add(line(mine));
        return out;
    }

    private ScoreLine line(Progress pr) {
        String cat = stages() == 0 ? "" : order.get(Math.min(stages() - 1, pr.stage - 1)).name();
        return new ScoreLine(pr.color, pr.label, pr.stage, stages(), cat + " " + pr.kills + "/" + needed(pr));
    }

    @Override
    protected String statusLine(ArenaPlayer viewer) {
        Progress pr = progress.get(viewer.teamKey);
        if (pr == null || stages() == 0) return "";
        String next = pr.stage < stages() ? "　下一階段：" + order.get(pr.stage).name() : "　最後一階段！";
        return "第 " + pr.stage + "／" + stages() + " 階段：" + order.get(pr.stage - 1).name() + "　擊殺 " + pr.kills + "/" + needed(pr) + next;
    }

    @Override
    protected String scoreSummary() {
        List<Progress> list = new ArrayList<>(progress.values());
        list.sort((a, b) -> Integer.compare(b.stage, a.stage));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            if (i > 0) sb.append("　");
            sb.append(list.get(i).label).append(" 第").append(Math.min(list.get(i).stage, stages())).append("階");
        }
        return sb.toString();
    }
}
