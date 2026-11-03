"""Технические текстуры и модели M10: стол плат, компоненты, термометр и солнечная панель."""
import json
import os
from png import write

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'ee_extras')
NS = 'ee_extras'


def dump(obj, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def image(color=(0, 0, 0, 0)):
    return [[color for _ in range(16)] for _ in range(16)]


def rect(px, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[y][x] = color


def line(px, x0, y0, x1, y1, color):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        px[y0][x0] = color
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def texture(name, px, kind='item'):
    path = os.path.join(ASSETS, 'textures', kind, name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    write(path, px)


def item_icon(name, base, accent, style):
    px = image()
    dark = tuple(max(0, c - 45) for c in base[:3]) + (255,)
    light = tuple(min(255, c + 35) for c in base[:3]) + (255,)
    if style in ('sheet', 'board', 'wafer', 'design'):
        rect(px, 2, 2, 14, 14, dark)
        rect(px, 3, 2, 13, 13, base)
        line(px, 3, 3, 12, 3, light)
        if style == 'board':
            for x in (4, 7, 10):
                line(px, x, 4, x, 11, accent)
            for y in (5, 9):
                line(px, 4, y, 11, y, accent)
            for x, y in ((4, 5), (10, 5), (7, 9)):
                rect(px, x, y, x + 1, y + 1, (225, 215, 140, 255))
        elif style == 'wafer':
            for x in range(4, 12, 2):
                for y in range(4, 12, 2):
                    rect(px, x, y, x + 1, y + 1, accent)
        elif style == 'design':
            for i in range(3, 12):
                if i % 2:
                    line(px, 4, i, 11, i, accent)
            line(px, 4, 3, 4, 12, accent)
        else:
            line(px, 4, 11, 11, 4, accent)
    elif style in ('wire', 'fiber'):
        for off in (0, 2, 4):
            line(px, 2, 12 - off, 12 - off, 2, base if off != 2 else accent)
        rect(px, 2, 12, 5, 14, dark)
    elif style in ('fabric', 'laminate'):
        rect(px, 2, 2, 14, 14, dark)
        rect(px, 3, 3, 13, 13, base)
        for i in range(3, 13, 2):
            line(px, i, 3, i, 12, accent)
            line(px, 3, i, 12, i, accent)
    elif style in ('resistor', 'capacitor', 'inductor', 'diode', 'transistor'):
        line(px, 1, 8, 5, 8, (180, 180, 170, 255))
        line(px, 11, 8, 15, 8, (180, 180, 170, 255))
        if style == 'capacitor':
            rect(px, 6, 3, 8, 13, base)
            rect(px, 9, 3, 11, 13, accent)
        elif style == 'inductor':
            for x in range(4, 12, 2):
                rect(px, x, 6, x + 2, 10, base)
                rect(px, x, 5, x + 1, 6, accent)
        elif style == 'transistor':
            rect(px, 5, 4, 11, 12, dark)
            rect(px, 6, 4, 10, 10, base)
            line(px, 8, 11, 8, 15, accent)
        else:
            rect(px, 4, 6, 12, 10, dark)
            rect(px, 5, 5, 11, 11, base)
            if style == 'resistor':
                for x, c in ((6, accent), (8, (40, 40, 40, 255)), (10, (210, 175, 50, 255))):
                    rect(px, x, 5, x + 1, 11, c)
            else:
                rect(px, 9, 5, 10, 11, accent)
    elif style in ('connector', 'ic'):
        rect(px, 3, 4, 13, 12, dark)
        rect(px, 4, 4, 12, 11, base)
        for x in (4, 7, 10):
            rect(px, x, 2, x + 1, 4, accent)
            rect(px, x, 12, x + 1, 14, accent)
        rect(px, 7, 6, 9, 8, accent)
    else:
        rect(px, 3, 3, 13, 13, base)
        line(px, 3, 3, 12, 12, accent)
    texture(name, px)


ITEMS = {
    'copper_foil': ((185, 105, 55, 255), (245, 175, 105, 255), 'sheet'),
    'glass_fiber': ((205, 220, 215, 255), (245, 250, 245, 255), 'fiber'),
    'glass_fabric': ((180, 200, 190, 255), (220, 230, 215, 255), 'fabric'),
    'epoxy_laminate': ((155, 115, 55, 255), (105, 145, 75, 255), 'laminate'),
    'l2_board_blank': ((55, 115, 70, 255), (190, 115, 60, 255), 'board'),
    'l3_board_blank': ((45, 85, 105, 255), (190, 115, 60, 255), 'board'),
    'solder_wire': ((165, 175, 180, 255), (225, 230, 230, 255), 'wire'),
    'rosin_flux': ((195, 125, 35, 255), (240, 190, 70, 255), 'sheet'),
    'resistor': ((195, 170, 115, 255), (160, 45, 35, 255), 'resistor'),
    'capacitor': ((65, 115, 170, 255), (195, 205, 210, 255), 'capacitor'),
    'inductor': ((165, 85, 40, 255), (225, 145, 65, 255), 'inductor'),
    'connector': ((215, 205, 175, 255), (195, 155, 65, 255), 'connector'),
    'silicon_diode': ((55, 60, 65, 255), (220, 220, 215, 255), 'diode'),
    'transistor': ((55, 65, 60, 255), (195, 200, 190, 255), 'transistor'),
    'basic_ic': ((45, 55, 50, 255), (190, 195, 175, 255), 'ic'),
    'advanced_ic': ((50, 45, 65, 255), (210, 180, 85, 255), 'ic'),
    'silicon_wafer': ((85, 105, 135, 255), (145, 175, 205, 255), 'wafer'),
    'pcb_design': ((225, 220, 195, 255), (55, 115, 75, 255), 'design'),
}


def cuboid(a, b, top, side, bottom=None):
    faces = {face: {'texture': '#side', 'uv': [0, 0, 16, 16]} for face in ('north', 'east', 'south', 'west')}
    faces['up'] = {'texture': '#top', 'uv': [0, 0, 16, 16]}
    faces['down'] = {'texture': '#bottom' if bottom else '#side', 'uv': [0, 0, 16, 16]}
    return {'from': a, 'to': b, 'faces': faces}


def device_model(name, top, side, panel=False):
    textures = {'top': f'{NS}:block/{top}', 'side': f'{NS}:block/{side}', 'particle': f'{NS}:block/{side}',
                'red': 'minecraft:block/redstone_block', 'blue': 'minecraft:block/lapis_block'}
    elements = [cuboid([0, 0, 0], [16, 4 if panel else 5, 16], top, side)]
    for x, color in ((5.5, 'red'), (8.5, 'blue')):
        elements.append({'from': [x, 5, 0], 'to': [x + 2, 7, 1], 'faces': {
            face: {'texture': '#' + color, 'uv': [0, 0, 16, 16]}
            for face in ('north', 'east', 'south', 'west', 'up', 'down')}})
    model = {'credit': 'ee_extras', 'textures': textures, 'elements': elements}
    dump(model, ASSETS, 'models', 'block', name, 'block.json')
    dump({'parent': f'{NS}:block/{name}/block'}, ASSETS, 'models', 'block', name, 'block_roll.json')
    dump({'parent': f'{NS}:block/{name}/block'}, ASSETS, 'models', 'item', name + '.json')


ROT = {
    'up': {}, 'down': {'x': 180}, 'south': {'x': 270}, 'north': {'x': 270, 'y': 180},
    'east': {'x': 270, 'y': 270}, 'west': {'x': 270, 'y': 90},
}


def device_blockstate(name):
    variants = {}
    for facing, rot in ROT.items():
        for logged in ('dry', 'oillogged', 'waterlogged'):
            for rolled in (False, True):
                key = f'facing={facing},logged_state={logged},roll={str(rolled).lower()}'
                variants[key] = {'model': f'{NS}:block/{name}/' + ('block_roll' if rolled else 'block'), **rot}
    dump({'variants': variants}, ASSETS, 'blockstates', name + '.json')


def block_textures():
    px = image((45, 55, 60, 255))
    rect(px, 1, 1, 15, 15, (35, 70, 115, 255))
    for x in (4, 8, 12):
        line(px, x, 1, x, 14, (85, 135, 175, 255))
    for y in (4, 8, 12):
        line(px, 1, y, 14, y, (85, 135, 175, 255))
    texture('solar_panel_top', px, 'block')
    px = image((55, 60, 62, 255))
    rect(px, 0, 0, 16, 2, (125, 130, 130, 255))
    texture('solar_panel_side', px, 'block')
    px = image((170, 175, 165, 255))
    rect(px, 2, 2, 14, 14, (225, 220, 195, 255))
    for i in range(5, 12, 2):
        line(px, 4, i, 11, i, (65, 70, 65, 255))
    line(px, 8, 8, 11, 4, (180, 45, 35, 255))
    texture('thermometer_top', px, 'block')
    texture('thermometer_side', image((85, 90, 88, 255)), 'block')
    px = image((105, 75, 42, 255))
    rect(px, 2, 2, 14, 14, (45, 85, 60, 255))
    for x in range(3, 14, 2):
        for y in range(3, 14, 2):
            px[y][x] = (190, 125, 60, 255)
    texture('pcb_design_table_top', px, 'block')
    texture('pcb_design_table_side', image((105, 75, 42, 255)), 'block')


def editor_model():
    textures = {'top': f'{NS}:block/pcb_design_table_top', 'side': f'{NS}:block/pcb_design_table_side',
                'particle': f'{NS}:block/pcb_design_table_side'}
    elements = [cuboid([0, 12, 0], [16, 16, 16], 'top', 'side')]
    for x in (1, 13):
        for z in (1, 13):
            elements.append(cuboid([x, 0, z], [x + 2, 12, z + 2], 'side', 'side'))
    dump({'credit': 'ee_extras', 'textures': textures, 'elements': elements}, ASSETS, 'models', 'block', 'pcb_design_table.json')
    dump({'parent': f'{NS}:block/pcb_design_table'}, ASSETS, 'models', 'item', 'pcb_design_table.json')
    dump({'variants': {f'facing={facing}': {'model': f'{NS}:block/pcb_design_table', 'y': y}
                       for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}},
         ASSETS, 'blockstates', 'pcb_design_table.json')


def loot(name):
    table = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
             'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
    if name == 'thermometer':
        table['neoforge:conditions'] = [{'type': 'neoforge:mod_loaded', 'modid': 'tfc'}]
    dump(table, RES, 'data', NS, 'loot_table', 'blocks', name + '.json')


for name, (base, accent, style) in ITEMS.items():
    item_icon(name, base, accent, style)
    dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{name}'}},
         ASSETS, 'models', 'item', name + '.json')
block_textures()
device_model('solar_panel', 'solar_panel_top', 'solar_panel_side', True)
device_model('thermometer', 'thermometer_top', 'thermometer_side')
device_blockstate('solar_panel')
device_blockstate('thermometer')
editor_model()
for name in ('solar_panel', 'thermometer', 'pcb_design_table'):
    loot(name)
print('M10 ресурсы готовы:', len(ITEMS), 'предметов и 3 блока')
