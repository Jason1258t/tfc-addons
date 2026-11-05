"""
Декоративный провод (#205): состояния распределительной коробки — все сочетания свойств блока EE (FACING, ROLL, FLIP,
вода/масло), повороты — ROT из gen_assets (как у блоков EE). Запуск: python3 art/surface_wire.py.
Текстуры — готовые (задача #271, исходники в art/surface-wiring), модели коробки и предметов — файлы в ресурсах.
"""
import json
import os

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

with open(os.path.join(RES, 'blockstates', 'junction_box.json'), 'w') as f:
    json.dump(blockstate(NS, 'junction_box', ROT, ['block'] * 4), f, indent=2)
print('готово')
