"""Generates Blockside Resort's original 16x16 textures and JSON models/blockstates.
Run: python3 tools/make_assets.py"""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'neoforge', 'src', 'main', 'resources')
A = os.path.join(ROOT, 'assets', 'blockside')
D = os.path.join(ROOT, 'data', 'blockside')
random.seed(7)

def path(*p):
    full = os.path.join(*p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full

def dump(obj, *p):
    with open(path(*p), 'w') as f:
        json.dump(obj, f, indent=2)

def tex(name, fn, kind='block'):
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), fn(x, y))
    img.save(path(A, 'textures', kind, name + '.png'))

def jitter(c, n=8):
    return tuple(max(0, min(255, v + random.randint(-n, n))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)

# --- block textures
tex('job_block', lambda x, y: (30, 30, 30, 255) if ((x + y) // 3) % 2 else (240, 190, 30, 255)
    if not (5 <= x <= 10 and 5 <= y <= 10) else ((250, 250, 250, 255) if (x in (5, 10) or y in (5, 10)) else (40, 110, 200, 255)))
tex('groomed_snow', lambda x, y: jitter((232, 238, 246) if y % 3 else (206, 216, 230), 4))           # corduroy ridges
tex('groomed_snow_side', lambda x, y: jitter((236, 240, 247), 5))
tex('icy_snow', lambda x, y: jitter((196, 222, 240) if (x * 3 + y * 5) % 11 else (235, 247, 255), 4))
tex('tower_section', lambda x, y: (120, 125, 132, 255) if x in (0, 1, 14, 15) or (x == y) or (x == 15 - y) else (0, 0, 0, 0))
tex('snow_gun_body', lambda x, y: jitter((70, 76, 86), 6) if (y // 4) % 2 else jitter((215, 60, 45), 6))
tex('snow_gun_base', lambda x, y: jitter((92, 96, 104), 6))
tex('snow_gun_fan', lambda x, y: (40, 44, 52, 255) if (x - 7.5) ** 2 + (y - 7.5) ** 2 > 42 else
    ((200, 210, 220, 255) if (x + y) % 4 < 2 else (150, 160, 170, 255)))

# --- item textures (pixel art)
def skis(x, y):
    if x in (5, 6) and 1 <= y <= 15 or x in (9, 10) and 0 <= y <= 14:
        if y in (7, 8): return (30, 30, 30, 255)
        return (220, 40, 40, 255) if y < 4 or y > 11 else (240, 240, 245, 255)
    return (0, 0, 0, 0)
tex('skis', skis, 'item')
def tablet(x, y):
    if 2 <= x <= 13 and 1 <= y <= 14:
        if 3 <= x <= 12 and 2 <= y <= 11:
            if y >= 8 and x % 3 == 0 and y >= 11 - (x // 3): return (90, 220, 120, 255)
            return (40, 70, 110, 255) if y > 4 else (240, 190, 30, 255)
        return (50, 50, 55, 255)
    return (0, 0, 0, 0)
tex('resort_tablet', tablet, 'item')
def stake(x, y):
    if x in (7, 8) and 3 <= y <= 15: return (150, 105, 60, 255)
    if 4 <= x <= 11 and 1 <= y <= 4: return (255, 120, 20, 255)
    return (0, 0, 0, 0)
tex('survey_stake', stake, 'item')
def kit(x, y):
    if 1 <= x <= 14 and 3 <= y <= 14:
        if x in (1, 14) or y in (3, 14) or x == y or x == 17 - y: return (110, 75, 40, 255)
        return (180, 135, 80, 255)
    if 5 <= x <= 10 and 0 <= y <= 2: return (240, 190, 30, 255)
    return (0, 0, 0, 0)
tex('project_kit', kit, 'item')

# --- models & blockstates
def cube_all(name, texture):
    dump({"parent": "minecraft:block/cube_all", "textures": {"all": "blockside:block/" + texture}}, A, 'models', 'block', name + '.json')

cube_all('job_block', 'job_block')
dump({"variants": {"": {"model": "blockside:block/job_block"}}}, A, 'blockstates', 'job_block.json')
dump({"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
      "textures": {"all": "blockside:block/tower_section"}}, A, 'models', 'block', 'tower_section.json')
dump({"variants": {"": {"model": "blockside:block/tower_section"}}}, A, 'blockstates', 'tower_section.json')

for snow, top, side in (('groomed_snow', 'groomed_snow', 'groomed_snow_side'), ('icy_snow', 'icy_snow', 'icy_snow')):
    variants = {}
    for n in range(1, 9):
        h = n * 2
        model = {"textures": {"particle": "blockside:block/" + side, "top": "blockside:block/" + top, "side": "blockside:block/" + side},
                 "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": {
                     "down": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "down"},
                     "up": {"uv": [0, 0, 16, 16], "texture": "#top"} if n < 8 else {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up"},
                     "north": {"uv": [0, 16 - h, 16, 16], "texture": "#side", "cullface": "north"},
                     "south": {"uv": [0, 16 - h, 16, 16], "texture": "#side", "cullface": "south"},
                     "west": {"uv": [0, 16 - h, 16, 16], "texture": "#side", "cullface": "west"},
                     "east": {"uv": [0, 16 - h, 16, 16], "texture": "#side", "cullface": "east"}}}]}
        dump(model, A, 'models', 'block', f'{snow}_height{h}.json')
        variants[f'layers={n}'] = {"model": f"blockside:block/{snow}_height{h}"}
    dump({"variants": variants}, A, 'blockstates', snow + '.json')

# Snow gun: base, tower, fan barrel pointing north (-z).
gun = {"textures": {"particle": "blockside:block/snow_gun_body", "body": "blockside:block/snow_gun_body",
                    "base": "blockside:block/snow_gun_base", "fan": "blockside:block/snow_gun_fan"},
       "elements": [
           {"from": [3, 0, 3], "to": [13, 3, 13], "faces": {f: {"texture": "#base"} for f in ("down", "up", "north", "south", "west", "east")}},
           {"from": [7, 3, 7], "to": [9, 8, 9], "faces": {f: {"texture": "#base"} for f in ("north", "south", "west", "east")}},
           {"from": [4, 7, 2], "to": [12, 14, 13], "rotation": {"angle": 22.5, "axis": "x", "origin": [8, 8, 8]},
            "faces": {"north": {"texture": "#fan"}, "south": {"texture": "#fan"}, "up": {"texture": "#body"},
                      "down": {"texture": "#body"}, "west": {"texture": "#body"}, "east": {"texture": "#body"}}}]}
dump(gun, A, 'models', 'block', 'snow_gun.json')
dump({"variants": {f"facing={f}": {"model": "blockside:block/snow_gun", **({"y": r} if r else {})}
                   for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}, A, 'blockstates', 'snow_gun.json')

# Items: model definitions (assets/<ns>/items) + item models.
for item in ('skis', 'resort_tablet', 'survey_stake', 'project_kit'):
    dump({"parent": "minecraft:item/generated", "textures": {"layer0": "blockside:item/" + item}}, A, 'models', 'item', item + '.json')
    dump({"model": {"type": "minecraft:model", "model": "blockside:item/" + item}}, A, 'items', item + '.json')
for block in ('snow_gun', 'tower_section'):
    dump({"model": {"type": "minecraft:model", "model": "blockside:block/" + block}}, A, 'items', block + '.json')

dump({
    "itemGroup.blockside": "Blockside Resort",
    "block.blockside.job_block": "Construction Site",
    "block.blockside.snow_gun": "Snow Gun",
    "block.blockside.groomed_snow": "Groomed Snow",
    "block.blockside.icy_snow": "Icy Snow",
    "block.blockside.tower_section": "Lift Tower Section",
    "item.blockside.skis": "Skis",
    "item.blockside.resort_tablet": "Resort Tablet",
    "item.blockside.survey_stake": "Survey Stake",
    "item.blockside.project_kit": "Project Kit",
}, A, 'lang', 'en_us.json')

# Loot: the snow gun and tower sections drop themselves; job blocks and managed snow drop nothing.
for b in ('snow_gun', 'tower_section'):
    dump({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "blockside:" + b}],
          "conditions": [{"condition": "minecraft:survives_explosion"}]}]}, D, 'loot_table', 'blocks', b + '.json')
print('assets written')
