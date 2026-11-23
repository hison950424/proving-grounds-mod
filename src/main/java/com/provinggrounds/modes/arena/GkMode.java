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
        int stage = 1, kills, needed, members;
        Progress(String key, String label, int color) { this.key = key; this.label = label; this.color = color; }
    }

    private static final String[] ATTACH_TYPES = { "mag", "scope", "muzzle", "stock", "grip", "laser" };

    private final int multiplier;
    private final List<ArenaData.GkCategory> order = new ArrayList<>();
    private final List<ArenaData.GkEntry> weapons = new ArrayList<>();      // 每階段的武器
    private final List<List<String>> stageAttachments = new ArrayList<>();  // 每階段的配件
    private final Map<String, Progress> progress = new LinkedHashMap<>();
    private int armorTier;

    public GkMode(MinecraftServer server, int multiplier) {
        super(server);
        this.multiplier = Math.max(1, multiplier);
    }

    @Override public GameManager.GameMode kind() { return GameManager.GameMode.GK; }
    @Override public String title() { return "槍王之王"; }
    @Override public boolean usesShop() { return false; }
    @Override public boolean usesClasses() { return false; }

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
            pr.members++;
        }
        for (Progress pr : progress.values()) pr.needed = Math.max(1, pr.members * multiplier);
    }

    @Override
    protected void onStart() {
        Random rand = new Random();
        order.addAll(ArenaData.gkCategories());
        Collections.shuffle(order, rand);
        Map<String, List<String>> pools = ArenaData.gkAttachments();
        for (ArenaData.GkCategory c : order) {
            weapons.add(c.items().get(rand.nextInt(c.items().size())));
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
        if (ticks % 20 != 0) return;
        // 投擲物階段：用完補 1 個
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            Progress pr = progress.get(ap.teamKey);
            if (pr == null || stages() == 0 || p.isSpectator() || !p.isAlive()) continue;
            ArenaData.GkEntry e = weapons.get(Math.min(stages() - 1, pr.stage - 1));
            if (e.gun() == null && e.item() != null && e.item().startsWith("lrtactical:throwable")) {
                ItemStack t = RespawnKit.parse(e.item());
                if (!t.isEmpty() && count(p, t) == 0) give(p, t);
            }
            ensureOne(p, cfg.gkMedkit.get());
        }
    }

    @Override
    protected void onKill(ServerPlayer killer, ArenaPlayer k, ServerPlayer victim, ArenaPlayer v) {
        Progress pr = progress.get(k.teamKey);
        if (pr == null) return;
        pr.kills++;
        if (pr.kills < pr.needed) return;
        pr.kills = 0;
        pr.stage++;
        if (pr.stage > stages()) {
            Set<UUID> winners = new HashSet<>();
            for (ArenaPlayer ap : players.values()) if (ap.teamKey.equals(pr.key)) winners.add(ap.id);
            endMatch(Component.literal(pr.label + " 成為槍王！").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), winners);
            return;
        }
        String cat = order.get(pr.stage - 1).name();
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            if (!ap.teamKey.equals(pr.key)) continue;
            giveStage(p, ap);
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.MASTER, 1f, 1.2f);
            p.displayClientMessage(Component.literal("晉級！第 " + pr.stage + " 階段：" + cat).withStyle(ChatFormatting.GOLD), true);
        }
        broadcast(Component.literal("[槍王] " + pr.label + " 晉級到第 " + pr.stage + "／" + stages() + " 階段（" + cat + "）").withStyle(ChatFormatting.YELLOW));
    }

    /** 重生：隨機重生點、血量 100、補彈藥箱與醫療包（武器 keepInventory 保留）。 */
    @Override
    protected void respawn(ServerPlayer p, ArenaPlayer ap) {
        super.respawn(p, ap);
        if (!hasGun(p) && count(p, RespawnKit.parse("lrtactical:melee")) == 0) giveStage(p, ap);
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
        list.sort((a, b) -> a.stage != b.stage ? Integer.compare(b.stage, a.stage) : Integer.compare(b.kills * a.needed, a.kills * b.needed));
        List<ScoreLine> out = new ArrayList<>();
        Progress mine = progress.get(viewer.teamKey);
        for (int i = 0; i < list.size() && out.size() < 6; i++) out.add(line(list.get(i)));
        if (mine != null && list.indexOf(mine) >= 6) out.add(line(mine));
        return out;
    }

    private ScoreLine line(Progress pr) {
        String cat = stages() == 0 ? "" : order.get(Math.min(stages() - 1, pr.stage - 1)).name();
        return new ScoreLine(pr.color, pr.label, pr.stage, stages(), cat + " " + pr.kills + "/" + pr.needed);
    }

    @Override
    protected String statusLine(ArenaPlayer viewer) {
        Progress pr = progress.get(viewer.teamKey);
        if (pr == null || stages() == 0) return "";
        String next = pr.stage < stages() ? "　下一階段：" + order.get(pr.stage).name() : "　最後一階段！";
        return "第 " + pr.stage + "／" + stages() + " 階段：" + order.get(pr.stage - 1).name() + "　擊殺 " + pr.kills + "/" + pr.needed + next;
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
