# 檔案: make_gun_prices.py
# 目的: 商店所有槍上架（計畫書 5 的 H3）。讀伺服器 tacz/ 資料夾裡所有槍包的槍械資料，
#       依「類別基礎價 × 綜合係數」定價，舊版商店（shop.json 裡原本的槍）的價格當錨點保持不變，
#       把還沒上架的槍加進 src/main/resources/data/provinggrounds/arena/shop.json，並輸出價格表 docs/SHOP_PRICES.md。
# 綜合係數 score = 每秒傷害^0.6 × (1 + 穿甲)^0.5 × 彈匣^0.15 × 子彈速度^0.2
#   每秒傷害 = 傷害 × 彈丸數 × 射速 / 60（散彈槍算全部彈丸）
#   類別基礎價 = 該類別錨點槍的價格中位數；新槍價格 = 基礎價 × (score / 該類別錨點 score 中位數)，四捨五入到 10，限制在錨點價格的 0.5～1.6 倍之間
# 用法: python tools/make_gun_prices.py [伺服器 tacz 資料夾]
import io, json, os, re, statistics, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TACZ = sys.argv[1] if len(sys.argv) > 1 else r'C:\mcdatapack\1.20.1 Proving Grounds_Server_Mod\tacz'
SHOP = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'provinggrounds', 'arena', 'shop.json')
OUT_MD = os.path.join(ROOT, 'docs', 'SHOP_PRICES.md')
CAT = {'pistol': '手槍', 'smg': '衝鋒槍', 'shotgun': '散彈槍', 'rifle': '步槍', 'mg': '機槍', 'sniper': '狙擊槍', 'rpg': '重武器'}


def load(path):
    text = io.open(path, encoding='utf-8-sig').read()
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)  # 區塊註解
    text = re.sub(r'(?m)(^|[\s,{\[])//[^\n]*', r'\1', text)  # 行尾註解（不動字串裡的 //）
    text = re.sub(r',(\s*[}\]])', r'\1', text)  # 多出來的逗號
    return json.loads(text)


def lang(pack_dir):
    out = {}
    for name in ('en_us.json', 'zh_tw.json', 'zh_cn.json'):
        for dirpath, _, files in os.walk(os.path.join(pack_dir, 'assets')):
            if name in files and dirpath.endswith('lang'):
                try:
                    data = load(os.path.join(dirpath, name))
                except Exception:
                    continue
                if name == 'zh_cn.json':
                    for k, v in data.items():
                        out.setdefault(k, v)
                else:
                    out.update(data)
    return out


guns = []
for pack in sorted(os.listdir(TACZ)):
    pdir = os.path.join(TACZ, pack)
    if not os.path.isdir(pdir) or not os.path.isdir(os.path.join(pdir, 'data')):
        continue
    names = lang(pdir)
    for ns in os.listdir(os.path.join(pdir, 'data')):
        idx_dir = os.path.join(pdir, 'data', ns, 'index', 'guns')
        if not os.path.isdir(idx_dir):
            continue
        for f in sorted(os.listdir(idx_dir)):
            if not f.endswith('.json'):
                continue
            gid = ns + ':' + f[:-5]
            try:
                idx = load(os.path.join(idx_dir, f))
                dns, dpath = idx['data'].split(':')
                data = load(os.path.join(pdir, 'data', dns, 'data', 'guns', dpath + '.json'))
            except Exception as e:
                print('skip', gid, e)
                continue
            b = data.get('bullet', {})
            dmg = float(b.get('damage', 0)) * int(b.get('bullet_amount', 1))
            rpm = float(data.get('rpm', 60))
            modes = [m.upper() for m in data.get('fire_mode', ['semi'])]
            mode = 'AUTO' if 'AUTO' in modes else ('BURST' if 'BURST' in modes else 'SEMI')
            if mode == 'SEMI':
                rpm = min(rpm, 400)  # 半自動實際射速受點擊速度限制
            armor = float(b.get('extra_damage', {}).get('armor_ignore', 0))
            mag = int(data.get('ammo_amount', 1))
            speed = float(b.get('speed', 5))
            dps = dmg * rpm / 60
            score = (max(dps, 1) ** 0.6) * ((1 + armor) ** 0.5) * (max(mag, 1) ** 0.15) * (max(speed, 1) ** 0.2)
            name = names.get(idx.get('name', ''), f[:-5])
            guns.append(dict(id=gid, name=name, type=idx.get('type', ''), cat=CAT.get(idx.get('type', ''), '其他槍械'),
                             dmg=dmg, rpm=rpm, mag=mag, armor=armor, speed=speed, dps=dps, score=score, mode=mode))

shop = load(SHOP)
items = [it for it in shop['items'] if not it.get('auto')]  # 先移除上次自動產生的，重算
shop['items'] = items
anchor = {}
for it in items:
    m = re.search(r'GunId:\\?"([^"\\]+)', it['item'])
    if m:
        anchor[m.group(1)] = it
by_id = {g['id']: g for g in guns}

rows = []
cats = {}
for g in guns:
    if g['id'] in anchor:
        cats.setdefault(g['cat'], []).append((anchor[g['id']]['price'], g['score']))
all_pairs = [p for v in cats.values() for p in v]
for g in guns:
    if g['id'] in anchor:
        a = anchor[g['id']]
        rows.append((g, a['price'], '舊版錨點（不變）'))
        continue
    pairs = cats.get(g['cat']) or all_pairs
    base_price = statistics.median(p for p, _ in pairs)
    base_score = statistics.median(s for _, s in pairs)
    raw = base_price * g['score'] / base_score
    lo, hi = min(p for p, _ in pairs) * 0.5, max(p for p, _ in pairs) * 1.6
    price = int(round(max(lo, min(hi, raw)) / 10.0) * 10)
    price = max(price, 10)
    basis = '%s 基礎價 %d × 係數 %.2f' % (g['cat'] if g['cat'] in cats else '全部', base_price, g['score'] / base_score)
    rows.append((g, price, basis))
    items.append({'auto': True, 'id': g['id'].split(':')[1], 'name': g['name'], 'price': price, 'free': int(round(price * 0.8)),
                  'item': 'tacz:modern_kinetic_gun{GunId:"%s",GunFireMode:"%s"}' % (g['id'], g['mode']), 'count': 1, 'cat': g['cat']})

order = shop['categories']
items.sort(key=lambda e: (order.index(e['cat']) if e['cat'] in order else 99, e['price'], e['name']))
io.open(SHOP, 'w', encoding='utf-8', newline='\n').write(json.dumps(shop, ensure_ascii=False, indent=1))

rows.sort(key=lambda r: (order.index(r[0]['cat']) if r[0]['cat'] in order else 99, r[1]))
md = ['# 商店槍械價格表（計畫書 5 的 H3）', '',
      '由 `tools/make_gun_prices.py` 產生。舊版商店原有的槍價格不變（錨點），其他槍依「類別基礎價 × 綜合係數」定價；自由人八折。',
      '綜合係數 = 每秒傷害^0.6 × (1+穿甲)^0.5 × 彈匣^0.15 × 子彈速度^0.2，再除以同類別錨點槍的中位數。', '',
      '| 類別 | 槍 | ID | 價格 | 傷害×彈丸 | 射速 | 彈匣 | 穿甲 | 定價依據 |', '|---|---|---|---:|---:|---:|---:|---:|---|']
for g, price, basis in rows:
    md.append('| %s | %s | `%s` | %d | %.1f | %d | %d | %.2f | %s |' % (g['cat'], g['name'], g['id'], price, g['dmg'], g['rpm'], g['mag'], g['armor'], basis))
io.open(OUT_MD, 'w', encoding='utf-8', newline='\n').write('\n'.join(md) + '\n')
print('guns', len(guns), 'anchors', sum(1 for g in guns if g['id'] in anchor), 'added', len(guns) - sum(1 for g in guns if g['id'] in anchor))
