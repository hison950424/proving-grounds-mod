# 檔案: curate_shop.py
# 目的: 計畫書 9：商店減法——每種槍械精選 5 把特色分明的槍（共 35 把），
#       並依伺服器 TACZ 槍械包的相容標籤，只保留這 35 把槍（含職業起始槍）裝得上的配件。
# 用法: python tools/curate_shop.py [伺服器 tacz 資料夾]
import json, os, re, sys
from collections import Counter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TACZ = sys.argv[1] if len(sys.argv) > 1 else r'C:\mcdatapack\1.20.1 Proving Grounds_Server_Mod\tacz'
SHOP = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'provinggrounds', 'arena', 'shop.json')

# 7 大類 × 每類 5 把特色分明的槍（按順序保留）
KEEP_GUNS = {
    '手槍':   ['tacz:glock_17', 'tacz:b93r', 'tacz:p320', 'tacz:deagle', 'tacz:rhino357'],
    '衝鋒槍': ['tacz:uzi', 'tacz:hk_mp5a5', 'tacz:vector45', 'suffuse:pp19', 'tacz:p90'],
    '散彈槍': ['tacz:db_short', 'tacz:m870', 'tacz:m1014', 'suffuse:ks23m', 'tacz:aa12'],
    '步槍':   ['tacz:ak47', 'tacz:hk416d', 'tacz:m16a4', 'tacz:aug', 'tacz:scar_h'],
    '機槍':   ['suffuse:qjb951', 'tacz:m249', 'tacz:rpk', 'suffuse:pkp', 'tacz:minigun'],
    '狙擊槍': ['suffuse:svd', 'tacz:m700', 'tacz:m107', 'tacz:ai_awp', 'tacz:m95'],
    '重武器': ['tacz:m320', 'tacz:rpg7', 'suffuse:m32a1', 'suffuse:qlu11', 'suffuse:qlz87'],
}
# 加上職業起始槍（如果不在 35 把裡，確保配件也相容）
EXTRA_STARTER_GUNS = ['tacz:cz75', 'suffuse:tt33', 'suffuse:mas38']
ALL_GUN_IDS = {g for gs in KEEP_GUNS.values() for g in gs}
CHECK_GUN_IDS = ALL_GUN_IDS | set(EXTRA_STARTER_GUNS)

def load_json(path):
    text = open(path, encoding='utf-8-sig').read()
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'(?m)(^|[\s,{\[])//[^\n]*', r'\1', text)
    text = re.sub(r',(\s*[}\]])', r'\1', text)
    return json.loads(text)

# 1. 讀取所有槍包的 tacz_tags/attachments 標籤（包含 allow_attachments/<gun> 與一般標籤）
tags = {}       # tag_id ("ns:path") -> list of raw entries
gun_allow = {}  # gun_id ("ns:gun") -> list of raw entries
for pack in sorted(os.listdir(TACZ)):
    data_dir = os.path.join(TACZ, pack, 'data')
    if not os.path.isdir(data_dir):
        continue
    for ns in os.listdir(data_dir):
        att_root = os.path.join(data_dir, ns, 'tacz_tags', 'attachments')
        if not os.path.isdir(att_root):
            continue
        for dirpath, _, files in os.walk(att_root):
            for f in files:
                if not f.endswith('.json'):
                    continue
                full = os.path.join(dirpath, f)
                rel = os.path.relpath(full, att_root).replace('\\', '/')[:-5]
                try:
                    arr = load_json(full)
                except Exception:
                    continue
                if not isinstance(arr, list):
                    continue
                key = f'{ns}:{rel}'
                tags.setdefault(key, []).extend(arr)
                if rel.startswith('allow_attachments/'):
                    gid = f'{ns}:{rel[len("allow_attachments/"):]}'
                    gun_allow.setdefault(gid, []).extend(arr)

def resolve_tag(tag_id, seen=None):
    if seen is None:
        seen = set()
    if tag_id in seen:
        return set()
    seen.add(tag_id)
    out = set()
    for entry in tags.get(tag_id, []):
        if not isinstance(entry, str):
            continue
        if entry.startswith('#'):
            out |= resolve_tag(entry[1:], seen)
        else:
            out.add(entry)
    return out

# 計算這 35 把商店槍 + 起始槍能裝的所有配件 ID
compatible_attachments = set()
for gid in CHECK_GUN_IDS:
    for entry in gun_allow.get(gid, []):
        if not isinstance(entry, str):
            continue
        if entry.startswith('#'):
            compatible_attachments |= resolve_tag(entry[1:])
        else:
            compatible_attachments.add(entry)

print('Resolved compatible attachment IDs for shop guns:', len(compatible_attachments))

# 2. 過濾 shop.json
shop = json.load(open(SHOP, encoding='utf-8'))
old_items = shop['items']
new_items = []
kept_guns_found = set()
dropped_att = 0
kept_att = 0

for it in old_items:
    cat = it['cat']
    if cat in KEEP_GUNS:
        m = re.search(r'GunId:"([^"]+)"', it['item'])
        gid = m.group(1) if m else ''
        if gid in KEEP_GUNS[cat]:
            new_items.append(it)
            kept_guns_found.add(gid)
    elif cat == '其他槍械':
        continue  # 拿掉「其他槍械」分類
    elif cat == '配件':
        m = re.search(r'AttachmentId:"([^"]+)"', it['item'])
        aid = m.group(1) if m else ''
        if aid in compatible_attachments:
            new_items.append(it)
            kept_att += 1
        else:
            dropped_att += 1
    else:
        new_items.append(it)

missing = ALL_GUN_IDS - kept_guns_found
if missing:
    print('WARNING: missing guns from shop.json:', missing)

shop['categories'] = [c for c in shop['categories'] if c != '其他槍械']
shop['items'] = new_items
with open(SHOP, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(shop, f, ensure_ascii=False, indent=1)

print('Done! Total items:', len(old_items), '->', len(new_items))
print('Attachments:', kept_att, 'kept,', dropped_att, 'dropped')
print('Counts by cat:', dict(Counter(x['cat'] for x in new_items)))
