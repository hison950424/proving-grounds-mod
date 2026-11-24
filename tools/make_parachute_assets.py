# 檔案: make_parachute_assets.py
# 目的: 產生降落傘的貼圖與模型（原創的簡單設計，不使用其他遊戲的素材）。
#   textures/item/parachute.png        快捷欄圖示：收好的傘包（橄欖綠、背帶、橘色拉環）
#   textures/item/parachute_canopy.png 傘衣：橘白相間的布片；最下面兩列是深灰色（傘繩用）
#   models/item/parachute.json         圖示模型；打開時（provinggrounds:open = 1）改用 parachute_open（看不見）
#   models/item/parachute_open.json    空模型：開傘時手上不顯示傘包
#   models/item/parachute_canopy.json  3D 傘衣：階梯狀的圓頂＋四片斜裙邊＋四條傘繩（渲染時放大 3 倍）
# 用法: python tools/make_parachute_assets.py
import json, os, struct, zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'provinggrounds')
TEX = os.path.join(ROOT, 'textures', 'item')
MOD = os.path.join(ROOT, 'models', 'item')


def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *px) for px in row) for row in pixels)
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)


def hexc(s, a=255):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


T = (0, 0, 0, 0)

# 圖示：收好的傘包
icon = [[T] * 16 for _ in range(16)]
bag, dark, light, strap, pull = hexc('5C6B3A'), hexc('3E4826'), hexc('76874C'), hexc('2B2B2B'), hexc('E8742A')
for y in range(3, 15):
    for x in range(3, 13):
        icon[y][x] = bag
for x in range(3, 13):
    icon[3][x] = light; icon[14][x] = dark
for y in range(3, 15):
    icon[y][3] = light; icon[y][12] = dark
for y in range(4, 14):                  # 兩條背帶
    icon[y][5] = strap; icon[y][10] = strap
for x in range(4, 12):                  # 橫帶
    icon[9][x] = strap
for (x, y) in [(7, 1), (8, 1), (6, 2), (9, 2), (7, 2), (8, 2)]:  # 上方提把
    icon[y][x] = strap
for (x, y) in [(11, 11), (12, 11), (12, 12), (13, 12), (13, 13)]:  # 橘色拉環
    icon[y][x] = pull
png(os.path.join(TEX, 'parachute.png'), icon)

# 傘衣貼圖
can = [[T] * 16 for _ in range(16)]
orange, cream, edge, cord = hexc('E8742A'), hexc('F2E6C8'), hexc('B8541A'), hexc('3A3A3A')
for y in range(14):
    for x in range(16):
        can[y][x] = orange if (x // 2) % 2 == 0 else cream
for x in range(16):
    can[13][x] = edge
for y in (14, 15):
    for x in range(16):
        can[y][x] = cord
png(os.path.join(TEX, 'parachute_canopy.png'), can)


def faces(uv=None):
    f = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        f[d] = {'texture': '#canopy'} if uv is None else {'texture': '#canopy', 'uv': uv}
    return f


CORD_UV = [0, 14, 2, 16]
elements = [
    # 圓頂（階梯）
    {'from': [4, 15, 4], 'to': [12, 16, 12], 'faces': faces()},
    {'from': [2, 14, 2], 'to': [14, 15, 14], 'faces': faces()},
    {'from': [1, 13.5, 1], 'to': [15, 14, 15], 'faces': faces()},
    # 四片裙邊（往外斜 22.5 度）
    {'from': [1, 11, 0.5], 'to': [15, 13.5, 1], 'rotation': {'origin': [8, 13.5, 1], 'axis': 'x', 'angle': 22.5}, 'faces': faces()},
    {'from': [1, 11, 15], 'to': [15, 13.5, 15.5], 'rotation': {'origin': [8, 13.5, 15], 'axis': 'x', 'angle': -22.5}, 'faces': faces()},
    {'from': [0.5, 11, 1], 'to': [1, 13.5, 15], 'rotation': {'origin': [1, 13.5, 8], 'axis': 'z', 'angle': -22.5}, 'faces': faces()},
    {'from': [15, 11, 1], 'to': [15.5, 13.5, 15], 'rotation': {'origin': [15, 13.5, 8], 'axis': 'z', 'angle': 22.5}, 'faces': faces()},
    # 四條傘繩：從傘衣邊緣往下收到中間（人的肩膀）
    {'from': [7.8, 0, 7.8], 'to': [8.2, 13, 8.2], 'rotation': {'origin': [8, 0, 8], 'axis': 'x', 'angle': -22.5}, 'faces': faces(CORD_UV)},
    {'from': [7.8, 0, 7.8], 'to': [8.2, 13, 8.2], 'rotation': {'origin': [8, 0, 8], 'axis': 'x', 'angle': 22.5}, 'faces': faces(CORD_UV)},
    {'from': [7.8, 0, 7.8], 'to': [8.2, 13, 8.2], 'rotation': {'origin': [8, 0, 8], 'axis': 'z', 'angle': -22.5}, 'faces': faces(CORD_UV)},
    {'from': [7.8, 0, 7.8], 'to': [8.2, 13, 8.2], 'rotation': {'origin': [8, 0, 8], 'axis': 'z', 'angle': 22.5}, 'faces': faces(CORD_UV)},
]
canopy = {
    'textures': {'canopy': 'provinggrounds:item/parachute_canopy', 'particle': 'provinggrounds:item/parachute_canopy'},
    'elements': elements,
    'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}},
}
models = {
    'parachute_canopy.json': canopy,
    'parachute.json': {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'provinggrounds:item/parachute'},
                       'overrides': [{'predicate': {'provinggrounds:open': 1}, 'model': 'provinggrounds:item/parachute_open'}]},
    'parachute_open.json': {'textures': {'particle': 'provinggrounds:item/parachute'}, 'elements': []},
}
for name, m in models.items():
    with open(os.path.join(MOD, name), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(m, f, ensure_ascii=False, indent=2)
print('ok')
