from pathlib import Path
from PIL import Image
import json

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parents[1] / "src/main/resources/assets/ee_extras"
atlas = Image.open(ROOT / "src/block_atlas.png").convert("RGBA")
names = [
    "solar_panel_top",
    "thermometer_top",
    "pcb_design_table_top",
    "pcb_photolithographer_top",
    "solar_panel_side",
    "thermometer_side",
    "pcb_design_table_side",
    "pcb_photolithographer_side",
    "pcb_via_metallizer_side",
    "pcb_via_metallizer_inner",
    "pcb_via_metallizer_rim",
    "pcb_photolithographer_port",
    "electronics_terminal_red",
    "electronics_terminal_blue",
    "electronics_bottom",
    "electronics_board",
]
for i, name in enumerate(names):
    x = i % 4
    y = i // 4
    tile = atlas.crop(
        (
            round(x * atlas.width / 4),
            round(y * atlas.height / 4),
            round((x + 1) * atlas.width / 4),
            round((y + 1) * atlas.height / 4),
        )
    ).resize((64, 64), Image.Resampling.NEAREST)
    tile.save(ASSETS / "textures/block" / f"{name}.png")


def put(rel, obj):
    p = ASSETS / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n")


def cube(a, b, side, top=None, bottom=None, uv=None):
    faces = {
        d: {"texture": "#" + side, "uv": uv or [0, 0, 16, 16]}
        for d in ["north", "east", "south", "west"]
    }
    faces["up"] = {"texture": "#" + (top or side), "uv": [0, 0, 16, 16]}
    faces["down"] = {"texture": "#" + (bottom or side), "uv": [0, 0, 16, 16]}
    return {"from": a, "to": b, "faces": faces}


# Геометрия и ориентации приборов сохранены, клеммы получают собственный материал.
for name in ["solar_panel", "thermometer"]:
    p = ASSETS / f"models/block/{name}/block.json"
    m = json.loads(p.read_text())
    m["textures"]["red"] = "ee_extras:block/electronics_terminal_red"
    m["textures"]["blue"] = "ee_extras:block/electronics_terminal_blue"
    m["textures"]["bottom"] = "ee_extras:block/electronics_bottom"
    m["elements"][0]["faces"]["down"]["texture"] = "#bottom"
    put(f"models/block/{name}/block.json", m)
# Деревянные ножки используют фрагмент дерева с правильным соотношением сторон.
p = ASSETS / "models/block/pcb_design_table.json"
m = json.loads(p.read_text())
for i, e in enumerate(m["elements"]):
    for face, data in e["faces"].items():
        if i:
            data["texture"] = "#side"
            data["uv"] = [0, 0, 2, 2] if face in ["up", "down"] else [0, 0, 2, 12]
        elif face not in ["up", "down"]:
            data["uv"] = [0, 0, 16, 4]
put("models/block/pcb_design_table.json", m)
# Сохраняем форму пресса Create, заменяем только материалы его граней.
put(
    "models/block/pcb_photolithographer.json",
    {
        "parent": "create:block/mechanical_press/block",
        "textures": {
            "4": "ee_extras:block/pcb_photolithographer_side",
            "particle": "ee_extras:block/pcb_photolithographer_side",
            "gearbox_top": "ee_extras:block/solar_panel_side",
            "gearbox": "ee_extras:block/pcb_photolithographer_port",
            "mechanical_press_top": "ee_extras:block/pcb_photolithographer_top",
            "mechanical_press_bottom": "ee_extras:block/electronics_bottom",
        },
    },
)
# Открытая ёмкость прежних внешних размеров; отдельные внутренние грани вместо перевёрнутого куба.
tex = {
    k: "ee_extras:block/" + v
    for k, v in {
        "side": "pcb_via_metallizer_side",
        "inner": "pcb_via_metallizer_inner",
        "rim": "pcb_via_metallizer_rim",
        "bottom": "electronics_bottom",
        "particle": "pcb_via_metallizer_side",
    }.items()
}
els = [
    cube([2, 0, 2], [14, 2, 14], "bottom", "inner"),
    cube([0, 2, 0], [16, 4, 16], "side", "inner", "bottom"),
]
for a, b, inner in [
    ([0, 4, 0], [16, 16, 2], "south"),
    ([0, 4, 14], [16, 16, 16], "north"),
    ([0, 4, 2], [2, 16, 14], "east"),
    ([14, 4, 2], [16, 16, 14], "west"),
]:
    e = cube(a, b, "side", "rim", "bottom")
    e["faces"][inner]["texture"] = "#inner"
    els.append(e)
put(
    "models/block/pcb_via_metallizer.json",
    {"parent": "minecraft:block/block", "textures": tex, "elements": els},
)
for name in ["pcb_photolithographer", "pcb_via_metallizer"]:
    put(f"models/item/{name}.json", {"parent": f"ee_extras:block/{name}"})
    p = ASSETS / f"blockstates/{name}.json"
    data = json.loads(p.read_text())
    for variant in data["variants"].values():
        variant["model"] = f"ee_extras:block/{name}"
    put(f"blockstates/{name}.json", data)
print("Блоки: 5, текстуры: 16")
