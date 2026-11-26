"""Render the shipped 1.21.1 pixel assets. Requires Pillow."""

import json
from pathlib import Path

from PIL import Image, ImageDraw


ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/advanced_bulk_storage"
ID = "advanced_bulk_storage"
EDGE = "#242d3b"
DARK = "#171e2a"
WELL = "#253446"
SHADE = "#4a586b"
METAL = "#7e8f9f"
MID = "#a9b9c2"
LIGHT = "#d1dde1"
GLINT = "#edf3ee"
TEAL = ("#245c63", "#45ad9f", "#9befca")
BLUE = ("#2d5282", "#5c9dda", "#b5e8ff")
AMBER = ("#875431", "#d78a4d", "#ffe0a0")
VIOLET = ("#4e427e", "#9679d1", "#e7c9ff")
CIRCUIT = "#37656a"
CONTACT = "#d9ae68"


class Tile:
    def __init__(self):
        self.image = Image.new("RGBA", (16, 16))
        self.draw = ImageDraw.Draw(self.image)

    def box(self, coordinates, color):
        self.draw.rectangle(coordinates, fill=color)

    def line(self, coordinates, color):
        self.draw.line(coordinates, fill=color, width=1)

    def pixel(self, x, y, color):
        self.draw.point((x, y), fill=color)

    def shape(self, points, color):
        self.draw.polygon(points, fill=color)

    def save(self, path):
        destination = ASSETS / "textures" / (path + ".png")
        destination.parent.mkdir(parents=True, exist_ok=True)
        self.image.save(destination)


def bevel(tile, bounds, fill, bright, dark):
    x0, y0, x1, y1 = bounds
    tile.box(bounds, fill)
    tile.line([(x0, y1), (x0, y0), (x1, y0)], bright)
    tile.line([(x0 + 1, y1), (x1, y1), (x1, y0 + 1)], dark)


def node(tile, x, y, colors):
    low, mid, high = colors
    tile.box((x - 1, y - 1, x + 1, y + 1), low)
    tile.line([(x - 1, y - 1), (x + 1, y - 1), (x + 1, y + 1)], mid)
    tile.pixel(x, y, high)


def ring(tile, x, y, colors):
    low, medium, high = colors
    tile.shape([(x - 3, y - 1), (x - 1, y - 3), (x + 1, y - 3), (x + 3, y - 1),
                (x + 3, y + 1), (x + 1, y + 3), (x - 1, y + 3), (x - 3, y + 1)], low)
    tile.line([(x - 3, y), (x - 2, y - 2), (x, y - 3), (x + 2, y - 2), (x + 3, y)], medium)
    tile.line([(x - 2, y + 2), (x, y + 3), (x + 2, y + 2)], medium)
    tile.pixel(x, y, high)
    tile.pixel(x - 2, y - 2, high)
    tile.pixel(x + 2, y + 2, high)


def cell(name, tier, colors):
    tile = Tile()
    low, mid, high = colors
    # Low-profile slanted cartridge. A pale die-cast rail wraps around a separate colored insert.
    tile.shape([(0, 6), (4, 2), (7, 1), (10, 2), (15, 7), (15, 10),
                (12, 14), (9, 15), (6, 14), (0, 9)], "#293342")
    tile.shape([(1, 7), (6, 11), (9, 13), (9, 14), (6, 13), (1, 9)], "#63748b")
    tile.shape([(9, 13), (13, 10), (14, 8), (14, 10), (11, 13), (10, 14), (9, 14)], "#46536b")
    tile.shape([(1, 6), (5, 2), (7, 2), (10, 3), (14, 7), (14, 9),
                (11, 12), (9, 13), (6, 12), (1, 8)], "#a9b9cc")
    tile.line([(1, 6), (5, 2), (7, 2), (10, 3), (14, 7)], "#eff5fd")
    tile.line([(1, 7), (3, 9), (6, 11), (9, 12)], "#d7e5ef")
    tile.line([(10, 12), (14, 8)], "#879ab4")
    tile.shape([(3, 6), (6, 3), (8, 3), (12, 6), (13, 8), (10, 11), (8, 12), (5, 10), (3, 8)], "#293b53")
    tile.shape([(4, 6), (6, 4), (8, 4), (11, 6), (12, 8), (9, 11), (7, 10), (4, 8)], low)
    tile.line([(4, 6), (6, 4), (8, 4), (11, 6)], mid)
    if tier == 0:
        tile.line([(5, 7), (7, 6), (8, 6), (10, 9)], high)
        tile.pixel(6, 8, mid)
        tile.pixel(9, 9, mid)
    elif tier == 1:
        tile.line([(5, 7), (7, 5), (10, 7)], high)
        tile.line([(7, 9), (8, 8), (10, 9), (11, 8)], mid)
        tile.pixel(7, 9, high)
    elif tier == 2:
        tile.line([(5, 7), (6, 6)], high)
        tile.line([(7, 5), (8, 6)], high)
        tile.line([(9, 7), (10, 6)], high)
        tile.line([(7, 9), (8, 8)], mid)
        tile.pixel(11, 7, mid)
    else:
        tile.line([(5, 7), (7, 5), (9, 6), (11, 8), (9, 10), (7, 9), (6, 8)], mid)
        tile.line([(5, 7), (7, 5), (9, 6)], high)
        tile.pixel(8, 8, high)
        tile.pixel(9, 10, high)
        tile.pixel(2, 7, "#eff5fd")
        tile.pixel(12, 6, "#eff5fd")
    # Contacts sit on the near rail, below the display.
    tile.pixel(10, 13, "#bac7d8")
    tile.pixel(12, 11, "#bac7d8")
    tile.pixel(13, 10, mid)
    tile.pixel(14, 9, high)
    tile.save("item/" + name)


def card(name, feature, colors, tier=0):
    tile = Tile()
    tile.shape([(4, 1), (12, 1), (14, 3), (14, 6), (13, 7), (13, 13),
                (11, 15), (3, 15), (1, 13), (1, 4)], DARK)
    tile.shape([(4, 2), (12, 2), (13, 3), (13, 6), (12, 7), (12, 12),
                (11, 13), (3, 13), (2, 12), (2, 4)], "#2c5055")
    tile.line([(2, 11), (2, 4), (4, 2), (11, 2)], "#638b85")
    tile.line([(3, 4), (5, 4), (5, 10), (4, 10), (4, 12)], CIRCUIT)
    tile.line([(12, 4), (10, 4), (10, 9), (11, 9), (11, 12)], CIRCUIT)
    for x in (4, 6, 8, 10):
        tile.box((x, 13, x, 14), CONTACT)
    bevel(tile, (5, 5, 10, 10), WELL, SHADE, DARK)
    low, medium, high = colors
    if feature == "tags":
        tile.shape([(5, 4), (8, 4), (11, 7), (8, 10), (5, 7)], low)
        tile.line([(5, 6), (5, 4), (8, 4), (11, 7)], medium)
        tile.pixel(6, 5, high)
        tile.line([(8, 11), (12, 7)], high)
    elif feature == "compress":
        for x, y in [(6, 5), (10, 5), (8, 9)]:
            node(tile, x, y, colors)
        tile.pixel(8, 6, high)
        tile.line([(6, 7), (8, 8), (10, 7)], high)
    elif feature == "limit":
        tile.box((5, 5, 11, 10), low)
        tile.line([(5, 9), (5, 7)], medium)
        tile.line([(7, 9), (7, 6)], medium)
        tile.line([(9, 9), (9, 4)], high)
        tile.line([(11, 5), (11, 10)], high)
    elif feature == "energy":
        tile.line([(9, 4), (6, 8), (9, 8), (6, 11)], high)
        tile.line([(9, 5), (7, 8), (10, 8), (8, 11)], medium)
        tile.pixel(11, 5, high)
    else:
        tile.line([(8, 4), (8, 7), (5, 7), (5, 10)], medium)
        tile.line([(8, 7), (11, 7), (11, 10)], medium)
        for x in (5, 11):
            tile.box((x - 1, 10, x + 1, 11), low)
            tile.pixel(x, 10, high)
        for level in range(tier + 1):
            tile.pixel(4 + 2 * level, 3, high)
        if tier >= 1:
            tile.box((7, 6, 9, 8), low)
            tile.pixel(8, 7, high)
        if tier >= 2:
            tile.line([(3, 6), (4, 6), (4, 9)], medium)
            tile.line([(12, 4), (11, 4), (11, 6)], medium)
        if tier == 3:
            tile.line([(3, 11), (4, 12), (11, 12), (13, 10), (13, 7)], high)
    tile.save("item/" + name)


def chassis():
    tile = Tile()
    tile.box((0, 0, 15, 15), EDGE)
    bevel(tile, (1, 1, 14, 14), METAL, LIGHT, SHADE)
    tile.line([(2, 3), (2, 2), (13, 2)], MID)
    tile.line([(2, 13), (13, 13)], SHADE)
    bevel(tile, (3, 3, 12, 12), WELL, SHADE, MID)
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        tile.pixel(x, y, DARK)
    tile.pixel(0, 0, SHADE)
    tile.pixel(15, 15, DARK)
    return tile


def backplane():
    tile = chassis()
    bevel(tile, (4, 4, 11, 11), SHADE, METAL, DARK)
    tile.box((6, 5, 9, 10), EDGE)
    for y in (6, 8, 10):
        tile.line([(6, y), (9, y)], METAL)
    return tile


def interface(name, advanced, active):
    tile = Tile()
    tile.box((0, 0, 15, 15), "#414b60")
    bevel(tile, (1, 1, 14, 14), "#c1c9d5", "#f6f7fa", "#a0acbd")
    tile.box((3, 3, 12, 12), "#303d54" if not advanced else "#3a344f")
    if advanced:
        inset, accent, light, dark = (
            ("#493e7b", "#866bc3", "#cbb7f6", "#312849") if active
            else ("#51496a", "#766b92", "#ada1c3", "#39354d")
        )
        bevel(tile, (4, 4, 11, 11), inset, accent, dark)
        tile.line([(5, 6), (5, 5), (10, 5), (10, 6)], light)
        tile.line([(5, 9), (5, 10), (10, 10), (10, 9)], accent)
        tile.box((5, 7, 7, 8), dark)
        tile.box((8, 7, 10, 8), dark)
        tile.line([(6, 7), (7, 7)], light)
        tile.line([(8, 8), (9, 8)], light)
        tile.pixel(2, 2, "#f6f7fa")
        tile.pixel(13, 2, "#f6f7fa")
    else:
        inset, accent, light, dark = (
            ("#3e668f", "#6b9abf", "#c6e5fa", "#2d4d72") if active
            else ("#4f6480", "#6b819f", "#adc0d6", "#3f516b")
        )
        bevel(tile, (4, 4, 11, 11), inset, light, dark)
        bevel(tile, (5, 5, 10, 10), dark, dark, accent)
        tile.box((6, 6, 9, 9), inset)
        tile.line([(6, 7), (9, 7)], light)
        tile.line([(7, 8), (8, 8)], accent)
    tile.pixel(7, 13, light if active else "#8290a4")
    if advanced:
        tile.pixel(9, 13, light if active else "#8290a4")
    tile.save("block/" + name)


def migrator(name, tier, colors):
    face = chassis()
    low, medium, high = colors
    for y in range(5, 11, 5 - tier):
        face.line([(1, y), (5, y), (6, y - 1), (9, y - 1), (10, y), (14, y)], medium)
        face.pixel(3, y, high)
        face.pixel(12, y, high)
    plate = chassis()
    bevel(plate, (3, 4, 12, 11), DARK, METAL, DARK)
    for y in (5, 9):
        plate.box((4, y, 11, y + 1), low)
        plate.line([(5, y), (10, y)], medium)
        points = [(9, y - 1), (11, y), (9, y + 1)] if y == 5 else [(6, y - 1), (4, y), (6, y + 1)]
        plate.shape(points, high)
    if tier == 1:
        for x in range(4, 13, 2):
            face.line([(x, 3), (x, 4)], high)
        node(face, 7, 8, colors)
        node(face, 9, 8, colors)
        plate.line([(4, 7), (11, 7)], medium)
    if tier == 2:
        ring(face, 8, 7, colors)
        ring(plate, 8, 7, colors)
        plate.pixel(3, 12, high)
        plate.pixel(12, 12, high)
    face.save("block/" + name + "_side")
    plate.save("block/" + name + "_top")


def devices():
    drive = chassis()
    bevel(drive, (2, 2, 13, 13), DARK, SHADE, METAL)
    for x in (3, 6, 9, 12):
        for y in (3, 5, 7, 9, 11):
            drive.box((x - 1, y, x, y), SHADE if y % 4 == 1 else METAL)
            drive.pixel(x + 1, y, TEAL[1] if x in (3, 6) else "#466372")
    drive.save("block/bulk_storage_drive")

    table = chassis()
    bevel(table, (1, 1, 14, 14), SHADE, LIGHT, EDGE)
    bevel(table, (2, 3, 9, 12), WELL, EDGE, METAL)
    tile_color = BLUE[1]
    for (x, y), (dx, dy) in [((3, 4), (1, 1)), ((8, 4), (-1, 1)), ((3, 11), (1, -1)), ((8, 11), (-1, -1))]:
        table.line([(x + dx, y), (x, y), (x, y + dy)], tile_color)
    table.box((5, 6, 6, 9), METAL)
    table.pixel(6, 7, BLUE[2])
    bevel(table, (10, 3, 13, 12), DARK, MID, DARK)
    for y in (5, 7, 9, 11):
        table.line([(11, y), (12, y)], TEAL[1])
    table.save("block/advanced_cell_workbench")

    manager = chassis()
    bevel(manager, (2, 3, 13, 12), DARK, SHADE, MID)
    for y in (5, 8, 11):
        for x in (4, 7, 10):
            node(manager, x, y, BLUE if (x + y) % 2 == 0 else TEAL)
    manager.pixel(13, 2, BLUE[2])
    manager.save("block/bulk_storage_manager")

    for powered in (False, True):
        monitor = chassis()
        bevel(monitor, (2, 3, 13, 11), "#20444a", WELL, METAL)
        for x in range(4, 12):
            monitor.line([(x, 9), (x, 10)], "#34565a")
        monitor.line([(3, 9), (5, 9), (5, 7), (7, 7), (7, 8), (8, 8),
                      (8, 5), (10, 5), (10, 6), (12, 6)], TEAL[2] if powered else TEAL[1])
        monitor.line([(4, 5), (6, 5)], "#87c1b5")
        monitor.box((10, 12, 12, 12), "#ee8264" if powered else "#674f55")
        monitor.save("block/bulk_storage_monitor" + ("_signal" if powered else ""))


def json_file(path, data):
    (ASSETS / (path + ".json")).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def item_model(name, machine=False):
    model = {"parent": f"{ID}:block/{name}"} if machine else {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"{ID}:item/{name}"}
    }
    json_file("models/item/" + name, model)


def interface_model(name):
    # Each face has a full-size frame with a half-pixel inset and four narrow modelled reveals.
    # The (coordinate axis, sign) triples follow Minecraft's face UV orientation.
    orientations = {
        "north": ((2, 1), (0, -1), (1, -1)),
        "south": ((2, -1), (0, 1), (1, -1)),
        "west": ((0, 1), (2, 1), (1, -1)),
        "east": ((0, -1), (2, -1), (1, -1)),
        "up": ((1, -1), (0, 1), (2, 1)),
        "down": ((1, 1), (0, 1), (2, -1)),
    }
    negative = ("west", "down", "north")
    positive = ("east", "up", "south")
    elements = []

    def add_quad(axes, spans, uv, direction, outer=False):
        start, end = [0, 0, 0], [0, 0, 0]
        for (axis, sign), (lo, hi) in zip(axes, spans):
            start[axis], end[axis] = (lo, hi) if sign == 1 else (16 - hi, 16 - lo)
        face = {"uv": uv, "texture": "#panel"}
        if outer:
            face["cullface"] = direction
        elements.append({"from": start, "to": end, "faces": {direction: face}})

    for face, axes in orientations.items():
        for u, v, right, bottom in ((0, 0, 16, 3), (0, 13, 16, 16), (0, 3, 3, 13), (13, 3, 16, 13)):
            add_quad(axes, [(0, 0), (u, right), (v, bottom)], [u, v, right, bottom], face, outer=True)
        add_quad(axes, [(0.5, 0.5), (3, 13), (3, 13)], [3, 3, 13, 13], face)
        for inside_axis in (1, 2):
            axis, sign = axes[inside_axis]
            for edge, inward in ((3, 1), (13, -1)):
                spans = [(0, 0.5), (3, 13), (3, 13)]
                spans[inside_axis] = (edge, edge)
                direction = (positive if sign == inward else negative)[axis]
                add_quad(axes, spans, [3, 3, 4, 4], direction)
    json_file("models/block/" + name, {
        "parent": "minecraft:block/block",
        "textures": {"panel": f"{ID}:block/{name}", "particle": "#panel"},
        "elements": elements,
    })


def machine_model(name, side, top, bottom="backplane", front=None, frame=None):
    texture = lambda part: f"{ID}:block/{part}"
    if front is None:
        model = {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "side": texture(side), "top": texture(top), "bottom": texture(bottom)
        }}
    else:
        model = {"parent": "minecraft:block/orientable", "textures": {
            "side": texture(side), "top": texture(top), "front": texture(front)
        }}
    if frame is not None:
        # Horizontal fin on the workbench. Keep the model inside the block's AABB.
        model["parent"] = "minecraft:block/block"
        model["textures"]["particle"] = texture(side)
        model["elements"] = [
            {"from": [0, 0, 0], "to": [16, 15, 16], "faces": {
                direction: {"texture": tex, **({"cullface": direction} if direction != "up" else {})}
                for direction, tex in {"down": "#bottom", "up": "#top", "north": "#side", "south": "#side",
                                       "east": "#side", "west": "#side"}.items()
            }},
            {"from": [1, 15, 1], "to": [15, 16, 15], "faces": {
                direction: {"texture": tex, "uv": [1, 1, 15, 15] if direction == "up" else [1, 1, 15, 2]}
                for direction, tex in {"up": "#top", "north": "#side", "south": "#side",
                                       "east": "#side", "west": "#side"}.items()
            }}
        ]
    json_file("models/block/" + name, model)


def states(name, property_name=None, active=None):
    model = f"{ID}:block/{name}"
    variants = {"": {"model": model}} if property_name is None else {
        property_name + "=false": {"model": model},
        property_name + "=true": {"model": f"{ID}:block/{active}"}
    }
    json_file("blockstates/" + name, {"variants": variants})
    item_model(name, True)


def main():
    for level, (name, colors) in enumerate([
        ("tag_bulk_storage_cell", TEAL), ("advanced_tag_bulk_storage_cell", BLUE),
        ("mod_bulk_storage_cell", AMBER), ("quantum_bulk_storage_cell", VIOLET)
    ]):
        cell(name, level, colors)
        item_model(name)
    for name, purpose, colors in [
        ("extended_tag_card", "tags", TEAL), ("multi_compression_card", "compress", AMBER),
        ("storage_limit_card", "limit", BLUE), ("energy_saving_card", "energy", TEAL)
    ]:
        card(name, purpose, colors)
        item_model(name)
    for level, (name, colors) in enumerate([
        ("auto_sorting_card", TEAL), ("advanced_auto_sorting_card", BLUE),
        ("overclocked_auto_sorting_card", AMBER), ("quantum_auto_sorting_card", VIOLET)
    ]):
        card(name, "sorting", colors, level)
        item_model(name)

    backplane().save("block/backplane")
    for advanced, name in [
        (False, "bulk_storage_interface"), (True, "advanced_bulk_storage_interface")
    ]:
        for online in (False, True):
            version = name + ("_online" if online else "")
            interface(version, advanced, online)
            interface_model(version)
        states(name, "lit", name + "_online")
        json_file("models/item/" + name, {"parent": f"{ID}:block/{name}_online"})
    for level, (name, colors) in enumerate([
        ("bulk_storage_migrator", TEAL), ("overclocked_bulk_storage_migrator", AMBER),
        ("quantum_bulk_storage_migrator", VIOLET)
    ]):
        migrator(name, level, colors)
        machine_model(name, name + "_side", name + "_top")
        states(name)
    devices()
    machine_model("bulk_storage_drive", "backplane", "backplane", front="bulk_storage_drive")
    machine_model("advanced_cell_workbench", "backplane", "advanced_cell_workbench", frame=True)
    machine_model("bulk_storage_manager", "backplane", "backplane", front="bulk_storage_manager")
    machine_model("bulk_storage_monitor", "backplane", "backplane", front="bulk_storage_monitor")
    machine_model("bulk_storage_monitor_signal", "backplane", "backplane", front="bulk_storage_monitor_signal")
    for name in ("bulk_storage_drive", "advanced_cell_workbench", "bulk_storage_manager"):
        states(name)
    states("bulk_storage_monitor", "powered", "bulk_storage_monitor_signal")


if __name__ == "__main__":
    main()
