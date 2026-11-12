#!/usr/bin/env python3
"""Фон окна электролизёра 176×176 (на холсте 256×256) в стиле окон машин IE: серая панель машины, коричневая панель
инвентаря, утопленные окна баков, слот мембраны и шкала прогресса. Координаты — те же, что в ElectrolyzerScreen/Menu."""
import pathlib
import random
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import png  # noqa: E402

W, H = 176, 176
SPLIT = 80  # граница панели машины и панели инвентаря
rnd = random.Random(7)
px = [[(0, 0, 0, 0)] * 256 for _ in range(256)]


def put(x, y, rgb):
    px[y][x] = rgb + (255,)


def noisy(base, spread):
    v = base + rnd.randint(-spread, spread)
    return (v, v, v)


def rect(x0, y0, w, h, fn):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            put(x, y, fn(x, y))


def recess(x0, y0, w, h, inner, dark=(0x1d, 0x1d, 0x1d), light=(0x84, 0x84, 0x84)):
    """Утопленное окно: тень сверху-слева, блик снизу-справа, внутри — заливка."""
    rect(x0, y0, w, h, lambda x, y: inner)
    for x in range(x0 - 1, x0 + w + 1):
        put(x, y0 - 1, dark)
        put(x, y0 + h, light)
    for y in range(y0 - 1, y0 + h + 1):
        put(x0 - 1, y, dark)
        put(x0 + w, y, light)


# Панель машины и панель инвентаря
rect(0, 0, W, SPLIT, lambda x, y: noisy(0x5d, 2))
rect(0, SPLIT, W, H - SPLIT, lambda x, y: (0x2e + rnd.randint(-6, 4), 0x19 + rnd.randint(-4, 3), 0x0f + rnd.randint(-3, 2)))
for x in range(W):
    put(x, SPLIT, (0x61, 0x34, 0x21))
    put(x, SPLIT - 1, (0x3f, 0x3f, 0x3f))
# Рамка окна
for x in range(W):
    put(x, 0, (0x41, 0x41, 0x41))
    put(x, H - 1, (0x1a, 0x0e, 0x08))
for y in range(H):
    put(0, y, (0x41, 0x41, 0x41))
    put(W - 1, y, (0x41, 0x41, 0x41) if y < SPLIT else (0x1a, 0x0e, 0x08))
for x in range(1, W - 1):
    put(x, 1, (0x86, 0x86, 0x86))
for y in range(1, SPLIT - 1):
    put(1, y, (0x86, 0x86, 0x86))

# Баки: вход, смесь | катодный газ, анодный газ, католит (16×47, как у ферментера IE)
for tx in (10, 30, 116, 136, 156):
    recess(tx, 18, 16, 47, (0x27, 0x27, 0x27))
# Слот мембраны (предмет рисуется в 73,21) и шкала прогресса
recess(72, 20, 18, 18, (0x37, 0x37, 0x37))
recess(53, 46, 56, 6, (0x27, 0x27, 0x27))
# Стрелки потока: вход → смесь → ячейка → выходы
for x0 in (27, 47, 109):
    for i in range(4):
        put(x0 + i, 40, (0x9a, 0x9a, 0x9a))
    put(x0 + 3, 39, (0x9a, 0x9a, 0x9a))
    put(x0 + 3, 41, (0x9a, 0x9a, 0x9a))

# Слоты инвентаря игрока: 3 ряда с y=94 и хотбар с y=152 (как в ElectrolyzerMenu)
def slot(x, y):
    recess(x, y, 16, 16, (0x0e, 0x08, 0x05), dark=(0x0a, 0x05, 0x03), light=(0x45, 0x26, 0x19))


for row in range(3):
    for col in range(9):
        slot(8 + col * 18, 94 + row * 18)
for col in range(9):
    slot(8 + col * 18, 152)

png.write(sys.argv[1], px)
