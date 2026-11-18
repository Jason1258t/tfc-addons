"""Модель рядного трёхцилиндрового дизеля. Вал вдоль +Y, головки цилиндров со стороны +Z.
Готовые текстуры ImageGen не перерисовываются; запуск: python3 art/gen_transport_engine.py.
"""
import json
import kit
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_aeronautics_extra'
textures = {name: f'{NS}:block/transport_engine_{name}' for name in ('louvres', 'service', 'head', 'base')}
textures.update(particle=textures['base'], steel='create:block/industrial_iron_block',
                shaft='create:block/axis', shaft_top='create:block/axis_top', brass='create:block/brass_block')
elements = []


def box(name, lo, hi, material, uv=None, **faces):
    data = {}
    for side in ('up', 'down', 'north', 'south', 'east', 'west'):
        tex = faces.get(side, material)
        data[side] = {'texture': '#' + tex, 'uv': uv or [0, 0, 16, 16]}
    elements.append({'name': name, 'from': lo, 'to': hi, 'faces': data})


# Узкие лапы и масляный поддон оставляют просвет под картером.
for x in (1, 12):
    box('Монтажная лапа', [x, 1, 0], [x + 3, 14, 1.5], 'steel', [2, 2, 6, 6])
box('Масляный поддон', [4, 2, 1.5], [12, 12, 4], 'base')
box('Картер', [3, 2, 4], [13, 11.5, 8], 'base', east='service', west='service')
# Ступенчатый кожух маховика у выходного вала.
box('Кожух маховика', [3.5, 11.5, 3.5], [12.5, 14, 12.5], 'steel')
box('Верх маховика', [5, 11.5, 12.5], [11, 14, 13.5], 'steel', [2, 2, 12, 5])
box('Ступица', [5, 14, 5], [11, 14.5, 11], 'base')
box('Выходной вал', [6, 14.5, 6], [10, 16, 10], 'shaft', [6, 0, 10, 3], up='shaft_top', down='shaft_top')
# Три отдельных цилиндра с рёбрами и крышками: силуэт читается со всех боков.
for i, y in enumerate((2.5, 5.5, 8.5), 1):
    box(f'Цилиндр {i}', [5, y, 8], [11, y + 2.5, 12], 'steel', [3, 3, 9, 9])
    for z in (8.5, 10, 11.5):
        box(f'Ребро цилиндра {i}', [4.5, y - .2, z], [11.5, y + 2.7, z + .5], 'steel', [1, 1, 8, 3])
    box(f'Крышка головки {i}', [4.5, y, 12.5], [11.5, y + 2.5, 14], 'head')
    box(f'Форсунка {i}', [3.5, y + .7, 10.5], [4.5, y + 1.5, 12.5], 'brass', [3, 3, 6, 6])
    box(f'Топливная трубка {i}', [2.75, y + 1, 7], [3.25, y + 1.5, 11.5], 'brass', [4, 4, 5, 8])
    box(f'Выпускной канал {i}', [11, y + .5, 10], [13.5, y + 1.5, 11], 'steel', [3, 3, 7, 7])
box('Топливный насос двигателя', [1.75, 3, 5.5], [3.25, 10.5, 7.5], 'base')
box('Выпускной коллектор', [13, 2.5, 9.5], [15, 11, 11.5], 'steel', [2, 2, 7, 7])
box('Выпускной патрубок', [13, 2.5, 11.5], [15, 4.5, 15.5], 'steel', [2, 2, 6, 8])
box('Впускной коллектор', [1, 3.5, 12], [3.5, 10.5, 13.5], 'steel', [3, 3, 7, 7])
box('Воздушный фильтр', [1, 1, 9], [4, 3.5, 14.5], 'louvres')
box('Задняя крышка и вход топлива', [4, 0, 4], [12, 2, 11], 'service')
model = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements,
         'display': {'gui': {'rotation': kit.lying(view_y=45, tilt=-90), 'scale': [.625, .625, .625]}}}
path = ROOT / f'src/main/resources/assets/{NS}/models/block/transport_engine.json'
path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n')
print(f'Дизель: {len(elements)} деталей → {path}')
