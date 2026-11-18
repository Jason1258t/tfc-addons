# Текстуры транспортного дизеля

Источник: встроенный ImageGen. Референс: `art/preview-modules.png` (корпус бортового компьютера и модули).
Оригинал: `sources/transport_engine.png`. Игровые поверхности: четыре PNG 32×32, 16 цветов на поверхность.
Из исходника вырезаны квадраты (20,20)–(608,608), (647,20)–(1235,608), (20,647)–(608,1235), (647,647)–(1235,1235);
уменьшение nearest-neighbour, затем квантование до 16 цветов. Pillow нужен только для подготовки, сборка использует готовые PNG.
Вал использует проверенные MCP текстуры Create `block/axis` и `block/axis_top`.

Превью собранной модели: `preview-transport-engine.png`. Геометрия — `gen_transport_engine.py`: открытые цилиндры, топливная аппаратура, коллекторы, поддон, маховик и лапы.

## Запрос

Use case: stylized-concept. Asset type: actual Minecraft block face texture atlas, NOT a rendered object. Generate one square atlas consisting of exactly four equal square tiles in a 2 by 2 grid, no gutters, no labels. Each tile is precisely 16 by 16 logical pixels enlarged with sharp nearest-neighbor edges; total logical atlas 32x32. Theme: compact diesel transport engine for Create Aeronautics. Match the attached reference's restrained flat dark graphite metal panels, steel edges and small brass fittings, simple functional pixel art. Top left tile: engine side panel with four dark horizontal cooling louvres, graphite casing, four steel corner fasteners and one small brass fuel fitting at bottom middle. Top right tile: opposite engine side, graphite crankcase with rectangular steel service hatch, four corner bolts, small restrained brass plate. Bottom left tile: top cylinder head, dark steel rectangular cover, two long straight cooling ribs, four bolts, small brass oil cap in corner. Bottom right tile: plain dark steel bottom mounting plate with four bolts and subtle rectangular inset. All tiles flat orthographic front-facing, opaque, full bleed. Very few flat shades, high readability at actual 16x16, no gradients, no noise, no glossy highlights, no perspective, no text, no borders between tiles, no illustrated engine silhouette. Every tile is a surface texture filling its entire square.
