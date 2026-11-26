"""Side-by-side review sheets: current shipped art vs the v2 pass."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
CURRENT = ROOT / "src/main/resources/assets/advanced_bulk_storage/textures"
V2 = ROOT / "art-review/v2/textures"
SHEETS = ROOT / "art-review/sheets"


def font(size, bold=False):
    for name in ("msyhbd.ttc" if bold else "msyh.ttc", "segoeuib.ttf", "arialbd.ttf", "arial.ttf"):
        path = Path(r"C:\Windows\Fonts") / name
        if path.exists():
            try:
                return ImageFont.truetype(str(path), size)
            except OSError:
                continue
    return ImageFont.load_default()


BG = (24, 27, 33)
PANEL = (34, 38, 46)
GRID = (44, 49, 58)


def checker(draw, x, y, size, step=8):
    for yy in range(0, size, step):
        for xx in range(0, size, step):
            if (xx // step + yy // step) % 2 == 0:
                draw.rectangle([x + xx, y + yy, x + xx + step - 1, y + yy + step - 1], fill=(39, 43, 52))


def load(path):
    if not path.exists():
        return None
    img = Image.open(path).convert("RGBA")
    return img.resize((16, 16), Image.NEAREST) if img.size != (16, 16) else img


def compare(kind, names, zoom=7):
    cell = 16 * zoom
    pad, label_w, head = 12, 210, 34
    width = label_w + cell * 2 + pad * 3
    height = head + len(names) * (cell + pad) + pad
    img = Image.new("RGB", (width, height), BG)
    d = ImageDraw.Draw(img)
    f_title, f_label, f_head = font(15, True), font(12), font(11)

    d.text((pad, 8), f"{kind} — current (left) vs v2 (right), {zoom}x nearest", font=f_title, fill=(226, 234, 242))
    d.text((label_w + pad, head - 18), "current", font=f_head, fill=(150, 160, 172))
    d.text((label_w + cell + pad * 2, head - 18), "v2", font=f_head, fill=(150, 210, 190))

    for row, name in enumerate(names):
        y = head + row * (cell + pad)
        d.text((pad, y + cell // 2 - 8), name, font=f_label, fill=(198, 208, 220))
        for col, source in enumerate((CURRENT, V2)):
            x = label_w + pad + col * (cell + pad)
            d.rectangle([x - 2, y - 2, x + cell + 1, y + cell + 1], fill=PANEL)
            checker(d, x, y, cell)
            tile = load(source / kind / f"{name}.png")
            if tile is None:
                d.text((x + 8, y + cell // 2 - 6), "missing", font=f_head, fill=(180, 90, 90))
                continue
            img.paste(tile.resize((cell, cell), Image.NEAREST), (x, y), tile.resize((cell, cell), Image.NEAREST))
            d.rectangle([x, y, x + cell - 1, y + cell - 1], outline=GRID)
    out = SHEETS / f"compare-{kind}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(out)


def scale_strip(kind, names):
    """v2 at true in-inventory sizes 1x/2x/3x, on a dark inventory-like panel."""
    steps = (1, 2, 3)
    row_h, label_w, pad = 52, 200, 10
    width = label_w + sum(16 * s + 16 for s in steps) + pad
    height = 30 + len(names) * row_h + pad
    img = Image.new("RGB", (width, height), (30, 33, 39))
    d = ImageDraw.Draw(img)
    d.text((pad, 6), f"{kind} — v2 at 1x / 2x / 3x", font=font(13, True), fill=(220, 230, 238))
    for row, name in enumerate(names):
        y = 30 + row * row_h
        d.text((pad, y + 18), name, font=font(10), fill=(180, 190, 202))
        x = label_w
        tile = load(V2 / kind / f"{name}.png")
        for step in steps:
            size = 16 * step
            d.rectangle([x - 4, y + 2, x + size + 3, y + size + 5], fill=(40, 44, 52))
            if tile is not None:
                big = tile.resize((size, size), Image.NEAREST)
                img.paste(big, (x, y + 4), big)
            x += size + 18
    out = SHEETS / f"scale-{kind}.png"
    img.save(out)
    print(out)


ITEMS = [
    "tag_bulk_storage_cell", "advanced_tag_bulk_storage_cell",
    "mod_bulk_storage_cell", "quantum_bulk_storage_cell",
    "extended_tag_card", "multi_compression_card", "storage_limit_card",
    "energy_saving_card", "auto_sorting_card", "advanced_auto_sorting_card",
    "overclocked_auto_sorting_card", "quantum_auto_sorting_card",
]
BLOCKS = [
    "backplane", "bulk_storage_interface", "bulk_storage_interface_online",
    "advanced_bulk_storage_interface", "advanced_bulk_storage_interface_online",
    "bulk_storage_migrator_side", "bulk_storage_migrator_top",
    "overclocked_bulk_storage_migrator_side", "overclocked_bulk_storage_migrator_top",
    "quantum_bulk_storage_migrator_side", "quantum_bulk_storage_migrator_top",
    "bulk_storage_drive", "advanced_cell_workbench", "bulk_storage_manager",
    "bulk_storage_monitor", "bulk_storage_monitor_signal",
]


if __name__ == "__main__":
    compare("item", ITEMS, zoom=7)
    compare("block", BLOCKS, zoom=7)
    scale_strip("item", ITEMS)
