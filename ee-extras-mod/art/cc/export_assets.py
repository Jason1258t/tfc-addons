#!/usr/bin/env python3
"""Механический crop/resize одобренных исходников в игровые PNG 32×32."""
from pathlib import Path
from PIL import Image, ImageDraw
import json

ROOT = Path(__file__).resolve().parent
RUNTIME = ROOT.parents[1] / "src" / "main" / "resources" / "assets" / "ee_extras" / "textures" / "item"
RUNTIME.mkdir(parents=True, exist_ok=True)
report = []
for source in sorted((ROOT / "src").glob("*.png")):
    image = Image.open(source).convert("RGBA")
    alpha = image.getchannel("A")
    if alpha.getextrema()[0] == 255:
        raise ValueError(f"{source.name}: no transparent background")
    bbox = alpha.getbbox()
    if not bbox:
        raise ValueError(f"{source.name}: empty")
    cropped = image.crop(bbox)
    # Два пикселя прозрачного поля в игровом размере; пропорции и альфа сохраняются.
    cropped.thumbnail((28, 28), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    result.alpha_composite(cropped, ((32 - cropped.width) // 2, (32 - cropped.height) // 2))
    # Убираем только почти невидимый альфа-шум ресемплинга, не цвет фона.
    a = result.getchannel("A").point(lambda value: 0 if value < 16 else value)
    result.putalpha(a)
    result.save(RUNTIME / source.name)
    report.append({"id": source.stem, "source": str(source), "runtime": str(RUNTIME / source.name),
                   "source_size": list(image.size), "runtime_size": [32, 32], "alpha_bbox": list(bbox)})
(ROOT / "assets-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Светлый и тёмный листы: игровой размер увеличен только nearest-neighbour для проверки.
for background, name in [("#d8d8d8", "runtime-light.png"), ("#20242a", "runtime-dark.png")]:
    cell_w, cell_h, columns = 180, 176, 5
    rows = (len(report) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * cell_w, rows * cell_h), background)
    draw = ImageDraw.Draw(sheet)
    for index, entry in enumerate(report):
        image = Image.open(entry["runtime"]).convert("RGBA").resize((128, 128), Image.Resampling.NEAREST)
        x = (index % columns) * cell_w + 26
        y = (index // columns) * cell_h + 8
        sheet.paste(image, (x, y), image)
        draw.text(((index % columns) * cell_w + 4, (index // columns) * cell_h + 144), entry["id"],
                  fill="#181818" if name == "runtime-light.png" else "#eeeeee")
    sheet.save(ROOT / "previews" / name)
print(f"Экспортировано {len(report)} текстур 32×32 в {RUNTIME}")
