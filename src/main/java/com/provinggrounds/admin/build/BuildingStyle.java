package com.provinggrounds.admin.build;

/**
 * 檔案: BuildingStyle.java
 * 目的: 大逃殺建築生成工具的 4 種建築風格分類（每種風格各 3 棟大型／超大型建築，共 12 棟）。
 */
public enum BuildingStyle {
    MODERN("modern", "現代軍事", "適合：經典、通用城市地圖", 0xFF60B8FF),
    INDUSTRIAL("industrial", "工業倉庫", "適合：經典、普羅米修斯禁區、碼頭空地", 0xFFE8A040),
    JAPANESE("japanese", "和風木造", "適合：櫻峰之城、山吹城", 0xFFFF88A8),
    SCIFI("scifi", "廢土科幻", "適合：腐蝕之地、普羅米修斯禁區", 0xFF60E8B8);

    public final String id, displayName, mapHint;
    public final int color;

    BuildingStyle(String id, String displayName, String mapHint, int color) {
        this.id = id;
        this.displayName = displayName;
        this.mapHint = mapHint;
        this.color = color;
    }
}
