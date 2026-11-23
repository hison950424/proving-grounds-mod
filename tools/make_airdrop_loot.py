# 檔案: make_airdrop_loot.py
# 目的: 產生空投箱的戰利品表 data/br/loot_tables/crates/airdrop.json（高風險高回報）。
#       物品條目直接從現有的物品池（pools/high.json、attach.json、medical.json）複製，格式不會寫錯；
#       改這支程式的清單再執行一次即可重產。
# 每個空投保底：
#   頂級主武器 1 把（栓動狙擊、重機槍、頂級步槍）＋ 高級配件 2～3 個（高倍鏡、擴充彈匣、消音器、穿甲彈）
#   ＋ 頂級防具 2 件（鑽石／獄髓）＋ 高級補品 2～3 個 ＋ 投擲物 1～2 個 ＋ 子彈 4～6 組 ＋ 背包（三級 70%、二級 30%）
#   特殊物品另外由程式保底 1 件（SpecialLoot，特殊物品模式關閉時不出）。
# 用法: python tools/make_airdrop_loot.py
import json, os, re

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "br", "loot_tables")

def load(p):
    return json.load(open(os.path.join(ROOT, p), encoding="utf-8"))

def key(e):
    nbt = " ".join(f.get("tag", "") for f in e.get("functions", []) if f.get("function") == "minecraft:set_nbt").replace('\\"', '"')
    m = re.search(r'(GunId|AttachmentId|ConsumableId|ThrowableId):"([^"]+)', nbt)
    return m.group(2) if m else e.get("name", "")

entries = {}
for p in ("pools/high.json", "pools/attach.json", "pools/medical.json"):
    for pool in load(p)["pools"]:
        for e in pool["entries"]:
            entries.setdefault(key(e), e)

def pick(spec):
    out = []
    for k, w in spec:
        if k not in entries:
            raise SystemExit("找不到物品：" + k)
        e = json.loads(json.dumps(entries[k]))
        e["weight"] = w
        out.append(e)
    return out

# 頂級主武器（權重：狙擊與重機槍稍多）
WEAPONS = [("tacz:ai_awp", 10), ("suffuse:l115a3", 10), ("suffuse:axmc", 8), ("suffuse:axsr", 8), ("tacz:m107", 6),
           ("tacz:m249", 10), ("suffuse:mg338", 8), ("tacz:rpk", 8),
           ("tacz:scar_h", 10), ("suffuse:xm7", 10), ("suffuse:rm277", 8), ("tacz:hk416d", 10), ("tacz:mk14", 8), ("tacz:hk_g3", 6)]
ATTACH = [("tacz:scope_vudu", 10), ("tacz:scope_lpvo_1_6", 10), ("suffuse:scope_sig_tango_msr_1_6", 8), ("tacz:scope_elcan_4x", 10),
          ("suffuse:scope_acogta01", 10), ("tacz:scope_standard_8x", 8), ("tacz:extended_mag_3", 12), ("tacz:light_extended_mag_3", 10),
          ("tacz:sniper_extended_mag_3", 8), ("tacz:muzzle_silencer_wraith", 10), ("tacz:muzzle_silencer_vulture", 8), ("tacz:ammo_mod_fmj", 8)]
ARMOR = [("minecraft:diamond_helmet", 12), ("minecraft:diamond_chestplate", 12), ("minecraft:diamond_leggings", 12), ("minecraft:diamond_boots", 12),
         ("minecraft:netherite_helmet", 5), ("minecraft:netherite_chestplate", 5), ("minecraft:netherite_leggings", 5), ("minecraft:netherite_boots", 5)]
MEDICAL = [("lrtactical:cms", 20), ("lrtactical:surv12", 30), ("lrtactical:carfak", 40)]
THROW = [("lrtactical:m67", 30), ("lrtactical:rgn", 20), ("suffuse:m24_cluster", 10), ("lrtactical:molotov", 20), ("lrtactical:smoke_grenade", 20)]

old = load("crates/airdrop.json")
ammo = old["pools"][1]  # 子彈池沿用原本的，次數加多

table = {
    "type": "minecraft:chest",
    "pools": [
        {"rolls": 1, "entries": pick(WEAPONS)},
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 3}, "entries": pick(ATTACH)},
        {"rolls": 2, "entries": pick(ARMOR)},
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 3}, "entries": pick(MEDICAL)},
        {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 2}, "entries": pick(THROW)},
        {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 6}, "entries": ammo["entries"]},
        {"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": "provinggrounds:backpack_3", "weight": 70},
            {"type": "minecraft:item", "name": "provinggrounds:backpack_2", "weight": 30}]},
    ],
}
with open(os.path.join(ROOT, "crates", "airdrop.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump(table, f, ensure_ascii=False, indent=2)
    f.write("\n")
print("ok")
