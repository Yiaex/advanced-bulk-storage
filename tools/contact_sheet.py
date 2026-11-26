"""Render zoomed contact sheets of the mod textures for visual review."""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/advanced_bulk_storage"


def load_font(size):
    for candidate in (
        r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\consola.ttf",
        r"C:\Windows\Fonts\arial.ttf",
    ):
        if Path(candidate).exists():
            try:
                return ImageFont.truetype(candidate, size)
            except OSError:
                continue
    return ImageFont.load_default()


def sheet(paths, out, cell_px=96, cols=6, bg=(26, 30, 38)):
    pad, label_h = 14, 20
    rows = (len(paths) + cols - 1) // cols
    w = cols * (cell_px + pad) + pad
    h = rows * (cell_px + pad + label_h) + pad + 26
    canvas = Image.new("RGBA", (w, h), bg + (255,))
    draw = ImageDraw.Draw(canvas)
    title_font = load_font(15)
    label_font = load_font(11)
    draw.text((pad, 6), f"{len(paths)} textures @ 16x16, shown {cell_px // 16}x", font=title_font, fill=(220, 230, 240))
    for index, path in enumerate(paths):
        img = Image.open(path).convert("RGBA")
        if img.size != (16, 16):
            img = img.resize((16, 16), Image.NEAREST)
        big = img.resize((cell_px, cell_px), Image.NEAREST)
        cx = pad + (index % cols) * (cell_px + pad)
        cy = pad + 26 + (index // cols) * (cell_px + pad + label_h)
        # checker so transparency is obvious
        for yy in range(0, cell_px, 8):
            for xx in range(0, cell_px, 8):
                if (xx // 8 + yy // 8) % 2 == 0:
                    draw.rectangle([cx + xx, cy + yy, cx + xx + 7, cy + yy + 7], fill=(38, 42, 52))
        canvas.alpha_composite(big, (cx, cy))
        draw.rectangle([cx - 1, cy - 1, cx + cell_px, cy + cell_px], outline=(70, 78, 92))
        draw.text((cx, cy + cell_px + 3), path.stem, font=label_font, fill=(180, 192, 205))
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(out)
    print(out)


def main():
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else ASSETS
    out_dir = ROOT / "art-review" / "sheets"
    for kind in ("item", "block"):
        files = sorted((target / "textures" / kind).glob("*.png"))
        if files:
            sheet(files, out_dir / f"existing-{kind}.png", cell_px=96, cols=4)


if __name__ == "__main__":
    main()
