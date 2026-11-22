package com.provinggrounds.modes.br;

import net.minecraft.util.RandomSource;

/**
 * 檔案: Circle.java
 * 目的: 縮圈時間軸。每個階段：先靜止「階段間隔」秒（顯示下一圈），再縮到下一圈。
 *       縮圈時間 = 階段間隔；若圈邊移動速度會超過上限（BR 疾跑速度），就拉長縮圈時間。
 *       圈邊最快速度 = 半徑縮減 + 圈心位移，兩者一起算，保證全速疾跑的玩家永遠跑得贏圈。
 *       下一圈圈心在目前圈內隨機（完全包含在目前圈內），不硬編碼中心點。
 */
public class Circle {

    public enum Event { NONE, SHRINK_STARTED, SHRINK_ENDED }

    private final double mapRadius;
    private final double[] ratios;
    private final int intervalTicks;
    private final double maxEdgeSpeedPerTick;
    private final RandomSource random;

    private double cx, cz, r;
    private double fromX, fromZ, fromR;
    private double toX, toZ, toR = -1;
    private int stageIndex = 0;      // 正在進行的階段（0 起算）；== ratios.length 代表全部縮完
    private boolean shrinking = false;
    private int ticksLeft;
    private int shrinkTotal = 1;

    /** 開場第一次縮圈前多等一段時間（設定 first_wait_extra_seconds）。只在建立後、開始倒數前呼叫。 */
    public void addInitialWait(int ticks) {
        if (ticks > 0) ticksLeft += ticks;
    }

    public Circle(double centerX, double centerZ, double mapRadius, double[] ratios,
                  int intervalSeconds, double maxEdgeSpeedPerSecond, RandomSource random) {
        this.cx = centerX;
        this.cz = centerZ;
        this.r = mapRadius;
        this.mapRadius = mapRadius;
        this.ratios = ratios;
        this.intervalTicks = Math.max(1, intervalSeconds * 20);
        this.maxEdgeSpeedPerTick = Math.max(0.001, maxEdgeSpeedPerSecond / 20.0);
        this.random = random;
        this.ticksLeft = intervalTicks;
        planNext();
    }

    private void planNext() {
        if (stageIndex >= ratios.length) { toR = -1; return; }
        toR = mapRadius * ratios[stageIndex];
        double maxOffset = Math.max(0, r - toR);
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(random.nextDouble()) * maxOffset; // 圓內均勻分布
        toX = cx + Math.cos(angle) * dist;
        toZ = cz + Math.sin(angle) * dist;
    }

    public Event tick() {
        if (finished()) return Event.NONE;
        ticksLeft--;
        if (!shrinking) {
            if (ticksLeft <= 0) {
                shrinking = true;
                fromX = cx; fromZ = cz; fromR = r;
                double edgeTravel = (fromR - toR) + Math.hypot(toX - fromX, toZ - fromZ);
                int minTicks = (int) Math.ceil(edgeTravel / maxEdgeSpeedPerTick);
                shrinkTotal = Math.max(intervalTicks, minTicks);
                ticksLeft = shrinkTotal;
                return Event.SHRINK_STARTED;
            }
        } else {
            double t = 1.0 - (double) ticksLeft / shrinkTotal;
            cx = fromX + (toX - fromX) * t;
            cz = fromZ + (toZ - fromZ) * t;
            r  = fromR + (toR - fromR) * t;
            if (ticksLeft <= 0) {
                cx = toX; cz = toZ; r = toR;
                shrinking = false;
                stageIndex++;
                planNext();
                ticksLeft = intervalTicks;
                return Event.SHRINK_ENDED;
            }
        }
        return Event.NONE;
    }

    public boolean isOutside(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz > r * r;
    }

    public double distanceOutside(double x, double z) {
        return Math.hypot(x - cx, z - cz) - r;
    }

    public boolean finished()     { return stageIndex >= ratios.length; }
    /** 已經縮完的階段數。 */
    public int completedStages()  { return stageIndex; }
    public boolean isShrinking()  { return shrinking; }
    public int totalStages()      { return ratios.length; }
    /** 顯示用階段編號（1 起算），全部縮完後停在最後一階段。 */
    public int displayStage()     { return Math.min(stageIndex + 1, ratios.length); }
    /** 傷害用階段索引（0 起算）。 */
    public int damageStageIndex() { return Math.max(0, Math.min(stageIndex, ratios.length - 1)); }
    public int secondsLeft()      { return finished() ? 0 : (ticksLeft + 19) / 20; }
    public int ticksLeft()        { return ticksLeft; }

    public double centerX() { return cx; }
    public double centerZ() { return cz; }
    public double radius()  { return r; }
    public double nextX()   { return toX; }
    public double nextZ()   { return toZ; }
    /** 下一圈半徑；沒有下一圈時為 -1。 */
    public double nextRadius() { return finished() ? -1 : toR; }
}
