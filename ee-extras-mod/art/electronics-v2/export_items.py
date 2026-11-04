from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parents[1] / "src/main/resources/assets/ee_extras"
rows = []
for source in sorted((ROOT / "src").glob("*.png")):
    if source.stem == "block_atlas":
        continue
    image = Image.open(source).convert("RGBA")
    alpha = image.getchannel("A")
    if alpha.getextrema()[0] == 255:
        raise ValueError(f"Нет прозрачности: {source.name}")
    # Отсекаем только почти невидимый альфа-шум; цвет фона не используется.
    bbox = alpha.point(lambda v: 255 if v >= 32 else 0).getbbox()
    if not bbox:
        raise ValueError(f"Пустая текстура: {source.name}")
    for size in (32, 64):
        cropped = image.crop(bbox)
        cropped.thumbnail((size - 4, size - 4), Image.Resampling.LANCZOS)
        runtime = Image.new("RGBA", (size, size))
        runtime.alpha_composite(
            cropped, ((size - cropped.width) // 2, (size - cropped.height) // 2)
        )
        runtime.putalpha(runtime.getchannel("A").point(lambda v: 0 if v < 32 else v))
        dest = ROOT / f"runtime-{size}"
        dest.mkdir(exist_ok=True)
        runtime.save(dest / source.name)
    rows.append(
        {
            "id": source.stem,
            "source": f"src/{source.name}",
            "sourceSize": image.size,
            "alphaBounds": bbox,
            "runtimeSize": 64,
        }
    )
    # 64 сохраняет маркировку и кромки; исходник остаётся отдельно.
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    Image.open(ROOT / "runtime-64" / source.name).save(
        ASSETS / "textures/item" / source.name
    )
    model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"ee_extras:item/{source.stem}"},
    }
    (ASSETS / "models/item" / f"{source.stem}.json").write_text(
        json.dumps(model, ensure_ascii=False, indent=2) + "\n"
    )
try:
    font = ImageFont.truetype("Arial.ttf", 13)
except OSError:
    font = ImageFont.load_default()
for size in (32, 64):
    for bg, label in [("#20242a", "dark"), ("#d8d8d8", "light")]:
        sheet = Image.new("RGB", (6 * 180, ((len(rows) + 5) // 6) * 190), bg)
        draw = ImageDraw.Draw(sheet)
        for i, row in enumerate(rows):
            im = Image.open(ROOT / f"runtime-{size}" / (row["id"] + ".png"))
            x = i % 6 * 180
            y = i // 6 * 190
            sheet.paste(
                im.resize((128, 128), Image.Resampling.NEAREST),
                (x + 26, y + 8),
                im.resize((128, 128), Image.Resampling.NEAREST),
            )
            sheet.paste(
                im.resize((32, 32), Image.Resampling.LANCZOS),
                (x + 8, y + 142),
                im.resize((32, 32), Image.Resampling.LANCZOS),
            )
            draw.text(
                (x + 4, y + 177),
                row["id"],
                font=font,
                fill="#eee" if label == "dark" else "#111",
            )
        sheet.save(ROOT / "previews" / f"items-{size}-{label}.png")
(ROOT / "assets-report.json").write_text(
    json.dumps(rows, ensure_ascii=False, indent=2) + "\n"
)
print("Предметов:", len(rows))
