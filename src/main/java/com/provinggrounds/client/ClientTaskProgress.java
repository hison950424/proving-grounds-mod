package com.provinggrounds.client;

import com.provinggrounds.network.TaskProgressPacket;

import javax.annotation.Nullable;

/**
 * 檔案: ClientTaskProgress.java
 * 目的: 客戶端接收伺服器耗時作業進度（生成地圖、修復光照、繪製俯視圖、轉換掃描、黑曜地堡建置），
 *       供 HUD 在畫面上方顯示即時進度條與目前階段說明；完成後保留 2.5 秒再自動淡出。
 */
public final class ClientTaskProgress {

    private static volatile boolean active;
    private static volatile String title = "";
    private static volatile String stage = "";
    private static volatile float progress;
    private static volatile boolean done;
    private static volatile long updatedAtMs;
    private static volatile long doneAtMs;

    private ClientTaskProgress() {}

    public static void update(TaskProgressPacket p) {
        if (!p.active()) {
            clear();
            return;
        }
        active = true;
        title = p.title();
        stage = p.stage();
        progress = Math.max(0f, Math.min(1f, p.progress()));
        done = p.done();
        updatedAtMs = System.currentTimeMillis();
        if (done) {
            if (doneAtMs == 0L) doneAtMs = updatedAtMs;
        } else {
            doneAtMs = 0L;
        }
    }

    public static void clear() {
        active = false;
        title = "";
        stage = "";
        progress = 0f;
        done = false;
        updatedAtMs = 0L;
        doneAtMs = 0L;
    }

    public record Snapshot(String title, String stage, float progress, boolean done, float alpha) {}

    @Nullable
    public static Snapshot snapshot() {
        if (!active) return null;
        long now = System.currentTimeMillis();
        if (done) {
            long age = now - doneAtMs;
            if (age > 2600L) {
                active = false;
                return null;
            }
            float alpha = age > 1800L ? Math.max(0f, (2600L - age) / 800f) : 1f;
            return new Snapshot(title, stage, 1f, true, alpha);
        }
        if (now - updatedAtMs > 15_000L) {
            active = false;
            return null;
        }
        return new Snapshot(title, stage, progress, false, 1f);
    }
}
