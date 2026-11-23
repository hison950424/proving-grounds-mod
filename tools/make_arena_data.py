"""從舊版資料包產生團隊死鬥／佔點／槍王之王用的資料（商店、槍王武器與配件、重生點）。
用法：python make_arena_data.py <舊版 functions 資料夾> <markers.json> <輸出資料夾>"""
import os, re, json, sys
F, MK, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
GIVE = re.compile(r'give @s (\S+?)(\{.*\})? (\d+)\s*$')
def gives(path):
    out = []
    for line in open(path, encoding='utf-8'):
        m = GIVE.search(line.strip())
        if m: out.append((m.group(1), m.group(2) or '', int(m.group(3))))
    return out
# 槍王：九個類別
GK = os.path.join(F, 'gamemode', 'gk')
CAT_NAMES = ['手槍', '衝鋒槍', '散彈槍', '步槍', '機槍', '狙擊槍', '重武器', '投擲物', '近戰']
gun_cat = {}
cats = []
for i in range(1, 10):
    items = []
    for item, nbt, count in gives(os.path.join(GK, f'give_cat{i}.mcfunction')):
        m = re.search(r'GunId:"([^"]+)"', nbt)
        if m:
            items.append({'gun': m.group(1)})
            gun_cat[m.group(1)] = CAT_NAMES[i - 1]
        else:
            items.append({'item': item + nbt, 'count': count})
    cats.append({'name': CAT_NAMES[i - 1], 'items': items})
att = {}
for k in ['mag', 'scope', 'muzzle', 'stock', 'grip', 'laser']:
    att[k] = [item + nbt for item, nbt, _ in gives(os.path.join(GK, f'give_attach_{k}.mcfunction'))]
markers = json.load(open(MK, encoding='utf-8'))
gk = {'categories': cats, 'attachments': att,
      'spawns': [' '.join(str(c) for c in p) for p in markers['gk_spawn']]}
# 商店
rows = json.load(open(os.path.join(os.path.dirname(__file__), '..', 'docs', 'old_shop_catalog.json'), encoding='utf-8'))
def cat_of(item, nbt):
    if item == 'tacz:modern_kinetic_gun':
        m = re.search(r'GunId:"([^"]+)"', nbt)
        return gun_cat.get(m.group(1) if m else '', '其他槍械')
    if item == 'tacz:attachment': return '配件'
    if item == 'lrtactical:melee': return '近戰'
    if item == 'lrtactical:throwable': return '投擲物'
    if re.search(r'(helmet|chestplate|leggings|boots)$', item): return '防具'
    return '其他'
ORDER = CAT_NAMES[:7] + ['其他槍械', '配件', '近戰', '投擲物', '防具', '其他']
shop = []
for r in rows:
    g = r['gives']
    nbt = re.sub(r',?ShopItem:"[^"]*"', '', g[0][1]).replace('{,', '{')
    if nbt == '{}': nbt = ''
    shop.append({'id': r['file'], 'name': r['name'] or r['file'], 'price': r['price'], 'free': r['free'] or r['price'],
                 'item': g[0][0] + nbt, 'count': g[0][2], 'cat': cat_of(g[0][0], nbt)})
shop.sort(key=lambda e: (ORDER.index(e['cat']), e['price'], e['name']))
os.makedirs(OUT, exist_ok=True)
json.dump({'categories': ORDER, 'items': shop}, open(os.path.join(OUT, 'shop.json'), 'w', encoding='utf-8', newline='\n'), ensure_ascii=False, indent=1)
json.dump(gk, open(os.path.join(OUT, 'gk.json'), 'w', encoding='utf-8', newline='\n'), ensure_ascii=False, indent=1)
from collections import Counter
print(Counter(e['cat'] for e in shop), {c['name']: len(c['items']) for c in cats}, {k: len(v) for k, v in att.items()}, len(gk['spawns']))
