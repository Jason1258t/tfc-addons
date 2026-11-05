"""
Декоративный провод (#205): заглушки текстур, модель и состояния распределительной коробки, модель предмета.
Запуск: python3 art/surface_wire.py (из gen_assets.py не вызывается — текстуры потом заменит отдельная задача).
Модель коробки — плоская коробка 8×3×8 лицом вверх, как powergrid:cord_junction; повороты — ROT из gen_assets.
"""
import json
import os

from png import write
from cables import blockstate

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'ee_extras')
NS = 'ee_extras'
ROT = {
    'up': {},
    'down': {'x': 180},
    'south': {'x': 270},
    'north': {'x': 270, 'y': 180},
    'east': {'x': 270, 'y': 270},
    'west': {'x': 270, 'y': 90},
}


def dump(obj, *path):
    full = os.path.join(RES, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)


def solid(w, h, a, b):
    """Заглушка: клетка двух серых тонов"""
    return [[a if (x // 2 + y // 2) % 2 == 0 else b for x in range(w)] for y in range(h)]


GREY = (0x8a, 0x8a, 0x8a, 255)
DARK = (0x6a, 0x6a, 0x6a, 255)

# Текстура провода: рендер берёт u/v в блоках (длина отрезка), поэтому заглушка — 16×16 почти белая (цвет — краской)
write(os.path.join(RES, 'textures', 'entity', 'surface_wire.png'), solid(16, 16, (0xee, 0xee, 0xee, 255), (0xd8, 0xd8, 0xd8, 255)))
write(os.path.join(RES, 'textures', 'item', 'surface_wire.png'), solid(16, 16, GREY, DARK))
write(os.path.join(RES, 'textures', 'block', 'junction_box.png'), solid(16, 16, GREY, DARK))

side = {'texture': '#0', 'uv': [0, 8, 8, 11]}
dump({
    'credit': 'ee_extras: заглушка; форма — как powergrid:cord_junction (Create: Power Grid, Apache-2.0)',
    'parent': 'block/block',
    'textures': {'0': f'{NS}:block/junction_box', 'particle': f'{NS}:block/junction_box'},
    'elements': [{
        'from': [4, 0, 4], 'to': [12, 3, 12],
        'faces': {'north': side, 'east': side, 'south': side, 'west': side,
                  'up': {'texture': '#0', 'uv': [0, 0, 8, 8]}, 'down': {'texture': '#0', 'uv': [8, 0, 16, 8], 'cullface': 'down'}},
    }],
}, 'models', 'block', 'junction_box', 'block.json')
dump({'parent': f'{NS}:block/junction_box/block'}, 'models', 'item', 'junction_box.json')
dump(blockstate(NS, 'junction_box', ROT, ['block'] * 4), 'blockstates', 'junction_box.json')
dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/surface_wire'}}, 'models', 'item', 'surface_wire.json')
print('готово')
