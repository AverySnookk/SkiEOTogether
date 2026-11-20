"""Generates the starter building templates (original designs) as JSON.
Run: python3 tools/make_templates.py   (writes core/src/main/resources/blockside/templates)"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'core', 'src', 'main', 'resources', 'blockside', 'templates')

def box_layers(w, d, h, fn):
    return [[''.join(fn(x, y, z) for x in range(w)) for z in range(d)] for y in range(h)]

def write(tid, name, w, h, d, palette, fn, entrance):
    layers = box_layers(w, d, h, fn)
    flat = ''.join(''.join(r) for l in layers for r in l)
    assert flat.count('@') == 1, (tid, flat.count('@'))
    data = {"id": tid, "name": name, "size": [w, h, d], "entrance": entrance,
            "palette": {k: {"block": v[0], "stage": v[1]} for k, v in palette.items()},
            "layers": layers}
    with open(os.path.join(OUT, tid + '.json'), 'w') as f:
        json.dump(data, f, indent=1)

def edge(x, z, w, d):
    return x in (0, w - 1) or z in (0, d - 1)

def corner(x, z, w, d):
    return x in (0, w - 1) and z in (0, d - 1)

# Ticket booth: 5x5, spruce kiosk with a service window facing the guests (south / +z).
def booth(x, y, z):
    w, d = 5, 5
    if y == 0: return 'F'
    if (x, y, z) == (2, 1, 2): return '@'
    if y == 4: return 'R'
    if y == 3: return 'L' if edge(x, z, w, d) else '.'
    if edge(x, z, w, d):
        if corner(x, z, w, d): return 'L'
        if z == 4 and x == 2: return '.'               # door
        if y == 2 and (z == 4 or x in (0, 4)): return 'G'  # windows
        return 'W'
    if (x, y, z) == (1, 1, 1): return 'B'
    if (x, y, z) == (1, 2, 1): return 'N'
    if (x, y, z) == (3, 1, 1): return 'K'
    return '.'

PAL_BOOTH = {'F': ('minecraft:stone_bricks', 'FOUNDATION'), 'W': ('minecraft:spruce_planks', 'STRUCTURE'),
             'L': ('minecraft:stripped_spruce_log', 'STRUCTURE'), 'G': ('minecraft:glass_pane', 'STRUCTURE'),
             'R': ('minecraft:dark_oak_slab', 'STRUCTURE'), 'B': ('minecraft:barrel', 'EQUIPMENT'),
             'N': ('minecraft:lantern', 'EQUIPMENT'), 'K': ('minecraft:cartography_table', 'EQUIPMENT')}
write('ticket_booth', 'Ticket Booth', 5, 5, 5, PAL_BOOTH, booth, [2, 1, 4])

# Rental shop: 7x7 with ski racks (fences) and a fitting counter.
def rental(x, y, z):
    w, d = 7, 7
    if y == 0: return 'F'
    if (x, y, z) == (3, 1, 3): return '@'
    if y == 5: return 'R'
    if y == 4: return 'L' if edge(x, z, w, d) else '.'
    if edge(x, z, w, d):
        if corner(x, z, w, d): return 'L'
        if z == 6 and x in (2, 3, 4) and y <= 2: return '.'
        if y == 2 and x in (0, 6) and z in (2, 4): return 'G'
        if y == 2 and z == 6: return 'G'
        return 'W'
    if z == 1 and x in (1, 2, 4, 5) and y == 1: return 'P'
    if z == 1 and x in (1, 2, 4, 5) and y == 2: return 'S'
    if (x, y, z) == (5, 1, 4): return 'B'
    if (x, y, z) == (1, 1, 4): return 'K'
    if (x, y, z) == (3, 3, 3): return 'N'
    return '.'

PAL_RENTAL = {'F': ('minecraft:polished_andesite', 'FOUNDATION'), 'W': ('minecraft:birch_planks', 'STRUCTURE'),
              'L': ('minecraft:stripped_dark_oak_log', 'STRUCTURE'), 'G': ('minecraft:glass_pane', 'STRUCTURE'),
              'R': ('minecraft:spruce_slab', 'STRUCTURE'), 'P': ('minecraft:spruce_fence', 'EQUIPMENT'),
              'S': ('minecraft:light_blue_carpet', 'EQUIPMENT'), 'B': ('minecraft:barrel', 'EQUIPMENT'),
              'K': ('minecraft:loom', 'EQUIPMENT'), 'N': ('minecraft:lantern', 'EQUIPMENT')}
write('rental_shop', 'Rental Shop', 7, 6, 7, PAL_RENTAL, rental, [3, 1, 6])

# Groomer garage: 7 wide, 9 deep, 3-wide door on the south side.
def garage(x, y, z):
    w, d = 7, 9
    if y == 0: return 'F'
    if (x, y, z) == (3, 1, 1): return '@'
    if y == 5: return 'R'
    if y == 4: return 'L' if edge(x, z, w, d) else '.'
    if edge(x, z, w, d):
        if corner(x, z, w, d): return 'L'
        if z == 8 and 1 <= x <= 5 and y <= 3: return '.'
        if y == 1: return 'S'
        if y == 2 and x in (0, 6) and z in (3, 5): return 'G'
        return 'W'
    if (x, y, z) == (1, 1, 1): return 'A'
    if (x, y, z) == (5, 1, 1): return 'B'
    if (x, y, z) == (3, 3, 4): return 'N'
    return '.'

PAL_GARAGE = {'F': ('minecraft:polished_andesite', 'FOUNDATION'), 'W': ('minecraft:spruce_planks', 'STRUCTURE'),
              'S': ('minecraft:stone_bricks', 'STRUCTURE'), 'L': ('minecraft:stripped_spruce_log', 'STRUCTURE'),
              'G': ('minecraft:glass_pane', 'STRUCTURE'), 'R': ('minecraft:dark_oak_slab', 'STRUCTURE'),
              'A': ('minecraft:smithing_table', 'EQUIPMENT'), 'B': ('minecraft:blast_furnace', 'EQUIPMENT'),
              'N': ('minecraft:lantern', 'EQUIPMENT')}
write('groomer_garage', 'Groomer Garage', 7, 6, 9, PAL_GARAGE, garage, [3, 1, 8])

# Small lodge: 9x9, fireplace, tables, two windows per side.
def lodge(x, y, z):
    w, d = 9, 9
    if y == 0: return 'F'
    if (x, y, z) == (4, 1, 4): return '@'
    if y == 6: return 'R' if 1 <= x <= 7 and 1 <= z <= 7 else 'r'
    if y == 5: return 'L' if edge(x, z, w, d) else '.'
    if edge(x, z, w, d):
        if corner(x, z, w, d): return 'L'
        if z == 8 and x == 4 and y <= 2: return '.'
        if y in (2, 3) and (x in (2, 6) or z in (2, 6)): return 'G'
        return 'W'
    if z == 1 and x == 4 and y <= 3: return 'C'
    if z == 2 and x == 4 and y == 1: return 'H'
    if (x, y, z) in ((2, 1, 5), (6, 1, 5)): return 'T'
    if (x, y, z) in ((2, 1, 6), (6, 1, 6), (2, 1, 4), (6, 1, 4)): return 'S'
    if (x, y, z) == (4, 4, 5): return 'N'
    return '.'

PAL_LODGE = {'F': ('minecraft:stone_bricks', 'FOUNDATION'), 'W': ('minecraft:spruce_planks', 'STRUCTURE'),
             'L': ('minecraft:spruce_log', 'STRUCTURE'), 'G': ('minecraft:glass_pane', 'STRUCTURE'),
             'R': ('minecraft:dark_oak_planks', 'STRUCTURE'), 'r': ('minecraft:dark_oak_slab', 'STRUCTURE'),
             'C': ('minecraft:cobblestone', 'STRUCTURE'), 'H': ('minecraft:campfire', 'EQUIPMENT'),
             'T': ('minecraft:spruce_fence', 'EQUIPMENT'), 'S': ('minecraft:spruce_slab', 'EQUIPMENT'),
             'N': ('minecraft:lantern', 'EQUIPMENT')}
write('small_lodge', 'Small Lodge', 9, 7, 9, PAL_LODGE, lodge, [4, 1, 8])

# Lift lower station: open platform with four posts and a roof; uphill is template north (-z).
def lower(x, y, z):
    w, d = 5, 7
    if y == 0: return 'F' if not (x == 2 and z == 6) else 'M'
    if (x, y, z) == (2, 1, 3): return '@'
    if y == 5: return 'R'
    if corner(x, z, w, d): return 'P'
    if y == 4 and edge(x, z, w, d): return 'B'
    if (x, y, z) in ((0, 1, 5), (4, 1, 5)): return 'Q'
    return '.'

PAL_LOWER = {'F': ('minecraft:polished_andesite', 'FOUNDATION'), 'M': ('minecraft:yellow_concrete', 'FOUNDATION'),
             'P': ('minecraft:stripped_dark_oak_log', 'STRUCTURE'), 'B': ('minecraft:dark_oak_planks', 'STRUCTURE'),
             'R': ('minecraft:dark_oak_slab', 'STRUCTURE'), 'Q': ('minecraft:lantern', 'EQUIPMENT')}
write('lift_lower_station', 'Lift Lower Station', 5, 6, 7, PAL_LOWER, lower, [2, 1, 6])

def upper(x, y, z):
    w, d = 5, 5
    if y == 0: return 'F' if not (x == 2 and z == 0) else 'M'
    if (x, y, z) == (2, 1, 2): return '@'
    if y == 5: return 'R'
    if corner(x, z, w, d): return 'P'
    if y == 4 and edge(x, z, w, d): return 'B'
    return '.'

write('lift_upper_station', 'Lift Upper Station', 5, 6, 5, PAL_LOWER, upper, [2, 1, 0])
print('templates written to', os.path.normpath(OUT))
