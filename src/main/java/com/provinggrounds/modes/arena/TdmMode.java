package com.provinggrounds.modes.arena;

import com.provinggrounds.core.GameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 檔案: TdmMode.java
 * 目的: 團隊死鬥（照舊版 gamemode/tdm）。紅藍兩隊，擊殺 +1 隊伍分數，先到目標或時間到分數高的贏。
 *   時間的 1/3、2/3 換階段（早→中→後），金錢倍率 ×1／×3／×9；落後隊伍有額外補貼。
 */
public final class TdmMode extends ArenaMode {

    private final int targetKills, totalSeconds;
    private int redKills, blueKills;
    private int stage; // 0 早期、1 中期、2 後期

    public TdmMode(MinecraftServer server, int targetKills, int minutes) {
        super(server);
        this.targetKills = Math.max(1, targetKills);
        this.totalSeconds = Math.max(60, minutes * 60);
    }

    @Override public GameManager.GameMode kind() { return GameManager.GameMode.TDM; }
    @Override public String title() { return "團隊死鬥・" + stageName(); }

    private String stageName() { return stage == 0 ? "早期" : stage == 1 ? "中期" : "後期"; }

    private int mult() { return ArenaConfig.at(cfg.tdmStageMultiplier.get(), stage); }

    private int timeOffset; // 測試指令 skipstage 往後跳的秒數

    private int elapsed() { return ticks / 20 + timeOffset; }

    @Override
    public String debug(String action, String arg, int n) {
        switch (action) {
            case "addkills" -> {
                int team = arg.toLowerCase().startsWith("b") || arg.contains("藍") ? BLUE : RED;
                if (team == RED) redKills += n; else blueKills += n;
                if (redKills >= targetKills) win(RED, "達到目標擊殺數！");
                else if (blueKills >= targetKills) win(BLUE, "達到目標擊殺數！");
                return "已加 " + n + " 擊殺給" + teamName(team);
            }
            case "skipstage" -> {
                int e = elapsed();
                int next = stage == 0 ? totalSeconds / 3 : stage == 1 ? totalSeconds * 2 / 3 : totalSeconds;
                timeOffset += Math.max(0, next - e);
                return "跳到下一階段";
            }
            default -> { return super.debug(action, arg, n); }
        }
    }


    @Override
    protected void onStart() {
        fill(ArenaConfig.boxes(cfg.barriers.get()), Blocks.AIR.defaultBlockState(), true); // 拆掉基地出口屏障
        clearSupplyChests();
        clearFloorItems();
    }

    @Override
    protected void tickRules() {
        if (ticks % 20 != 0) return;
        int e = elapsed();
        int newStage = e >= totalSeconds * 2 / 3 ? 2 : e >= totalSeconds / 3 ? 1 : 0;
        if (newStage != stage) {
            stage = newStage;
            int loser = losingTeam();
            broadcastTitle(Component.literal("進入" + stageName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal("金錢獎勵 ×" + mult()).withStyle(ChatFormatting.YELLOW), 40);
            playAll(SoundEvents.RAID_HORN.value(), 0.6f, 1f);
            if (loser != 0) {
                int bonus = ArenaConfig.at(cfg.tdmStageBonus.get(), stage - 1);
                addMoneyTeam(loser, bonus, "落後補貼（進入" + stageName() + "）", true);
            }
        }
        if (e >= totalSeconds) {
            if (redKills > blueKills) win(RED, "時間到！");
            else if (blueKills > redKills) win(BLUE, "時間到！");
            else endMatch(Component.literal("時間到！平手").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), Set.of());
        }
    }

    /** 擊殺數較少的隊伍（平手 = 0）。 */
    private int losingTeam() {
        return redKills < blueKills ? RED : blueKills < redKills ? BLUE : 0;
    }

    @Override
    protected void onDeath(ServerPlayer victim, ArenaPlayer v) {
        int m = mult();
        boolean losing = losingTeam() == v.team;
        addMoney(victim, v, cfg.tdmDeathMoney.get() * m + (losing ? cfg.tdmLoserBonus.get() * m : 0), "陣亡補貼");
    }

    @Override
    protected void onKill(ServerPlayer killer, ArenaPlayer k, ServerPlayer victim, ArenaPlayer v) {
        int m = mult();
        boolean losing = losingTeam() == k.team;
        if (k.team == RED) redKills++; else blueKills++;
        addMoney(killer, k, cfg.tdmKillMoney.get() * m + (losing ? cfg.tdmLoserBonus.get() * m : 0), "擊殺 " + v.name);
        refillConsumables(killer, k, false); // 擊殺補投擲物
        if (redKills >= targetKills) win(RED, "達到目標擊殺數！");
        else if (blueKills >= targetKills) win(BLUE, "達到目標擊殺數！");
    }

    private void win(int team, String why) {
        Set<UUID> winners = new HashSet<>();
        for (ArenaPlayer ap : players.values()) if (ap.team == team) winners.add(ap.id);
        endMatch(Component.literal(teamName(team) + "獲勝！").withStyle(teamColor(team), ChatFormatting.BOLD)
            .append(Component.literal(" " + why).withStyle(ChatFormatting.WHITE)), winners);
    }

    @Override
    protected void onRespawned(ServerPlayer p, ArenaPlayer ap) {
        refillConsumables(p, ap, false);
    }

    @Override protected Vec3 spawnOf(ArenaPlayer ap) { return randomTeamSpawn(ap.team); }

    @Override
    protected List<ScoreLine> scoreLines(ArenaPlayer viewer) {
        return List.of(new ScoreLine(rgb(ChatFormatting.RED), "紅隊", redKills, targetKills, ""),
            new ScoreLine(rgb(ChatFormatting.BLUE), "藍隊", blueKills, targetKills, ""));
    }

    @Override protected int timerSeconds() { return Math.max(0, totalSeconds - elapsed()); }
    @Override protected String timerLabel() { return "剩餘時間"; }

    @Override
    protected String statusLine(ArenaPlayer viewer) {
        return "金錢獎勵 ×" + mult() + (losingTeam() == viewer.team ? "　落後補貼中" : "");
    }

    @Override
    protected String scoreSummary() { return "紅隊 " + redKills + " : " + blueKills + " 藍隊"; }
}
