"""Текстуры и ресурсы листовой обшивки: белое окрашенное железо и светлый алюминий, гладкий лист и лист с рамкой."""
import json
from pathlib import Path

import png

root = Path(__file__).resolve().parents[1]
res = root / 'src/main/resources'
assets = res / 'assets/better_fluids'
data = res / 'data/better_fluids'

# light, base, shade, seam, rivet
PALETTES = {
    'white': ('#f3f2ee', '#e5e3dd', '#d3d0c8', '#aeaaa1', '#8e8a82'),
    'aluminum': ('#e0e6e9', '#cad1d5', '#b6bec3', '#8f989e', '#727b81'),
}
INGOT = {'white': 'c:ingots/iron', 'aluminum': 'c:ingots/aluminum'}


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def grain(x, y):
    """Слабая продольная фактура проката: 0 или 1, без случайности."""
    return ((x * 7 + y * 13 + (x * y) % 5) % 9) == 0


def plain(pal):
    light, base, shade, seam, rivet = map(rgb, pal)
    px = [[base] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if grain(x, y):
                px[y][x] = shade
    for i in range(16):
        px[0][i] = light
        px[i][0] = light
        px[15][i] = seam
        px[i][15] = seam
    px[0][15] = px[15][0] = shade
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = rivet
        px[y - 1][x - 1] = light
    return px


def framed(pal):
    light, base, shade, seam, rivet = map(rgb, pal)
    px = [[base] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge <= 2:
                px[y][x] = shade
            elif grain(x, y):
                px[y][x] = shade
    for i in range(16):
        px[0][i] = light
        px[i][0] = light
        px[15][i] = seam
        px[i][15] = seam
    # Рамка: светлый верх/лево, тёмный низ/право, как выдавленный профиль
    for i in range(3, 13):
        px[3][i] = seam
        px[i][3] = seam
        px[12][i] = light
        px[i][12] = light
    px[3][12] = px[12][3] = shade
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (8, 1), (1, 8), (14, 8), (8, 14)):
        px[y][x] = rivet
    return px


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


blocks = []
for metal, pal in PALETTES.items():
    for kind, draw in (('', plain), ('_framed', framed)):
        name = f'{metal}_cladding{kind}'
        blocks.append(name)
        tex = assets / f'textures/block/{name}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        png.write(tex, draw(pal))
        write_json(assets / f'blockstates/{name}.json', {'variants': {'': {'model': f'better_fluids:block/{name}'}}})
        write_json(assets / f'models/block/{name}.json',
                   {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'better_fluids:block/{name}'}})
        write_json(assets / f'models/item/{name}.json', {'parent': f'better_fluids:block/{name}'})
        write_json(data / f'loot_table/blocks/{name}.json', {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'better_fluids:{name}'}],
                       'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        })
    # Слиток на камнерезе даёт 4 блока (обшивка — тонкий лист, дешевле полного блока); гладкий лист и рамка
    # переводятся друг в друга
    for kind in ('', '_framed'):
        name = f'{metal}_cladding{kind}'
        other = f'{metal}_cladding{"" if kind else "_framed"}'
        write_json(data / f'recipe/{name}_from_ingot.json', {
            'type': 'minecraft:stonecutting',
            'ingredient': {'tag': INGOT[metal]},
            'result': {'id': f'better_fluids:{name}', 'count': 4},
        })
        write_json(data / f'recipe/{name}_from_{other}.json', {
            'type': 'minecraft:stonecutting',
            'ingredient': {'item': f'better_fluids:{other}'},
            'result': {'id': f'better_fluids:{name}', 'count': 1},
        })

print('\n'.join(blocks))
