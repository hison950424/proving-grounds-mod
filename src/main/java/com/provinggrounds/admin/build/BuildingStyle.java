package com.provinggrounds.admin.build;

/**
 * 檔案: BuildingStyle.java
 * 目的: 大逃殺建築生成工具的 8 種建築風格分類（共 28 棟大型／超大型無木箱建築）。
 */
public enum BuildingStyle {
    MODERN("modern", "現代軍事", "適合：經典、通用城市地圖", 0xFF60B8FF),
    INDUSTRIAL("industrial", "工業倉庫", "適合：經典、普羅米修斯禁區、碼頭空地", 0xFFE8A040),
    JAPANESE("japanese", "和風木造", "適合：櫻峰之城、山吹城", 0xFFFF88A8),
    SCIFI("scifi", "廢土科幻", "適合：腐蝕之地、普羅米修斯禁區", 0xFF60E8B8),
    CIVIC("civic", "都市地標", "適合：經典、大型城鎮核心跳點", 0xFF70D8FF),
    MEDIEVAL("medieval", "歐式古城", "適合：丘陵、城堡與古典街區", 0xFFD8B060),
    DESERT("desert", "沙漠戰區", "適合：荒漠、廢土與古遺跡地形", 0xFFF0C850),
    MARITIME("maritime", "海港極地", "適合：海岸港口、雪山與特殊巨構", 0xFF88C8FF);

    public final String id, displayName, mapHint;
    public final int color;

    BuildingStyle(String id, String displayName, String mapHint, int color) {
        this.id = id;
        this.displayName = displayName;
        this.mapHint = mapHint;
        this.color = color;
    }
}
