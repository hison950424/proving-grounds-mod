package com.provinggrounds.core;

import com.provinggrounds.network.PGNetwork;
import com.provinggrounds.network.TaskProgressPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * 檔案: TaskProgress.java
 * 目的: 統一管理所有耗時背景作業（地形生成、建造、光照修復、俯視圖、掃描）的即時進度條推播，
 *       透過節流（至少 150ms 或階段文字改變才重發）把狀態送到所有在線玩家畫面上。
 */
public final class TaskProgress {

    private static long lastSentMs;
    private static String lastTitle = "";
    private static String lastStage = "";
    private static float lastProgress = -1f;

    private TaskProgress() {}

    public static void update(@Nullable MinecraftServer server, String title, String stage, float progress) {
        if (server == null) return;
        float clamped = Math.max(0f, Math.min(1f, progress));
        long now = System.currentTimeMillis();
        boolean changedStage = !title.equals(lastTitle) || !stage.equals(lastStage);
        if (!changedStage && now - lastSentMs < 150L && Math.abs(clamped - lastProgress) < 0.015f) {
            return;
        }
        lastSentMs = now;
        lastTitle = title;
        lastStage = stage;
        lastProgress = clamped;
        sendAll(server, new TaskProgressPacket(true, title, stage, clamped, false));
    }

    public static void done(@Nullable MinecraftServer server, String title, String finalStage) {
        if (server == null) return;
        lastSentMs = System.currentTimeMillis();
        lastTitle = title;
        lastStage = finalStage;
        lastProgress = 1f;
        sendAll(server, new TaskProgressPacket(true, title, finalStage, 1f, true));
    }

    public static void clear(@Nullable MinecraftServer server) {
        lastTitle = "";
        lastStage = "";
        lastProgress = -1f;
        if (server == null) return;
        sendAll(server, new TaskProgressPacket(false, "", "", 0f, false));
    }

    private static void sendAll(MinecraftServer server, TaskProgressPacket pkt) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PGNetwork.sendTo(p, pkt);
        }
    }
}
