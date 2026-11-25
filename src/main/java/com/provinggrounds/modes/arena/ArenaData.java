package com.provinggrounds.modes.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.provinggrounds.ProvingGroundsMod;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 檔案: ArenaData.java
 * 目的: 讀取模組內建的資料（由 tools/make_arena_data.py 從舊版資料包產生）：
 *   data/provinggrounds/arena/shop.json — 商店商品（伺服器與客戶端都讀同一份，不用另外傳）
 *   data/provinggrounds/arena/gk.json   — 槍王之王的九類武器、配件池、126 個重生點
 */
public final class ArenaData {

    /** 商店商品。price = 一般價格、free = 自由人價格；item 格式同 /give。 */
    /** atype：配件的類型（瞄具、槍口…；tools/sort_shop_attachments.py 從槍械包讀出來，計畫書 7 A7）；不是配件時是空字串。 */
    public record ShopItem(int index, String id, String name, int price, int free, String item, int count, String category, String atype) {
        public int priceFor(ArenaClass c) { return c == ArenaClass.FREELANCER ? free : price; }
    }

    /** 槍王之王的一個武器：gun 不是 null 就是槍（給滿彈的槍），否則是 item × count。 */
    public record GkEntry(String gun, String item, int count) {}

    public record GkCategory(String name, List<GkEntry> items) {
        public boolean takesAttachments() { return items.stream().anyMatch(e -> e.gun() != null); }
    }

    private static List<String> shopCategories = List.of();
    private static List<ShopItem> shop;
    private static List<GkCategory> gkCategories;
    private static Map<String, List<String>> gkAttachments;
    private static List<Vec3> gkSpawns;

    private ArenaData() {}

    public static synchronized List<ShopItem> shop() {
        if (shop == null) loadShop();
        return shop;
    }

    public static synchronized List<String> shopCategories() {
        if (shop == null) loadShop();
        return shopCategories;
    }

    public static synchronized List<GkCategory> gkCategories() {
        if (gkCategories == null) loadGk();
        return gkCategories;
    }

    /** 配件池：mag、scope、muzzle、stock、grip、laser。 */
    public static synchronized Map<String, List<String>> gkAttachments() {
        if (gkAttachments == null) loadGk();
        return gkAttachments;
    }

    public static synchronized List<Vec3> gkSpawns() {
        if (gkSpawns == null) loadGk();
        return gkSpawns;
    }

    private static JsonObject read(String name) {
        String path = "/data/provinggrounds/arena/" + name;
        try (InputStream in = ArenaData.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            ProvingGroundsMod.LOGGER.error("Cannot read arena data {}", path, e);
            return new JsonObject();
        }
    }

    private static void loadShop() {
        JsonObject root = read("shop.json");
        List<String> cats = new ArrayList<>();
        if (root.has("categories")) for (JsonElement e : root.getAsJsonArray("categories")) cats.add(e.getAsString());
        List<ShopItem> items = new ArrayList<>();
        if (root.has("items")) {
            for (JsonElement e : root.getAsJsonArray("items")) {
                JsonObject o = e.getAsJsonObject();
                items.add(new ShopItem(items.size(), o.get("id").getAsString(), o.get("name").getAsString(),
                    o.get("price").getAsInt(), o.get("free").getAsInt(), o.get("item").getAsString(),
                    o.get("count").getAsInt(), o.get("cat").getAsString(), o.has("atype") ? o.get("atype").getAsString() : ""));
            }
        }
        // 只留下真的有商品的分類
        cats.removeIf(c -> items.stream().noneMatch(i -> i.category().equals(c)));
        shopCategories = Collections.unmodifiableList(cats);
        shop = Collections.unmodifiableList(items);
    }

    private static void loadGk() {
        JsonObject root = read("gk.json");
        List<GkCategory> cats = new ArrayList<>();
        if (root.has("categories")) {
            for (JsonElement e : root.getAsJsonArray("categories")) {
                JsonObject o = e.getAsJsonObject();
                List<GkEntry> entries = new ArrayList<>();
                for (JsonElement ie : o.getAsJsonArray("items")) {
                    JsonObject io = ie.getAsJsonObject();
                    if (io.has("gun")) entries.add(new GkEntry(io.get("gun").getAsString(), null, 1));
                    else entries.add(new GkEntry(null, io.get("item").getAsString(), io.has("count") ? io.get("count").getAsInt() : 1));
                }
                if (!entries.isEmpty()) cats.add(new GkCategory(o.get("name").getAsString(), List.copyOf(entries)));
            }
        }
        Map<String, List<String>> att = new java.util.LinkedHashMap<>();
        if (root.has("attachments")) {
            for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("attachments").entrySet()) {
                List<String> l = new ArrayList<>();
                for (JsonElement e : en.getValue().getAsJsonArray()) l.add(e.getAsString());
                if (!l.isEmpty()) att.put(en.getKey(), List.copyOf(l));
            }
        }
        List<Vec3> spawns = new ArrayList<>();
        if (root.has("spawns")) {
            JsonArray arr = root.getAsJsonArray("spawns");
            for (JsonElement e : arr) {
                Vec3 v = ArenaConfig.vec(e.getAsString());
                if (v != null) spawns.add(v);
            }
        }
        gkCategories = List.copyOf(cats);
        gkAttachments = Collections.unmodifiableMap(att);
        gkSpawns = List.copyOf(spawns);
    }
}
