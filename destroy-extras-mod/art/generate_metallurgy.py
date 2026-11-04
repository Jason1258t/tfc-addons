#!/usr/bin/env python3
"""Детерминированные JSON-ресурсы M13; игровые текстуры берутся из TFC/Create."""
import json
from pathlib import Path

ROOT = Path(__file__).parents[1] / "src/main/resources"
NS = "destroy_extras"

def write(path, value):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")

def mixture(key, contents):
    return {"id": "destroy:mixture", "amount": 250, "components": {"destroy:mixture": {
        "TranslationKey": f"destroy_extras.fluid.{key}",
        "Contents": [{"Molecule": f"destroy:{m}", "Concentration": c} for m, c in contents],
    }}}

def fluid(key, contents, amount=250):
    value = mixture(key, contents)
    value["amount"] = amount
    return value

def salt(cation, anion, amount=250):
    return {"type": "destroy:mixture_with_salt", "cation": f"destroy:{cation}", "anion": f"destroy:{anion}", "concentration": 1.0, "amount": amount}

def exact(value):
    ingredient = dict(value)
    ingredient["fluid"] = ingredient.pop("id")
    return ingredient

def molecule(name, amount=250, concentration=1.0):
    return {"type": "destroy:mixture_with_molecule", "molecule": f"destroy:{name}", "concentration": concentration, "amount": amount}

def recipe(name, items=(), fluids=(), item_results=(), fluid_results=(), time=200, voltage=240, current=4, catalyst=None, priority=0):
    normalized_fluids = []
    for ingredient in fluids:
        if "fluid" in ingredient and "type" not in ingredient:
            ingredient = {"type": "neoforge:single", **ingredient}
        normalized_fluids.append(ingredient)
    data = {"type": "destroy_extras:metallurgy", "ingredients": [{"item": i} for i in items] + normalized_fluids,
            "results": [{"id": i} if isinstance(i, str) else i for i in item_results] + list(fluid_results),
            "processing_time": time, "voltage": voltage, "current": current, "priority": priority}
    if catalyst:
        data["catalyst"] = catalyst
    write(f"data/{NS}/recipe/metallurgy/{name}.json", data)

acid = [("proton", 2), ("sulfate", 1), ("water", 10)]
naoh = [("sodium_ion", 1), ("hydroxide", 1), ("water", 4)]
aqua_regia = [("proton", 4), ("chloride", 3), ("nitrate", 1), ("water", 10)]
spent_etchant = [("iron_ii", 6), ("copper_ii", 3), ("chloride", 18), ("water", 20)]
solutions = {
    "copper": [("copper_ii", 2), ("sulfate", 2), ("water", 10)],
    "nickel": [("nickel_ion", 2), ("sulfate", 2), ("water", 10)],
    "zinc": [("zinc_ion", 2), ("sulfate", 2), ("water", 10)],
    "iron": [("iron_ii", 2), ("sulfate", 2), ("water", 10)],
}
so2 = fluid("sulfur_dioxide", [("sulfur_dioxide", 1)], 100)
co2 = fluid("carbon_dioxide", [("carbon_dioxide", 1)], 50)
recipe("roast_tetrahedrite", ["tfc:powder/tetrahedrite"], item_results=[f"{NS}:roasted_tetrahedrite", f"{NS}:gangue"], fluid_results=[so2], voltage=240, current=6)
recipe("roast_sphalerite", ["tfc:powder/sphalerite"], item_results=[f"{NS}:roasted_sphalerite", f"{NS}:gangue"], fluid_results=[so2], voltage=240, current=6)
recipe("leach_malachite", ["tfc:powder/malachite"], [salt("proton", "sulfate")], [f"{NS}:gangue"], [fluid("copper_sulfate", solutions["copper"])])
recipe("leach_tetrahedrite", [f"{NS}:roasted_tetrahedrite"], [salt("proton", "sulfate")], [f"{NS}:gangue"], [fluid("copper_sulfate", solutions["copper"])])
recipe("leach_garnierite", ["tfc:powder/garnierite"], [salt("proton", "sulfate")], [f"{NS}:gangue"], [fluid("nickel_sulfate", solutions["nickel"])], time=300)
recipe("leach_sphalerite", [f"{NS}:roasted_sphalerite"], [salt("proton", "sulfate")], [f"{NS}:gangue"], [fluid("zinc_sulfate", solutions["zinc"])])
for metal, fluid_id in (("copper", "tfc:metal/copper"), ("nickel", "tfc:metal/nickel"), ("zinc", "tfc:metal/zinc")):
    cation = {"copper": "copper_ii", "nickel": "nickel_ion", "zinc": "zinc_ion"}[metal]
    recipe(f"electrowin_{metal}", [], [salt(cation, "sulfate")], [], [
        {"id": fluid_id, "amount": 5}, fluid("dilute_sulfuric_acid", acid, 200)], time=300, voltage=600, current=8)
recipe("cement_copper", ["immersiveengineering:dust_iron"], [salt("copper_ii", "sulfate")], [f"{NS}:cement_copper"], [fluid("iron_sulfate", solutions["iron"], 200)], time=200, voltage=240, current=2)
sulfide = fluid("sulfide_reagent", [("sodium_ion", 2), ("sulfide", 1), ("water", 4)], 50)
recipe("precipitate_nickel", [], [salt("nickel_ion", "sulfate"), molecule("sulfide", 50)], [f"{NS}:nickel_sulfide"], [fluid("dilute_sulfuric_acid", acid, 200)], time=200, voltage=240, current=2)
recipe("dissolve_nickel_sulfide", [f"{NS}:nickel_sulfide"], [salt("proton", "sulfate")], [], [fluid("nickel_sulfate", solutions["nickel"]), sulfide], time=200, voltage=240, current=4)
aluminate = fluid("sodium_aluminate", naoh)
recipe("bayer_digest", ["tfc_ie_addon:powder/bauxite"], [salt("sodium_ion", "hydroxide")], [f"{NS}:red_mud"], [aluminate], time=300, voltage=240, current=6)
recipe("bayer_precipitation", [], [exact(aluminate)], [f"{NS}:aluminum_hydroxide"], [fluid("recovered_sodium_hydroxide", naoh, 200)], time=400, voltage=240, current=2, priority=-10)
recipe("bayer_precipitation_seeded", [], [exact(aluminate)], [f"{NS}:aluminum_hydroxide"], [fluid("recovered_sodium_hydroxide", naoh, 225)], time=200, voltage=240, current=2, catalyst=f"{NS}:aluminum_hydroxide", priority=-10)
recipe("calcination", [f"{NS}:aluminum_hydroxide"], item_results=[f"{NS}:alumina"], fluid_results=[{"id": "minecraft:water", "amount": 50}], time=300, voltage=600, current=6)
recipe("hall_heroult", [f"{NS}:alumina", "tfc:powder/coke"], item_results=[], fluid_results=[{"id": "tfc_ie_addon:metal/aluminum", "amount": 5}, co2], time=400, voltage=600, current=16, catalyst="tfc:ore/cryolite")
recipe("red_mud_washing", [f"{NS}:red_mud"], [{"fluid": "minecraft:water", "amount": 250}], [f"{NS}:iron_residue"], [fluid("recovered_sodium_hydroxide", naoh, 100)], time=200, voltage=240, current=2)
for source in ("gangue", "red_mud"):
    recipe(f"stabilize_{source}", [f"{NS}:{source}", "tfc:powder/lime"], item_results=[f"{NS}:stabilized_tailings"], time=100, voltage=240, current=1)
recipe("regenerate_etchant", [], [{"type": "destroy:mixture_with_salt", "cation": "destroy:iron_ii", "anion": "destroy:chloride", "concentration": 6.0, "amount": 250}, molecule("chlorine", 50)], [], [
    fluid("regenerated_ferric_chloride", [("iron_iii", 6), ("chloride", 18), ("copper_ii", 3), ("water", 20)], 200),
    fluid("copper_chloride", [("copper_ii", 1), ("chloride", 2), ("water", 10)], 50)], time=300, voltage=600, current=8)
recipe("electronic_scrap_leaching", [f"{NS}:electronic_scrap"], [salt("proton", "sulfate")], [f"{NS}:precious_residue"], [fluid("copper_sulfate", solutions["copper"], 100)], time=300, voltage=240, current=4)
recipe("precious_metals_recovery", [f"{NS}:precious_residue", "tfc:powder/lime"], [salt("proton", "nitrate")], [f"{NS}:stabilized_tailings"], [
    {"id": "tfc:metal/gold", "amount": 1}, {"id": "tfc:metal/silver", "amount": 2}], time=400, voltage=600, current=8)

names = {
    "roasted_tetrahedrite": ("Обожжённый тетраэдрит", "Roasted tetrahedrite", "tfc:item/powder/tetrahedrite"),
    "roasted_sphalerite": ("Обожжённый сфалерит", "Roasted sphalerite", "tfc:item/powder/sphalerite"),
    "gangue": ("Промытая пустая порода", "Washed gangue", "tfc:item/powder/flux"),
    "iron_residue": ("Железный остаток", "Iron residue", "tfc:item/powder/limonite"),
    "red_mud": ("Красный шлам", "Red mud", "tfc:item/powder/hematite"),
    "aluminum_hydroxide": ("Гидроксид алюминия", "Aluminum hydroxide", "tfc:item/powder/kaolinite"),
    "alumina": ("Глинозём", "Alumina", "tfc:item/powder/kaolinite"),
    "cement_copper": ("Цементная медь", "Cement copper", "tfc:item/powder/native_copper"),
    "nickel_sulfide": ("Сульфидный концентрат никеля", "Nickel sulfide concentrate", "tfc:item/powder/garnierite"),
    "electronic_scrap": ("Электронный лом", "Electronic scrap", "create:item/crushed_raw_copper"),
    "precious_residue": ("Благородный анодный остаток", "Precious anode residue", "tfc:item/powder/native_gold"),
    "stabilized_tailings": ("Стабилизированные хвосты", "Stabilized tailings", "tfc:item/powder/flux"),
}
for item, (_, _, texture) in names.items():
    write(f"assets/{NS}/models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
write(f"assets/{NS}/models/item/metallurgical_processor.json", {"parent": f"{NS}:block/metallurgical_processor"})
write(f"assets/{NS}/models/block/metallurgical_processor.json", {"parent": "minecraft:block/orientable", "textures": {
    "top": "create:block/copper_casing", "front": "create:block/copper_casing", "side": "create:block/copper_casing"}})
write(f"assets/{NS}/blockstates/metallurgical_processor.json", {"variants": {"": {"model": f"{NS}:block/metallurgical_processor"}}})
write(f"data/{NS}/loot_table/blocks/metallurgical_processor.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:metallurgical_processor"}], "functions": [{"function": "minecraft:copy_components", "source": "block_entity", "include": ["minecraft:block_entity_data"]}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
ru = json.loads((ROOT / f"assets/{NS}/lang/ru_ru.json").read_text())
en = json.loads((ROOT / f"assets/{NS}/lang/en_us.json").read_text())
ru[f"block.{NS}.metallurgical_processor"] = "Металлургический процессор"
en[f"block.{NS}.metallurgical_processor"] = "Metallurgical processor"
for item, (r, e, _) in names.items():
    ru[f"item.{NS}.{item}"] = r
    en[f"item.{NS}.{item}"] = e
statuses = {"idle": ("Ожидание исходных материалов", "Waiting for ingredients"), "waiting_for_power": ("Ожидание питания EE", "Waiting for EE power"), "running": ("Партия обрабатывается", "Processing batch"), "blocked_output": ("Выход переполнен", "Output full"), "fault": ("Неисправность процессора", "Processor fault")}
for status, (r, e) in statuses.items():
    ru[f"status.{NS}.metallurgy.{status}"] = r
    en[f"status.{NS}.metallurgy.{status}"] = e
ru.update({f"goggles.{NS}.metallurgy.status": "Состояние: %s", f"goggles.{NS}.metallurgy.power": "Питание: %s В / %s А", f"destroy_extras.recipe.metallurgy": "Химическая металлургия"})
en.update({f"goggles.{NS}.metallurgy.status": "Status: %s", f"goggles.{NS}.metallurgy.power": "Power: %s V / %s A", f"destroy_extras.recipe.metallurgy": "Chemical metallurgy"})
fluids = {"copper_sulfate": ("Раствор сульфата меди", "Copper sulfate solution"), "nickel_sulfate": ("Раствор сульфата никеля", "Nickel sulfate solution"), "zinc_sulfate": ("Раствор сульфата цинка", "Zinc sulfate solution"), "iron_sulfate": ("Раствор сульфата железа", "Iron sulfate solution"), "dilute_sulfuric_acid": ("Разбавленная серная кислота", "Dilute sulfuric acid"), "sodium_aluminate": ("Алюминатный раствор", "Sodium aluminate solution"), "recovered_sodium_hydroxide": ("Возвратный раствор NaOH", "Recovered sodium hydroxide"), "sulfur_dioxide": ("Диоксид серы", "Sulfur dioxide"), "carbon_dioxide": ("Диоксид углерода", "Carbon dioxide"), "regenerated_ferric_chloride": ("Регенерированный FeCl₃", "Regenerated ferric chloride"), "copper_chloride": ("Медьсодержащий раствор", "Copper-bearing solution"), "sulfide_reagent": ("Сульфидный реагент", "Sulfide reagent"), "spent_etchant": ("Отработанный травильный раствор", "Spent etchant"), "chlorine": ("Хлор", "Chlorine"), "aqua_regia": ("Царская водка", "Aqua regia")}
for key, (r, e) in fluids.items():
    ru[f"destroy_extras.fluid.{key}"] = r
    en[f"destroy_extras.fluid.{key}"] = e
write(f"assets/{NS}/lang/ru_ru.json", ru)
write(f"assets/{NS}/lang/en_us.json", en)
print(f"Создано {len(list((ROOT / 'data' / NS / 'recipe' / 'metallurgy').glob('*.json')))} рецептов")
