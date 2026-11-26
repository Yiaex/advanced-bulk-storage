"""Build the review sheet for the v2 texture pass.

    python tools/overview_sheet.py

Writes art-review/sheets/overview.png: the full proposed set at 6x with the
mod's own zh_cn names, so the drawings can be judged as one family.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "art-review" / "v2" / "textures"
OUT = ROOT / "art-review" / "sheets" / "overview.png"

FONT_CANDIDATES = [
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/msyhbd.ttc",
    "C:/Windows/Fonts/simhei.ttf",
]

ITEMS = [
    ("tag_bulk_storage_cell", "ME 标签大宗存储元件"),
    ("advanced_tag_bulk_storage_cell", "ME 高级标签大宗存储元件"),
    ("mod_bulk_storage_cell", "ME 模组大宗存储元件"),
    ("quantum_bulk_storage_cell", "ME 量子大宗存储元件"),
    ("extended_tag_card", "扩展标签卡"),
    ("multi_compression_card", "多类型压缩卡"),
    ("storage_limit_card", "限额卡"),
    ("energy_saving_card", "节能卡"),
    ("auto_sorting_card", "自动归类卡"),
    ("advanced_auto_sorting_card", "高级自动归类卡"),
    ("overclocked_auto_sorting_card", "超频自动归类卡"),
    ("quantum_auto_sorting_card", "量子自动归类卡"),
]

BLOCKS = [
    ("backplane", "背板"),
    ("bulk_storage_interface", "存储接口"),
    ("bulk_storage_interface_online", "存储接口·在线"),
    ("advanced_bulk_storage_interface", "高级存储接口"),
    ("advanced_bulk_storage_interface_online", "高级存储接口·在线"),
    ("bulk_storage_migrator_side", "迁移器 侧面"),
    ("bulk_storage_migrator_top", "迁移器 顶面"),
    ("overclocked_bulk_storage_migrator_side", "超频迁移器 侧面"),
    ("overclocked_bulk_storage_migrator_top", "超频迁移器 顶面"),
    ("quantum_bulk_storage_migrator_side", "量子迁移器 侧面"),
    ("quantum_bulk_storage_migrator_top", "量子迁移器 顶面"),
    ("bulk_storage_drive", "驱动器"),
    ("advanced_cell_workbench", "高级元件工作台"),
    ("bulk_storage_manager", "存储管理器"),
    ("bulk_storage_monitor", "存储监视器"),
    ("bulk_storage_monitor_signal", "监视器·信号"),
]

BG = (22, 25, 31)
PANEL = (30, 34, 41)
INK = (228, 235, 240)
MUTED = (138, 152, 166)
ACCENT = (127, 216, 200)


def font(size):
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def section(img, draw, y, title, rows, columns, zoom, cell_w):
    f_title = font(22)
    f_name = font(13)
    draw.text((28, y), title, font=f_title, fill=ACCENT)
    y += 40
    row_h = 16 * zoom + 34
    for index, (name, label) in enumerate(rows):
        col = index % columns
        row = index // columns
        x = 28 + col * cell_w
        cy = y + row * row_h
        draw.rounded_rectangle((x - 8, cy - 8, x + 16 * zoom + 8, cy + 16 * zoom + 30),
                               6, fill=PANEL)
        tex = Image.open(TEX / ("item" if title.startswith("物品") else "block") /
                         f"{name}.png").convert("RGBA")
        tex = tex.resize((16 * zoom, 16 * zoom), Image.NEAREST)
        img.paste(tex, (x, cy), tex)
        draw.text((x, cy + 16 * zoom + 8), label, font=f_name, fill=INK)
    rows_used = (len(rows) + columns - 1) // columns
    return y + rows_used * row_h + 26


def main():
    columns = 4
    cell_w = 200
    width = 28 + columns * cell_w + 8
    probe = Image.new("RGB", (width, 10), BG)
    pdraw = ImageDraw.Draw(probe)
    y = 24
    pdraw.text((28, y), "advanced_bulk_storage", font=font(26), fill=INK)
    y += 34
    pdraw.text((28, y), "贴图提案 v2 · 16×16 · 与 ARTDIRECTION 同规格",
               font=font(15), fill=MUTED)
    y += 42
    y_items = section(probe, pdraw, y, "物品 · 12", ITEMS, columns, 6, cell_w)
    y_blocks = section(probe, pdraw, y_items, "方块 · 16", BLOCKS, columns, 5, cell_w)
    height = y_blocks + 16

    img = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(img)
    y = 24
    draw.text((28, y), "advanced_bulk_storage", font=font(26), fill=INK)
    y += 34
    draw.text((28, y), "贴图提案 v2 · 16×16 · 与 ART-DIRECTION.md 同规格",
              font=font(15), fill=MUTED)
    y += 42
    y_items = section(img, draw, y, "物品 · 12", ITEMS, columns, 6, cell_w)
    section(img, draw, y_items, "方块 · 16", BLOCKS, columns, 5, cell_w)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT, img.size)


if __name__ == "__main__":
    main()
