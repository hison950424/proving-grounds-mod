package com.provinggrounds.modes.arena;

import com.provinggrounds.compat.GunCompat;
import com.provinggrounds.core.GameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import com.provinggrounds.network.ArenaStatePacket;
import java.util.*;

/**
 * 檔案: DomMode.java
 * 目的: 佔點（照舊版 gamemode/dom）。回合制：結算 → 準備（傳回基地、出口封住、可以買東西）→ 戰鬥。
 *   每回合隨機開一個據點（90 秒時 50% 機率再開一個），前 30 秒不能佔。
 *   一隊單獨在點上每秒進度 ±1（人多不加速），到上限＝佔領成功、贏本回合。
 *   回合中死了不會重生，改成旁觀隊友。全滅、時間到也會結束回合。先到目標分數的隊伍贏。
 */
public final class DomMode extends ArenaMode {

    private enum Phase { RESULT, PREP, ROUND }

    /** 一個據點。progress：正 = 紅隊、負 = 藍隊。 */
    private static final class Point {
        final String name;
        final Vec3 center;
        final int radius;
        @Nullable final BlockPos[] floor;
        boolean open;
        int progress, red, blue;

        Point(String name, Vec3 center, int radius, @Nullable BlockPos[] floor) {
            this.name = name; this.center = center; this.radius = radius; this.floor = floor;
        }

        boolean contains(ServerPlayer p) {
            return Math.abs(p.getX() - center.x) <= radius + 0.5 && Math.abs(p.getZ() - center.z) <= radius + 0.5
                && p.getY() >= center.y - 1 && p.getY() <= center.y + 3;
        }
    }

    private final int targetScore;
    private final List<Point> points = new ArrayList<>();
    private Phase phase = Phase.RESULT;
    private int phaseSeconds, roundSeconds, round;
    private int redScore, blueScore;
    private boolean secondOpen, firstBlood;
    private final int[] winStreak = new int[3], lossStreak = new int[3];

    public DomMode(MinecraftServer server, int targetScore) {
        super(server);
        this.targetScore = Math.max(100, targetScore);
        for (String line : cfg.domPoints.get()) {
            String[] parts = line.split("\\|");
            if (parts.length < 3) continue;
            Vec3 c = ArenaConfig.vec(parts[1]);
            if (c == null) continue;
            int r;
            try { r = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException e) { continue; }
            points.add(new Point(parts[0].trim(), c, r, parts.length > 3 ? ArenaConfig.box(parts[3]) : null));
        }
    }

    @Override public GameManager.GameMode kind() { return GameManager.GameMode.DOM; }
    @Override public String title() { return "佔點・第 " + Math.max(1, round) + " 回合"; }
    @Override protected boolean keepInventory() { return false; }
    @Override protected int moneyCap() { return cfg.domMoneyCap.get(); }
    /** 可以丟槍給隊友。 */
    @Override protected boolean allowToss(ItemStack stack) { return GunCompat.gunId(stack) != null; }

    private int captureMax() { return cfg.domCaptureSeconds.get(); }

    /** 計畫書 8：佔點沒有職業，改成自選三個特殊物品（special/DomGear）；開局發固定的基本裝備。 */
    @Override public boolean usesClasses() { return false; }
    @Override protected boolean basicKit() { return true; }

    /** 正在戰鬥（不是準備、結算）。特殊物品裝備只有戰鬥中能用、準備時才能換。 */
    public boolean fighting() { return phase == Phase.ROUND; }

    @Override
    protected void onStart() {
        for (Point pt : points) setFloor(pt, false);
        com.provinggrounds.special.DomGear.startMatch(online());
        enterPrep();
    }

    @Override
    protected void onFinish() {
        fill(ArenaConfig.boxes(cfg.barriers.get()), Blocks.AIR.defaultBlockState(), true);
        for (Point pt : points) setFloor(pt, false);
        com.provinggrounds.special.DomGear.endMatch(server);
    }

    private void setFloor(Point pt, boolean open) {
        if (pt.floor == null) return;
        fill(List.<BlockPos[]>of(pt.floor), (open ? Blocks.GOLD_BLOCK : Blocks.IRON_BLOCK).defaultBlockState(), false);
    }

    // ── 回合流程 ────────────────────────────────────────────────────────────

    /** 撿裝／準備：清地上、預告據點、傳回基地、封出口、補消耗品。 */
    private void enterPrep() {
        clearFloorItems();
        for (Point pt : points) { pt.open = false; pt.progress = 0; setFloor(pt, false); }
        openRandomPoint(false);
        phase = Phase.PREP;
        phaseSeconds = round == 0 ? cfg.domFirstPrepSeconds.get() : cfg.domPrepSeconds.get();
        round++;
        secondOpen = level.random.nextBoolean();
        fill(ArenaConfig.boxes(cfg.barriers.get()), Blocks.BARRIER.defaultBlockState(), true);
        clearSupplyChests();
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            ap.alive = true;
            ap.pointEarn = 0;
            p.setGameMode(GameType.ADVENTURE);
            p.setCamera(p);
            teleport(p, spawnOf(ap));
            prepare(p, ap, !hasGun(p)); // 上一回合死掉、東西掉光的人重新發起始槍
            refillConsumables(p, ap, true);
        }
        String names = String.join(" + ", points.stream().filter(pt -> pt.open).map(pt -> pt.name + " 點").toList());
        broadcastTitle(Component.literal("第 " + round + " 回合").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.literal("本回合據點：" + names).withStyle(ChatFormatting.YELLOW), 60);
    }

    private void startRound() {
        phase = Phase.ROUND;
        roundSeconds = 0;
        firstBlood = false;
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            ap.alive = true;
            p.setGameMode(GameType.ADVENTURE);
            refillConsumables(p, ap, true);
        }
        fill(ArenaConfig.boxes(cfg.barriers.get()), Blocks.AIR.defaultBlockState(), true);
        com.provinggrounds.special.DomGear.resetCooldowns(); // 每回合特殊物品冷卻歸零（計畫書 8）
        broadcastTitle(Component.literal("⚔ 戰鬥開始！").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
            Component.literal("佔領將於 " + cfg.domFreezeSeconds.get() + " 秒後開放").withStyle(ChatFormatting.YELLOW), 40);
        playAll(SoundEvents.RAID_HORN.value(), 0.6f, 1.2f);
    }

    /** 從關閉的據點隨機開一個。 */
    private void openRandomPoint(boolean announce) {
        List<Point> closed = points.stream().filter(pt -> !pt.open).toList();
        if (closed.isEmpty()) return;
        Point pt = closed.get(level.random.nextInt(closed.size()));
        pt.open = true;
        pt.progress = 0;
        setFloor(pt, true);
        if (announce) {
            broadcast(Component.literal("[系統] 僵持過久！" + pt.name + " 點已開放").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            playAll(SoundEvents.BELL_BLOCK, 1f, 1f);
        }
    }

    @Override
    protected void tickRules() {
        if (phase == Phase.ROUND) {
            for (Point pt : points) {
                pt.red = 0; pt.blue = 0;
                if (!pt.open) continue;
                for (ServerPlayer p : online()) {
                    ArenaPlayer ap = players.get(p.getUUID());
                    if (!ap.alive || p.isSpectator() || !p.isAlive() || !pt.contains(p)) continue;
                    if (ap.team == RED) pt.red++; else if (ap.team == BLUE) pt.blue++;
                }
            }
        }
        if (ticks % 20 != 0) return;
        switch (phase) {
            case RESULT -> { if (--phaseSeconds <= 0) enterPrep(); }
            case PREP -> {
                phaseSeconds--;
                if (phaseSeconds <= 0) startRound();
            }
            case ROUND -> secondRound();
        }
        if (phase == Phase.ROUND) {
            keepSpectating();
            checkWipe(); // 有人離線也要檢查全滅
        }
    }

    /** 戰鬥中每秒：據點進度、佔點收入、隊伍分數、第二據點、時間到。 */
    private void secondRound() {
        roundSeconds++;
        if (roundSeconds == cfg.domSecondPointAt.get() && secondOpen && points.stream().filter(pt -> pt.open).count() == 1) {
            openRandomPoint(true);
        }
        int max = captureMax();
        for (Point pt : points) {
            if (!pt.open) continue;
            boolean contested = pt.red > 0 && pt.blue > 0;
            if (roundSeconds > cfg.domFreezeSeconds.get() && !contested) {
                pt.progress += (pt.red > 0 ? 1 : 0) - (pt.blue > 0 ? 1 : 0);
                pt.progress = Math.max(-max, Math.min(max, pt.progress));
            }
            // 站在開放據點上：每秒 +$2
            for (ServerPlayer p : online()) {
                ArenaPlayer ap = players.get(p.getUUID());
                if (ap.alive && !p.isSpectator() && pt.contains(p)) {
                    addMoney(p, ap, cfg.domPointIncome.get(), null);
                    ap.pointEarn += cfg.domPointIncome.get();
                }
            }
            if (pt.progress > 0) redScore++;
            else if (pt.progress < 0) blueScore++;
            if (checkMatchWin()) return;
            if (pt.progress >= max) { roundWon(RED, "成功佔領 " + pt.name + " 點！", pt.name + " 點已被佔領！"); return; }
            if (pt.progress <= -max) { roundWon(BLUE, "成功佔領 " + pt.name + " 點！", pt.name + " 點已被佔領！"); return; }
        }
        if (roundSeconds >= cfg.domRoundSeconds.get()) resolveByLead("時間結束！");
    }

    @Override
    protected void onDeath(ServerPlayer victim, ArenaPlayer v) {
        if (phase != Phase.ROUND) return;
        v.alive = false;
    }

    @Override
    protected void onKill(ServerPlayer killer, ArenaPlayer k, ServerPlayer victim, ArenaPlayer v) {
        if (phase != Phase.ROUND) return;
        addMoney(killer, k, cfg.domKillMoney.get(), "擊殺 " + v.name);
        if (!firstBlood) {
            firstBlood = true;
            addMoney(killer, k, cfg.domFirstBloodMoney.get(), "首殺！");
            broadcast(Component.literal("[系統] " + k.name + " 拿下本回合首殺！").withStyle(ChatFormatting.GOLD));
        }
    }

    /** 死掉之後（下一 tick 重生時）檢查全滅。 */
    @Override
    protected void respawn(ServerPlayer p, ArenaPlayer ap) {
        if (phase != Phase.ROUND || ap.alive) {
            super.respawn(p, ap);
            if (!hasGun(p)) prepare(p, ap, true);
            return;
        }
        p.setGameMode(GameType.SPECTATOR);
        ServerPlayer mate = aliveMate(ap, null);
        if (mate != null) {
            teleport(p, mate.position());
            p.setCamera(mate);
        } else {
            teleport(p, spawnOf(ap));
        }
        p.sendSystemMessage(Component.literal("[佔點] 你陣亡了，下一回合才會復活。按 Shift 切換觀看的隊友。").withStyle(ChatFormatting.GRAY));
        checkWipe();
    }

    /** 旁觀的人一直看著活著的隊友（按 Shift 離開 → 換下一個隊友）。 */
    private void keepSpectating() {
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            if (ap.alive || !p.isSpectator()) continue;
            net.minecraft.world.entity.Entity cam = p.getCamera();
            boolean watching = cam instanceof ServerPlayer t && t != p && t.isAlive() && !t.isSpectator();
            if (watching) continue;
            ServerPlayer next = aliveMate(ap, cam instanceof ServerPlayer t ? t : null);
            if (next != null) {
                teleport(p, next.position());
                p.setCamera(next);
            }
        }
    }

    /** 活著的隊友（after 不是 null 就找它之後的下一個）。 */
    @Nullable
    private ServerPlayer aliveMate(ArenaPlayer ap, @Nullable ServerPlayer after) {
        List<ServerPlayer> alive = new ArrayList<>();
        for (ServerPlayer o : online(ap.team)) {
            ArenaPlayer oa = players.get(o.getUUID());
            if (oa != ap && oa.alive && o.isAlive() && !o.isSpectator()) alive.add(o);
        }
        if (alive.isEmpty()) return null;
        int i = after == null ? -1 : alive.indexOf(after);
        return alive.get((i + 1) % alive.size());
    }

    private int aliveCount(int team) {
        int n = 0;
        for (ServerPlayer p : online(team)) if (players.get(p.getUUID()).alive) n++;
        return n;
    }

    private void checkWipe() {
        if (phase != Phase.ROUND) return;
        // 單人測試：有一隊根本沒有人時不判全滅（不然回合一開始就結束），回合照佔點／時間結束
        if (online(RED).isEmpty() || online(BLUE).isEmpty()) return;
        int red = aliveCount(RED), blue = aliveCount(BLUE);

        if (red == 0 && blue == 0) resolveByLead("雙方同時全滅！");
        else if (red == 0) roundWon(BLUE, "敵方全員陣亡！", "我方全員陣亡！");
        else if (blue == 0) roundWon(RED, "敵方全員陣亡！", "我方全員陣亡！");
    }

    /** 開放中的據點：光柱顏色 無人＝白、紅領先＝紅、藍領先＝藍、爭奪中＝黃（客戶端閃爍）。 */
    @Override
    protected List<ArenaStatePacket.Marker> markers(ArenaPlayer viewer) {
        List<ArenaStatePacket.Marker> out = super.markers(viewer);
        for (Point pt : points) {
            if (!pt.open) continue;
            boolean contested = pt.red > 0 && pt.blue > 0;
            int color = contested ? 0xFFD030 : pt.progress > 0 ? 0xFF4040 : pt.progress < 0 ? 0x4080FF : 0xFFFFFF;
            out.add(new ArenaStatePacket.Marker((byte) 0, pt.name, pt.center.x, pt.center.y, pt.center.z, pt.radius, color, contested));
        }
        return out;
    }

    @Override
    public String debug(String action, String arg, int n) {

        if (action.equals("skipround")) {
            if (phase != Phase.ROUND) return "現在不是戰鬥階段";
            resolveByLead("管理員結束回合");
            return "已結束本回合";
        }
        return super.debug(action, arg, n);
    }

    /** 依開放據點的領先數判定（時間到、同時全滅）；一樣多就平手。 */
    private void resolveByLead(String why) {

        int red = 0, blue = 0;
        for (Point pt : points) {
            if (!pt.open) continue;
            if (pt.progress > 0) red++; else if (pt.progress < 0) blue++;
        }
        if (red > blue) roundWon(RED, why + "我方佔領領先", why + "敵方佔領領先");
        else if (blue > red) roundWon(BLUE, why + "我方佔領領先", why + "敵方佔領領先");
        else roundTie(why);
    }

    private void roundWon(int winner, String winMsg, String loseMsg) {
        int loser = winner == RED ? BLUE : RED;
        if (winner == RED) redScore += cfg.domRoundScore.get(); else blueScore += cfg.domRoundScore.get();
        for (ServerPlayer p : online()) {
            boolean won = players.get(p.getUUID()).team == winner;
            p.sendSystemMessage(Component.literal("[系統] " + (won ? winMsg + "本回合勝利 +" + cfg.domRoundScore.get() + " 分" : loseMsg + "本回合失敗"))
                .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
            // 回合勝負音效（計畫書 6 A4）：贏＝明亮的升級聲、輸＝低沉的號角
            if (won) p.playNotifySound(SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.MASTER, 0.8f, 1.2f);
            else p.playNotifySound(SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.MASTER, 0.5f, 0.7f);
        }
        int win = ArenaConfig.at(cfg.domWinMoney.get(), winStreak[winner]);
        int loss = ArenaConfig.at(cfg.domLossMoney.get(), lossStreak[loser]);
        addMoneyTeam(winner, win, "本回合勝利" + (winStreak[winner] > 0 ? "（連勝遞減）" : ""), true);
        addMoneyTeam(loser, loss, "本回合失敗" + (lossStreak[loser] > 0 ? "（連敗加成）" : ""), true);
        winStreak[winner]++; lossStreak[winner] = 0;
        lossStreak[loser]++; winStreak[loser] = 0;
        endRound();
    }

    private void roundTie(String why) {
        broadcast(Component.literal("[系統] " + why + "佔領平手，本回合平局").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        playAll(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 0.8f); // 平局音效
        addMoneyTeam(RED, cfg.domTieMoney.get(), "本回合平局", true);
        addMoneyTeam(BLUE, cfg.domTieMoney.get(), "本回合平局", true);
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            if (ap.alive) addMoney(p, ap, -cfg.domTieAlivePenalty.get(), "本回合存活");
            else addMoney(p, ap, cfg.domTieDeadPay.get(), "本回合陣亡補償");
        }
        int red = aliveCount(RED), blue = aliveCount(BLUE);
        if (red != blue) {
            int team = red > blue ? RED : BLUE;
            if (team == RED) redScore += cfg.domSurviveScore.get(); else blueScore += cfg.domSurviveScore.get();
            broadcast(Component.literal("[得分] " + teamName(team) + "存活優勢 +" + cfg.domSurviveScore.get() + " 分").withStyle(ChatFormatting.AQUA));
        }
        endRound();
    }

    private void endRound() {
        for (ServerPlayer p : online()) {
            ArenaPlayer ap = players.get(p.getUUID());
            if (ap.pointEarn > 0) p.sendSystemMessage(Component.literal("[金錢] 本回合佔點收入 +$" + ap.pointEarn).withStyle(ChatFormatting.GREEN));
        }
        for (Point pt : points) { pt.open = false; setFloor(pt, false); }
        phase = Phase.RESULT;
        phaseSeconds = cfg.domResultSeconds.get();
        checkMatchWin();
    }

    private boolean checkMatchWin() {
        int team = redScore >= targetScore ? RED : blueScore >= targetScore ? BLUE : 0;
        if (team == 0) return false;
        Set<UUID> winners = new HashSet<>();
        for (ArenaPlayer ap : players.values()) if (ap.team == team) winners.add(ap.id);
        endMatch(Component.literal(teamName(team) + "獲勝！").withStyle(teamColor(team), ChatFormatting.BOLD), winners);
        return true;
    }

    // ── HUD ─────────────────────────────────────────────────────────────────

    @Override protected Vec3 spawnOf(ArenaPlayer ap) { return randomTeamSpawn(ap.team); }

    /** 佔點回合中死亡是旁觀到下一回合，不用重生倒數。 */
    @Override protected int respawnDelaySeconds() { return 0; }

    @Override
    protected List<ScoreLine> scoreLines(ArenaPlayer viewer) {
        return List.of(new ScoreLine(rgb(ChatFormatting.RED), "紅隊", redScore, targetScore, "存活 " + aliveCount(RED)),
            new ScoreLine(rgb(ChatFormatting.BLUE), "藍隊", blueScore, targetScore, "存活 " + aliveCount(BLUE)));
    }

    @Override
    protected int timerSeconds() {
        return phase == Phase.ROUND ? Math.max(0, cfg.domRoundSeconds.get() - roundSeconds) : Math.max(0, phaseSeconds);
    }

    @Override
    protected String timerLabel() {
        return switch (phase) {
            case RESULT -> "回合結算";
            case PREP -> "準備（可以購買）";
            case ROUND -> roundSeconds < cfg.domFreezeSeconds.get() ? "佔領凍結 " + (cfg.domFreezeSeconds.get() - roundSeconds) + " 秒" : "回合時間";
        };
    }

    @Override
    protected String statusLine(ArenaPlayer viewer) {
        StringBuilder sb = new StringBuilder();
        int max = captureMax();
        for (Point pt : points) {
            if (!pt.open) continue;
            if (sb.length() > 0) sb.append("　");
            String owner = pt.progress > 0 ? "§c紅" : pt.progress < 0 ? "§9藍" : "§7中立";
            sb.append("§e◆").append(pt.name).append("點 ").append(owner).append("§f ").append(Math.abs(pt.progress)).append("/").append(max);
            if (pt.red > 0 && pt.blue > 0) sb.append(" §6⚠爭奪中");
        }
        return sb.length() == 0 ? "" : sb.toString();
    }

    @Override
    protected String scoreSummary() { return "紅隊 " + redScore + " : " + blueScore + " 藍隊（" + round + " 回合）"; }
}
