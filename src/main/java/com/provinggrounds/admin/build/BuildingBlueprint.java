package com.provinggrounds.admin.build;

/**
 * 檔案: BuildingBlueprint.java
 * 目的: 單一棟可生成建築的藍圖定義（名稱、風格、佔地長寬高、物資箱數量、說明與建構函式）。
 */
public record BuildingBlueprint(
    String id,
    BuildingStyle style,
    String name,
    String desc,
    int width,      // 左右總寬度（中心為 lx = 0）
    int depth,      // 前後總深度（lz = 0 為正門、lz = depth - 1 為後牆）
    int height,     // 總高度（ly = 0 為一樓地板）
    int lootCount,  // 內建自動登記的物資箱生成點數
    Builder builder
) {
    @FunctionalInterface
    public interface Builder {
        void build(BuildingCanvas c);
    }

    public int halfW() { return width / 2; }
    public String sizeText() { return width + "×" + depth + "×" + height; }
}
