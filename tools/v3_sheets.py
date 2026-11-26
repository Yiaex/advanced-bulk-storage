"""Review sheets for the v3 (AE2-aligned) texture pass.

    python tools/v3_sheets.py

Writes three sheets into art-review/sheets/:
    compare-v3-item.png   current | AE2 原版参考 | v3
    compare-v3-block.png  current | AE2 原版参考 | v3
    overview-v3.png       whole v3 set at 6x with the mod's zh_cn names
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
CURRENT = ROOT / "src/main/resources/assets/advanced_bulk_storage/textures"
V3 = ROOT / "art-review/v3/textures"
AE2 = ROOT / "art-review/ref/ae2/textures"
SHEETS = ROOT / "art-review/sheets"

BG = (24, 27, 33)
PANEL = (33, 37, 45)
GRID = (46, 51, 61)
INK = (228, 235, 240)
MUTED = (140, 154, 168)
ACCENT = (127, 216, 200)
COL_V3 = (150, 210, 190)


def font(size, bold=False, cjk=True):
    names = ["msyhbd.ttc", "msyh.ttc"] if bold else ["msyh.ttc", "msyhbd.ttc"]
    if not cjk:
        names = ["consola.ttf", "segoeui.ttf"] + names
    for name in names:
        path = Path(r"C:\Windows\Fonts") / name
        if path.exists():
            try:
                return ImageFont.truetype(str(path), size)
            except OSError:
                continue
    return ImageFont.load_default()


def checker(draw, x, y, size, step=10):
    for yy in range(0, size, step):
        for xx in range(0, size, step):
            if (xx // step + yy // step) % 2 == 0:
                draw.rectangle([x + xx, y + yy, x + xx + step - 1, y + yy + step - 1],
                               fill=(38, 42, 51))


def tile(path, cell):
    if path is None or not path.exists():
        return None
    img = Image.open(path).convert("RGBA")
    return img.resize((cell, cell), Image.NEAREST)


ITEMS = [
    ("tag_bulk_storage_cell", "item_storage_cell_1k", "ME 标签大宗存储元件"),
    ("advanced_tag_bulk_storage_cell", "item_storage_cell_4k", "ME 高级标签大宗存储元件"),
    ("mod_bulk_storage_cell", "item_storage_cell_64k", "ME 模组大宗存储元件"),
    ("quantum_bulk_storage_cell", "item_storage_cell_256k", "ME 量子大宗存储元件"),
    ("extended_tag_card", "basic_card", "扩展标签卡"),
    ("multi_compression_card", "advanced_card", "多类型压缩卡"),
    ("storage_limit_card", "advanced_card", "限额卡"),
    ("energy_saving_card", "advanced_card", "节能卡"),
    ("auto_sorting_card", "advanced_card", "自动归类卡"),
    ("advanced_auto_sorting_card", "advanced_card", "高级自动归类卡"),
    ("overclocked_auto_sorting_card", "advanced_card", "超频自动归类卡"),
    ("quantum_auto_sorting_card", "advanced_card", "量子自动归类卡"),
]

BLOCKS = [
    ("backplane", "controller", "背板"),
    ("bulk_storage_interface", "io_port_side", "存储接口"),
    ("bulk_storage_interface_online", "io_port_front", "存储接口·在线"),
    ("advanced_bulk_storage_interface", "interface", "高级存储接口"),
    ("advanced_bulk_storage_interface_online", "interface", "高级存储接口·在线"),
    ("bulk_storage_migrator_side", "io_port_side", "迁移器 侧面"),
    ("bulk_storage_migrator_top", "io_port_top", "迁移器 顶面"),
    ("overclocked_bulk_storage_migrator_side", "energy_cell_0", "超频迁移器 侧面"),
    ("overclocked_bulk_storage_migrator_top", "energy_cell_0", "超频迁移器 顶面"),
    ("quantum_bulk_storage_migrator_side", "quantum_ring", "量子迁移器 侧面"),
    ("quantum_bulk_storage_migrator_top", "quantum_ring_light", "量子迁移器 顶面"),
    ("bulk_storage_drive", "io_port_front", "驱动器"),
    ("advanced_cell_workbench", "cell_workbench_side", "高级元件工作台"),
    ("bulk_storage_manager", "io_port_top", "存储管理器"),
    ("bulk_storage_monitor", "vibration_chamber_front", "存储监视器"),
    ("bulk_storage_monitor_signal", "vibration_chamber_front_on", "监视器·信号"),
]


def compare(kind, rows, out_name, zoom=6):
    cell = 16 * zoom
    pad, label_w, head = 12, 210, 54
    width = label_w + cell * 3 + pad * 4
    height = head + len(rows) * (cell + pad) + pad
    img = Image.new("RGB", (width, height), BG)
    d = ImageDraw.Draw(img)
    f_title, f_label, f_head = font(15, True), font(12), font(11)

    d.text((pad, 10), f"{kind} — 现状（左） | AE2 原版参考（中） | 新版 v3（右），{zoom}×", 
           font=f_title, fill=INK)
    for col, (text, colour) in enumerate((
            ("现状", MUTED), ("AE2 原版参考", (200, 178, 120)), ("v3", COL_V3))):
        d.text((label_w + pad + col * (cell + pad), head - 20), text, font=f_head, fill=colour)

    for row, (name, ref, label) in enumerate(rows):
        y = head + row * (cell + pad)
        d.text((pad, y + 6), label, font=f_label, fill=INK)
        d.text((pad, y + 24), name, font=font(9), fill=MUTED)
        sources = [
            CURRENT / kind / f"{name}.png",
            AE2 / kind / f"{ref}.png" if ref else None,
            V3 / kind / f"{name}.png",
        ]
        for col, path in enumerate(sources):
            x = label_w + pad + col * (cell + pad)
            d.rectangle([x - 2, y - 2, x + cell + 1, y + cell + 1], fill=PANEL)
            checker(d, x, y, cell)
            pic = tile(path, cell)
            if pic is None:
                d.text((x + 10, y + cell // 2 - 6), "—", font=f_head, fill=(120, 128, 138))
            else:
                img.paste(pic, (x, y), pic)
            d.rectangle([x, y, x + cell - 1, y + cell - 1], outline=GRID)
    out = SHEETS / out_name
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(out, img.size)


def overview(zoom_item=6, zoom_block=5, columns=4, cell_w=200):
    def section(img, draw, y, title, rows, zoom, kind):
        f_title, f_name, f_sub = font(22), font(13), font(9)
        draw.text((28, y), title, font=f_title, fill=ACCENT)
        y += 40
        row_h = 16 * zoom + 36
        for index, (name, _ref, label) in enumerate(rows):
            col, row = index % columns, index // columns
            x, cy = 28 + col * cell_w, y + row * row_h
            draw.rounded_rectangle((x - 8, cy - 8, x + 16 * zoom + 8, cy + 16 * zoom + 32),
                                   6, fill=PANEL)
            pic = tile(V3 / kind / f"{name}.png", 16 * zoom)
            if pic is not None:
                img.paste(pic, (x, cy), pic)
            draw.text((x, cy + 16 * zoom + 8), label, font=f_name, fill=INK)
        rows_used = (len(rows) + columns - 1) // columns
        return y + rows_used * row_h + 22

    width = 28 + columns * cell_w + 8
    probe = Image.new("RGB", (width, 10), BG)
    pdraw = ImageDraw.Draw(probe)
    y = 24
    y += 34
    y += 42
    y_items = section(probe, pdraw, y, "物品 · 12", ITEMS, zoom_item, "item")
    y_blocks = section(probe, pdraw, y_items, "方块 · 16", BLOCKS, zoom_block, "block")

    img = Image.new("RGB", (width, y_blocks + 16), BG)
    draw = ImageDraw.Draw(img)
    draw.text((28, 24), "advanced_bulk_storage", font=font(26), fill=INK)
    draw.text((28, 58), "贴图提案 v3 · AE2 材质语言 · 16×16 · 与 ART-DIRECTION.md 同规格",
              font=font(15), fill=MUTED)
    y = 100
    y_items = section(img, draw, y, "物品 · 12", ITEMS, zoom_item, "item")
    section(img, draw, y_items, "方块 · 16", BLOCKS, zoom_block, "block")
    out = SHEETS / "overview-v3.png"
    img.save(out)
    print(out, img.size)


def details(kind, rows, zoom, out_name):
    """Plain grid of the v3 textures at one zoom, no labels."""
    pad, columns = 10, 4
    cell = 16 * zoom
    per_row = (len(rows) + columns - 1) // columns
    img = Image.new("RGB", (pad + columns * (cell + pad), pad + per_row * (cell + pad)), BG)
    for index, (name, _ref, _label) in enumerate(rows):
        pic = tile(V3 / kind / f"{name}.png", cell)
        if pic is None:
            continue
        x = pad + (index % columns) * (cell + pad)
        y = pad + (index // columns) * (cell + pad)
        img.paste(pic, (x, y), pic)
    out = SHEETS / out_name
    img.save(out)
    print(out, img.size)


def scales(kind, rows, out_name):
    """1x / 2x / 3x strip, to judge the texture at real inventory size."""
    pad, cell_w, zooms = 8, 64, (1, 2, 3)
    img = Image.new("RGB", (pad + len(rows) * (cell_w + pad), pad + len(zooms) * (cell_w + pad)),
                    (30, 33, 39))
    for row, zoom in enumerate(zooms):
        y = pad + row * (cell_w + pad)
        for index, (name, _ref, _label) in enumerate(rows):
            pic = tile(V3 / kind / f"{name}.png", 16 * zoom)
            if pic is None:
                continue
            x = pad + index * (cell_w + pad) + (cell_w - 16 * zoom) // 2
            img.paste(pic, (x, y + (cell_w - 16 * zoom) // 2), pic)
    out = SHEETS / out_name
    img.save(out)
    print(out, img.size)


if __name__ == "__main__":
    compare("item", ITEMS, "compare-v3-item.png")
    compare("block", BLOCKS, "compare-v3-block.png")
    overview()
    details("item", ITEMS, 14, "v3-item.png")
    details("block", BLOCKS, 10, "v3-block.png")
    details("item", ITEMS[:4], 14, "v3-cells.png")
    scales("item", ITEMS, "v3-scale-item.png")
    scales("block", BLOCKS, "v3-scale-block.png")
