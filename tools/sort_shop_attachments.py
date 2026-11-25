# 檔案: sort_shop_attachments.py
# 目的: 計畫書 7 A7：商店配件依「類型」分組、瞄具依倍率從小到大排。
#       從槍械包讀每個配件的類型（data/<ns>/index/attachments/<id>.json 的 "type"）與倍率
#       （assets/<ns>/display/attachments/<id>_display.json 的 "zoom"），寫進 shop.json 每個配件的
#       "atype"（中文組名）與 "zoom"（最小倍率；沒有倍率的瞄具 = 1），再把配件重新排序：
#       組別順序：瞄具 → 槍口 → 握把 → 槍托 → 彈匣 → 雷射 → 特殊彈藥 → 其他；瞄具組內依倍率，其他組依價格，再依名稱。
#       其他分類的商品順序不動。
# 用法: python tools/sort_shop_attachments.py <槍械包資料夾（伺服器的 tacz）>
import json, os, re, sys

PACKS = sys.argv[1]
SHOP = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'provinggrounds', 'arena', 'shop.json')
TYPE_NAMES = {'scope': '瞄具', 'muzzle': '槍口', 'grip': '握把', 'stock': '槍托', 'extended_mag': '彈匣',
              'laser': '雷射', 'bullet': '特殊彈藥'}
ORDER = ['瞄具', '槍口', '握把', '槍托', '彈匣', '雷射', '特殊彈藥', '其他']

types, zooms = {}, {}
for root, dirs, files in os.walk(PACKS):
    norm = root.replace('\\', '/')
    m = re.search(r'/data/([^/]+)/index/attachments$', norm)
    if m:
        for f in files:
            if f.endswith('.json'):
                try:
                    types[m.group(1) + ':' + f[:-5]] = json.load(open(os.path.join(root, f), encoding='utf-8')).get('type', '')
                except Exception:
                    pass
    m = re.search(r'/assets/([^/]+)/display/attachments$', norm)
    if m:
        for f in files:
            if f.endswith('_display.json'):
                try:
                    z = json.load(open(os.path.join(root, f), encoding='utf-8')).get('zoom')
                    if isinstance(z, list) and z:
                        zooms[m.group(1) + ':' + f[:-len('_display.json')]] = max(float(v) for v in z)
                except Exception:
                    pass

def keyword_type(aid):
    s = aid.lower()
    if 'ammo_mod' in s: return '特殊彈藥'
    if 'scope' in s or 'sight' in s: return '瞄具'
    if any(k in s for k in ('silencer', 'muzzle', 'flash_hider', 'bayonet', 'barrel')): return '槍口'
    if 'grip' in s: return '握把'
    if 'stock' in s: return '槍托'
    if 'mag' in s: return '彈匣'
    if 'laser' in s: return '雷射'
    return '其他'

def scope_zoom(aid, name):
    # 紅點、全息（sight_*）當 1 倍排最前；其他瞄具用名稱上的倍率（例如 4-10x 取 10），沒有才用槍械包資料的最大值
    if 'sight' in aid.split(':')[-1]: return 1.0
    nums = [float(x) for x in re.findall(r'(\d+(?:\.\d+)?)\s*[xX倍]', name.replace('-', ' '))]
    nums += [float(x) for x in re.findall(r'(\d+(?:\.\d+)?)-(?=\d+(?:\.\d+)?\s*[xX])', name)]
    if nums: return max(nums)
    return zooms.get(aid, 1.0)

d = json.load(open(SHOP, encoding='utf-8'))
items = d['items']
att_pos = [i for i, e in enumerate(items) if e['cat'] == '配件']
atts = [items[i] for i in att_pos]
missing = 0
for e in atts:
    m = re.search(r'AttachmentId:"([^"]+)"', e['item'])
    aid = m.group(1) if m else ''
    t = types.get(aid)
    if t is None: missing += 1
    e['atype'] = TYPE_NAMES.get(t, keyword_type(aid)) if t else keyword_type(aid)
    e['zoom'] = scope_zoom(aid, e['name']) if e['atype'] == '瞄具' else 0
atts.sort(key=lambda e: (ORDER.index(e['atype']), e['zoom'] if e['atype'] == '瞄具' else 0, e['price'], e['name']))
for i, e in zip(att_pos, atts):
    items[i] = e
json.dump(d, open(SHOP, 'w', encoding='utf-8', newline='\n'), ensure_ascii=False, indent=1)
from collections import Counter
print('配件', len(atts), '找不到類型（改用名稱判斷）', missing, dict(Counter(e['atype'] for e in atts)))
