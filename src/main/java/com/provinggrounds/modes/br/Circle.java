package com.provinggrounds.modes.br;

import net.minecraft.util.RandomSource;

/**
 * 檔案: Circle.java
 * 目的: 縮圈時間軸。每個階段：先靜止「階段間隔」秒（顯示下一圈），再縮到下一圈。
 *       縮圈時間 = 階段間隔；若圈邊移動速度會超過上限（BR 疾跑速度），就拉長縮圈時間。
 *       圈邊最快速度 = 半徑縮減 + 圈心位移，兩者一起算，保證全速疾跑的玩家永遠跑得贏圈。
 *       下一圈圈心在目前圈內隨機（完全包含在目前圈內），不硬編碼中心點。
 *       計畫書 7 B 決賽圈：方案的各階段（半徑大於 7.5 格的）之後，最後一階段縮到固定半徑 FINAL_RADIUS（7.5 格），
 *       接著是「決賽圈」：停留 finalHold 秒，再縮到 0。決賽圈不算在 totalStages（空投時間窗照舊到最後一階段為止）。
 */
public class Circle {

    public enum Event { NONE, SHRINK_STARTED, SHRINK_ENDED }

    /** 決賽圈的固定半徑（格）。 */
    public static final double FINAL_RADIUS = 7.5;

    /** 每個階段縮到的半徑（格）：方案的階段、7.5 格、0（決賽圈）。 */
    private final double[] radii;
    private final int intervalTicks, finalHoldTicks;
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
                  int intervalSeconds, int finalHoldSeconds, double maxEdgeSpeedPerSecond, RandomSource random) {
        this.cx = centerX;
        this.cz = centerZ;
        this.r = mapRadius;
        this.radii = stageRadii(mapRadius, ratios);
        this.intervalTicks = Math.max(1, intervalSeconds * 20);
        this.finalHoldTicks = Math.max(0, finalHoldSeconds * 20);
        this.maxEdgeSpeedPerTick = Math.max(0.001, maxEdgeSpeedPerSecond / 20.0);
        this.random = random;
        this.ticksLeft = intervalTicks;
        planNext();
    }

    /** 方案比例 → 各階段半徑：去掉不大於 7.5 格的（包含原本最後的 0），再接上 7.5 格與 0。 */
    public static double[] stageRadii(double mapRadius, double[] ratios) {
        java.util.List<Double> out = new java.util.ArrayList<>();
        for (double q : ratios) {
            double rr = mapRadius * q;
            if (rr > FINAL_RADIUS + 0.01 && (out.isEmpty() || rr < out.get(out.size() - 1))) out.add(rr);
        }
        out.add(FINAL_RADIUS);
        out.add(0.0);
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** 第 i 階段縮圈前的等待：決賽圈（最後一段）是 finalHold，其他是階段間隔。 */
    private int waitTicks(int i) { return i == radii.length - 1 ? finalHoldTicks : intervalTicks; }

    private void planNext() {
        if (stageIndex >= radii.length) { toR = -1; return; }
        toR = radii[stageIndex];
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
                ticksLeft = finished() ? 0 : Math.max(1, waitTicks(stageIndex));
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

    public boolean finished()     { return stageIndex >= radii.length; }
    /** 決賽圈中（縮到 7.5 格之後的停留、與縮到 0 的過程）。 */
    public boolean inFinal()      { return stageIndex == radii.length - 1; }
    /** 已經縮完的階段數。 */
    public int completedStages()  { return stageIndex; }
    public boolean isShrinking()  { return shrinking; }
    /** 第一次縮圈已經開始（開始前沒有實際邊界：不畫白牆、不扣圈外血）。 */
    public boolean started()      { return shrinking || stageIndex > 0; }
    /** 一般階段數（含縮到 7.5 格的最後一階段，不含決賽圈）。 */
    public int totalStages()      { return radii.length - 1; }
    /** 顯示用階段編號（1 起算）；決賽圈與全部縮完後 = totalStages + 1（HUD 顯示「決賽圈」）。 */
    public int displayStage()     { return Math.min(stageIndex + 1, radii.length); }
    /** 傷害用階段索引（0 起算；決賽圈照規則再多一階）。 */
    public int damageStageIndex() { return Math.max(0, Math.min(stageIndex, radii.length - 1)); }
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
