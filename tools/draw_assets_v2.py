"""advanced_bulk_storage - texture pass v2 (review build).

Same contract as tools/draw_assets.py (16x16, ART-DIRECTION.md), but every
texture is redrawn with one rule in front of the others:

    one bold motif per tile, two tones per mark, no mid-tone mud.

Layout facts reused everywhere
    16x16 canvas, 1px hard edges, no anti-aliasing, no gradients.
    Blocks: bright silver ring (rows/cols 1-2), dark seam (3/12),
            8x8 content window (4..11), rivets on the four frame corners.
    Items : cells are horizontal cartridges (silver shell, tinted display,
            gold edge contacts); cards are vertical PCBs (colour key strip,
            near-black well, gold connector teeth).

Output goes to art-review/v2/textures/{item,block}. Nothing is written into
src/main/resources unless --install is passed, so the shipped art stays put
until the drawings are approved.

    python tools/draw_assets_v2.py            # render the review set
    python tools/draw_assets_v2.py --install  # copy PNGs into the mod assets
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "art-review" / "v2"
OUT = OUTPUT
SRC = ROOT / "src" / "main" / "resources" / "assets" / "advanced_bulk_storage" / "textures"


# --------------------------------------------------------------------------
# palette
# --------------------------------------------------------------------------
def hx(s: str):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def tone(c, f: float):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def mix(a, b, t: float):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


EDGE = hx("0b1016")
INK = hx("151b25")
WELL = hx("212b39")
SHADE = hx("3b4859")
METAL = hx("68788b")
METAL_L = hx("9bafc0")
SILVER = hx("c6d3de")
GLINT = hx("f5fafd")
STEEL = hx("8d9bae")
CIRCUIT = hx("3d7f84")
PCB = hx("1b3a3d")
CHIP = hx("0a1016")
GOLD = hx("c69d5c")
GOLD_HI = hx("f7e0aa")
ALARM = hx("c85f45")
ALARM_ON = hx("ff9a6e")
SCREEN = hx("0d2b31")


class Tier:
    def __init__(self, name, deep, base, light, glow):
        self.name = name
        self.deep = hx(deep)
        self.base = hx(base)
        self.light = hx(light)
        self.glow = hx(glow)

    def __repr__(self):
        return f"Tier({self.name})"


MINT = Tier("MINT", "12403c", "2f9c8f", "6fd6c2", "c8f6ec")
BLUE = Tier("BLUE", "16305e", "3b6fc0", "7fb0ec", "cfe4ff")
AMBER = Tier("AMBER", "4a2a10", "b4711f", "e6a94d", "ffe0a0")
VIOLET = Tier("VIOLET", "2c1a52", "7448c0", "a982e8", "e2d3ff")
DESAT = Tier("DESAT", "232a34", "46515f", "7b8a99", "b9c5d1")


# --------------------------------------------------------------------------
# canvas
# --------------------------------------------------------------------------
class Canvas:
    def __init__(self, size: int = 16):
        self.size = size
        self.px = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

    def set(self, x, y, c):
        if c is None:
            return
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[y][x] = (c[0], c[1], c[2], 255)

    def get(self, x, y):
        if 0 <= x < self.size and 0 <= y < self.size:
            return self.px[y][x]
        return (0, 0, 0, 0)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1 + 1):
            self.set(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1 + 1):
            self.set(x, y, c)

    def stroke(self, pts, c):
        for i in range(len(pts) - 1):
            (x0, y0), (x1, y1) = pts[i], pts[i + 1]
            dx, dy = x1 - x0, y1 - y0
            steps = max(abs(dx), abs(dy))
            if steps == 0:
                self.set(x0, y0, c)
                continue
            for s in range(steps + 1):
                self.set(round(x0 + dx * s / steps), round(y0 + dy * s / steps), c)

    def poly(self, pts, fill=None, outline=None):
        if fill:
            ys = [p[1] for p in pts]
            y0, y1 = min(ys), max(ys)
            for y in range(y0, y1 + 1):
                xs = []
                for i in range(len(pts)):
                    a = pts[i]
                    b = pts[(i + 1) % len(pts)]
                    if a[1] == b[1]:
                        continue
                    if min(a[1], b[1]) <= y + 0.5 < max(a[1], b[1]):
                        t = (y + 0.5 - a[1]) / (b[1] - a[1])
                        xs.append(a[0] + t * (b[0] - a[0]))
                xs.sort()
                for i in range(0, len(xs) - 1, 2):
                    for x in range(int(round(xs[i])), int(round(xs[i + 1])) + 1):
                        self.set(x, y, fill)
        if outline:
            self.stroke(list(pts) + [pts[0]], outline)

    def image(self) -> Image.Image:
        img = Image.new("RGBA", (self.size, self.size))
        img.putdata([self.px[y][x] for y in range(self.size) for x in range(self.size)])
        return img

    def save(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image().save(path)


# --------------------------------------------------------------------------
# shared primitives
# --------------------------------------------------------------------------
def raised(c: Canvas, x0, y0, x1, y1, fill, hi=GLINT, lo=None):
    lo = lo or tone(SHADE, 0.85)
    c.rect(x0, y0, x1, y1, fill)
    c.hline(x0, x1, y0, hi)
    c.vline(x0, y0, y1, hi)
    c.hline(x0, x1, y1, lo)
    c.vline(x1, y0, y1, lo)


def recessed(c: Canvas, x0, y0, x1, y1, fill, lo=EDGE, hi=None):
    hi = hi or tone(SHADE, 1.0)
    c.rect(x0, y0, x1, y1, fill)
    c.hline(x0, x1, y0, lo)
    c.vline(x0, y0, y1, lo)
    c.hline(x0, x1, y1, hi)
    c.vline(x1, y0, y1, hi)


# --------------------------------------------------------------------------
# items: bulk storage cells
# --------------------------------------------------------------------------
CELL = [(2, 3), (4, 1), (11, 1), (14, 4), (14, 12), (12, 14), (4, 14), (2, 12)]


def cell_display(c: Canvas, tier: Tier):
    """The tier glyph on the cartridge display: fold line / double line /
    segmentation / closed ring - the four identities from ART-DIRECTION.md.
    Marks are dark on the lit panel, the way an LCD legend reads; a couple of
    white pixels keep them from looking flat at 1x."""
    g = tone(tier.deep, 0.55)
    if tier is MINT:  # single fold line
        c.stroke([(4, 7), (7, 6), (9, 6), (11, 5)], g)
        c.set(4, 7, GLINT)
        c.set(5, 7, GLINT)
        c.set(11, 5, GLINT)
        c.set(7, 6, tone(g, 1.0))
    elif tier is BLUE:  # double line
        c.stroke([(4, 7), (11, 6)], g)
        c.stroke([(4, 5), (11, 4)], g)
        c.vline(4, 5, 7, GLINT)
        c.set(11, 6, GLINT)
    elif tier is AMBER:  # segmentation
        for x in (4, 7, 10):
            c.rect(x, 5, x + 1, 8, g)
        c.hline(4, 11, 5, GLINT)
        c.set(4, 8, GLINT)
    else:  # VIOLET - closed ring
        for x, y in ((6, 4), (7, 4), (8, 4), (5, 5), (9, 5), (5, 6),
                     (9, 6), (5, 7), (9, 7), (6, 8), (7, 8), (8, 8)):
            c.set(x, y, g)
        c.set(6, 4, GLINT)
        c.set(5, 5, GLINT)
        c.set(8, 8, GLINT)


def draw_cell(name: str, tier: Tier):
    c = Canvas()
    c.poly(CELL, fill=METAL)
    c.stroke([(2, 3), (4, 1), (11, 1)], SILVER)
    c.stroke([(11, 1), (14, 4), (14, 12), (12, 14), (4, 14), (2, 12)],
             tone(SHADE, 0.75))
    c.set(4, 1, GLINT)
    c.set(5, 1, GLINT)
    c.set(2, 3, tone(SILVER, 0.97))
    c.hline(12, 13, 4, tone(SHADE, 0.9))

    # display window: tinted panel, 1px recess shadow, bright glyph
    c.rect(3, 3, 12, 9, tier.base)
    c.hline(3, 12, 3, tone(tier.deep, 0.9))
    c.vline(3, 3, 9, tone(tier.deep, 0.9))
    c.hline(4, 12, 9, tier.light)
    c.vline(12, 4, 9, tier.light)
    cell_display(c, tier)

    # connector rail + gold edge contacts
    c.hline(3, 12, 10, tone(METAL, 0.45))
    c.rect(3, 11, 12, 12, tone(INK, 1.3))
    for x in (4, 7, 10):
        c.rect(x, 11, x + 1, 12, GOLD)
        c.set(x, 11, GOLD_HI)
        c.set(x, 12, tone(GOLD, 0.8))
    c.hline(5, 11, 13, tone(SHADE, 0.85))
    c.save(OUT / "textures" / "item" / f"{name}.png")


# --------------------------------------------------------------------------
# items: upgrade cards
# --------------------------------------------------------------------------
CARD = [(2, 1), (10, 1), (13, 4), (13, 13), (2, 13)]


def glyph_tags(c: Canvas, tier: Tier):
    # back tag, solid, peeking out top-left
    c.poly([(5, 4), (7, 4), (8, 5), (8, 6), (5, 6)], fill=tone(tier.base, 1.25))
    # front tag, filled, punch hole, one label line
    c.poly([(5, 6), (8, 6), (10, 8), (8, 9), (5, 9)], fill=tier.light)
    c.set(6, 7, INK)
    c.set(6, 8, INK)
    c.hline(7, 8, 8, tone(tier.deep, 0.85))


def glyph_compress(c: Canvas, tier: Tier):
    # three plates, wide to narrow: a compression stack
    for span, y in (((5, 10), 4), ((6, 9), 6), ((7, 8), 8)):
        c.rect(span[0], y, span[1], y + 1, tier.light)
        c.hline(span[0], span[1], y, GLINT)
    c.set(4, 4, tone(tier.base, 1.1))
    c.set(11, 4, tone(tier.base, 1.1))
    c.set(7, 9, tone(tier.deep, 0.8))


def glyph_limit(c: Canvas, tier: Tier):
    c.hline(5, 10, 4, SILVER)
    c.set(10, 4, GLINT)
    c.vline(5, 6, 8, tier.light)
    c.vline(7, 5, 8, tier.light)
    c.vline(9, 7, 8, tier.light)
    c.hline(5, 10, 9, tone(tier.base, 0.85))


def glyph_energy(c: Canvas, tier: Tier):
    c.poly([(9, 4), (6, 6), (8, 6), (7, 9), (10, 7), (8, 7)], fill=tier.light,
           outline=GLINT)
    c.set(8, 5, GLINT)


def glyph_sorting(c: Canvas, tier: Tier, level: int):
    # one split glyph for all four tiers; the tier adds chip / bridge / glow
    c.vline(7, 4, 5, tier.light)
    c.set(7, 4, GLINT)
    c.stroke([(7, 6), (5, 7)], tier.light)
    c.stroke([(7, 6), (10, 7)], tier.light)
    c.rect(5, 8, 6, 9, tier.light)
    c.rect(9, 8, 10, 9, tier.light)
    c.set(5, 8, GLINT)
    c.set(9, 8, GLINT)
    if level >= 1:
        c.rect(7, 8, 8, 9, tone(tier.base, 1.1))
        c.set(7, 8, tier.glow)
    if level >= 2:
        c.hline(5, 6, 9, tone(tier.base, 1.2))
        c.hline(9, 10, 9, tone(tier.base, 1.2))
    if level >= 3:
        c.set(7, 9, GLINT)
        c.set(8, 9, GLINT)
        c.set(5, 4, tier.glow)


def draw_card(name: str, glyph, tier: Tier, level: int = 0):
    c = Canvas()
    c.poly(CARD, fill=mix(INK, tier.deep, 0.55))
    c.stroke([(2, 1), (10, 1), (13, 4)], SILVER)
    c.stroke([(13, 4), (13, 13), (2, 13)], tone(SHADE, 0.8))
    c.vline(2, 2, 12, tone(SILVER, 0.9))

    # colour key strip + near-black component well (interior 5..10 x 4..9)
    c.rect(3, 2, 3, 12, tier.base)
    c.vline(3, 2, 12, tier.light)
    c.rect(4, 3, 11, 10, INK)
    c.hline(4, 11, 3, EDGE)
    c.vline(4, 3, 10, EDGE)
    c.hline(5, 11, 10, tone(SHADE, 0.9))
    c.vline(11, 4, 10, tone(SHADE, 0.9))

    if glyph is glyph_sorting:
        glyph(c, tier, level)
    else:
        glyph(c, tier)

    # tier lamps on the right edge
    for i in range(level + 1):
        c.set(12, 4 + i, tier.glow if i < level else tier.light)

    # gold connector teeth
    c.rect(4, 14, 11, 15, tone(INK, 1.25))
    for x in (4, 6, 8, 10):
        c.rect(x, 14, x, 15, GOLD)
        c.set(x, 15, GOLD_HI)
    c.save(OUT / "textures" / "item" / f"{name}.png")


# --------------------------------------------------------------------------
# blocks: shared chassis
# --------------------------------------------------------------------------
def chassis() -> Canvas:
    c = Canvas()
    c.rect(0, 0, 15, 15, EDGE)
    c.rect(1, 1, 14, 14, METAL)
    c.hline(1, 14, 1, SILVER)
    c.vline(1, 1, 14, SILVER)
    c.hline(2, 14, 2, METAL_L)
    c.vline(2, 2, 14, METAL_L)
    c.hline(1, 14, 14, tone(SHADE, 0.68))
    c.vline(14, 1, 14, tone(SHADE, 0.74))
    c.rect(3, 3, 12, 12, tone(INK, 1.18))
    c.hline(3, 12, 3, EDGE)
    c.vline(3, 3, 12, EDGE)
    c.hline(3, 12, 12, tone(SHADE, 0.95))
    c.vline(12, 3, 12, tone(SHADE, 0.95))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, GLINT)
    return c


def slot(c: Canvas, x0, y0, x1, y1):
    recessed(c, x0, y0, x1, y1, INK)


# --------------------------------------------------------------------------
# blocks: backplane
# --------------------------------------------------------------------------
def backplane():
    c = chassis()
    c.rect(4, 4, 11, 11, PCB)
    c.hline(4, 11, 4, tone(PCB, 0.65))
    c.vline(4, 4, 11, tone(PCB, 0.65))
    c.hline(5, 11, 9, tone(CIRCUIT, 1.5))          # main bus
    for x in (6, 9):
        c.vline(x, 5, 9, tone(CIRCUIT, 1.3))
    c.rect(5, 5, 6, 6, CHIP)
    c.set(5, 5, tone(CIRCUIT, 1.75))
    c.rect(8, 6, 9, 7, CHIP)
    c.set(8, 6, tone(CIRCUIT, 1.3))
    c.set(10, 5, GOLD_HI)
    c.set(11, 5, GOLD)
    c.set(4, 11, GOLD_HI)
    c.save(OUT / "textures" / "block" / "backplane.png")


# --------------------------------------------------------------------------
# blocks: storage interfaces
# --------------------------------------------------------------------------
def port(c: Canvas, x0, y0, x1, y1, tier: Tier, active: bool):
    slot(c, x0, y0, x1, y1)
    ix0, iy0, ix1, iy1 = x0 + 1, y0 + 1, x1 - 1, y1 - 1
    if ix0 > ix1 or iy0 > iy1:
        return
    rim = tone(tier.base, 1.0) if active else tone(DESAT.base, 1.0)
    body = tone(tier.deep, 1.4) if active else tone(DESAT.deep, 1.15)
    c.rect(ix0, iy0, ix1, iy1, body)
    c.hline(ix0, ix1, iy0, rim)
    c.hline(ix0, ix1, iy1, tone(rim, 0.8))
    # metal tongue across the throat - this is what reads as "a port"
    my = (iy0 + iy1) // 2
    tongue = tone(tier.base, 1.3) if active else STEEL
    c.hline(ix0, ix1, my, tongue)
    if iy1 - iy0 >= 2:
        c.hline(ix0, ix1, my + 1, tone(tongue, 0.78))
    if active:
        c.hline(ix0, ix1, iy1 - 1, tier.glow)
    else:
        c.hline(ix0, ix1, iy1 - 1, tone(DESAT.base, 0.72))


def interface(name: str, advanced: bool, active: bool):
    c = chassis()
    tier = VIOLET if advanced else BLUE
    if advanced:
        port(c, 4, 4, 7, 10, tier, active)
        port(c, 8, 4, 11, 10, tier, active)
    else:
        port(c, 4, 5, 11, 10, tier, active)
    # mounting screws on the lower rail
    c.set(6, 11, tone(METAL, 0.78))
    c.set(9, 11, tone(METAL, 0.78))
    if active:
        c.set(2, 3, tier.light)
        c.set(13, 3, tier.light)
        c.set(2, 12, tone(tier.base, 0.9))
        c.set(13, 12, tone(tier.base, 0.9))
    c.save(OUT / "textures" / "block" / f"{name}.png")


# --------------------------------------------------------------------------
# blocks: migrators
# --------------------------------------------------------------------------
def rail(c: Canvas, x0, y0, x1, y1, tier: Tier):
    """A 2px conveyor rail: lit top edge, deep underside, tick marks."""
    c.rect(x0, y0, x1, y1, tone(tier.base, 0.82))
    c.hline(x0, x1, y0, tier.light)
    c.hline(x0, x1, y1, tone(tier.deep, 1.25))
    for x in range(x0 + 1, x1, 3):
        c.set(x, y0, GLINT)


def transfer_marks(c: Canvas, tier: Tier, y0=6, y1=8):
    """One double-headed arrow - bidirectional transfer."""
    mid = (y0 + y1) // 2
    c.hline(5, 10, mid, tier.light)
    c.poly([(4, mid), (5, y0), (5, y1)], fill=tier.light)
    c.poly([(11, mid), (10, y0), (10, y1)], fill=tier.light)
    c.set(4, mid, GLINT)
    c.set(11, mid, GLINT)
    c.set(7, mid - 1, tone(tier.base, 1.1))
    c.set(8, mid + 1, tone(tier.base, 1.1))


def processor(c: Canvas, tier: Tier, x0=5, y0=5, x1=10, y1=10):
    """Overclocked core: muted metal package, dark die, amber core, gold contacts."""
    raised(c, x0, y0, x1, y1, tone(tier.base, 0.72), hi=tone(METAL_L, 1.0),
           lo=tone(tier.deep, 0.95))
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, tone(tier.deep, 0.85))
    c.hline(x0 + 2, x1 - 2, y0 + 2, tone(tier.light, 0.9))
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    c.rect(cx - 1, cy - 1, cx, cy, tone(tier.base, 1.3))
    c.set(cx - 1, cy - 1, tier.glow)
    for x in range(x0 + 1, x1, 2):
        c.set(x, y0 - 1, GOLD)
        c.set(x, y1 + 1, GOLD)


def quantum_ring(c: Canvas, tier: Tier, cx=7, cy=7, r=2):
    pts = []
    for dx, dy in ((-1, -2), (0, -2), (1, -2), (2, -1), (2, 0), (2, 1),
                   (1, 2), (0, 2), (-1, 2), (-2, 1), (-2, 0), (-2, -1)):
        pts.append((cx + dx, cy + dy))
    for p in pts:
        c.set(p[0], p[1], tier.light)
    c.set(cx - 1, cy - 2, GLINT)
    c.set(cx - 2, cy - 1, GLINT)
    c.set(cx, cy, tone(tier.glow, 0.9))


def migrator(name: str, tier: Tier, kind: str):
    c = chassis()
    if kind == "side":
        rail(c, 4, 3, 11, 4, tier)
        rail(c, 4, 11, 11, 12, tier)
        if tier is AMBER:
            processor(c, tier, 5, 5, 10, 10)
        elif tier is VIOLET:
            quantum_ring(c, tier, 7, 7, 2)
        else:
            transfer_marks(c, tier, 6, 8)
    else:  # top: transfer plate
        raised(c, 4, 4, 11, 11, tone(METAL_L, 0.84), hi=SILVER,
               lo=tone(SHADE, 0.95))
        if tier is AMBER:
            processor(c, tier, 5, 5, 10, 10)
        elif tier is VIOLET:
            quantum_ring(c, tier, 7, 7, 2)
            for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
                c.set(x, y, tone(tier.base, 0.9))
        else:
            transfer_marks(c, tier, 6, 9)
    c.save(OUT / "textures" / "block" / f"{name}.png")


# --------------------------------------------------------------------------
# blocks: devices
# --------------------------------------------------------------------------
def drive():
    c = chassis()
    c.rect(4, 4, 11, 11, tone(METAL, 0.5))
    for i in range(3):
        y = 4 + i * 3
        slot(c, 4, y, 11, y + 1)
        c.hline(4, 5, y + 1, GOLD)
        c.set(4, y + 1, GOLD_HI)
        c.set(10, y + 1, MINT.light)
        c.set(11, y + 1, tone(MINT.base, 0.9))
        c.set(6, y + 1, tone(METAL, 0.75))
    c.save(OUT / "textures" / "block" / "bulk_storage_drive.png")


def workbench():
    c = chassis()
    raised(c, 4, 4, 11, 11, tone(METAL, 1.25), hi=METAL_L,
           lo=tone(SHADE, 0.7))
    # left zone: cell socket with a blue cell seated in it
    recessed(c, 4, 5, 7, 8, tone(INK, 1.1))
    c.rect(5, 6, 6, 8, tone(BLUE.deep, 1.2))
    c.stroke([(5, 6), (6, 6), (6, 8), (5, 8), (5, 6)], BLUE.light)
    c.set(5, 6, GLINT)
    # divider between the two zones
    c.vline(8, 5, 8, tone(SHADE, 0.75))
    # right zone: lift chevron
    recessed(c, 9, 5, 11, 8, tone(INK, 1.1))
    c.stroke([(9, 8), (10, 6)], MINT.light)
    c.stroke([(11, 8), (10, 6)], MINT.light)
    c.set(10, 6, GLINT)
    # tool rail
    c.hline(4, 11, 10, tone(SHADE, 0.85))
    c.set(5, 9, GOLD)
    c.set(7, 9, GOLD)
    c.set(10, 9, tone(METAL_L, 0.9))
    c.save(OUT / "textures" / "block" / "advanced_cell_workbench.png")


def manager():
    c = chassis()
    for iy, y in enumerate((4, 7, 10)):
        for ix, x in enumerate((4, 7, 10)):
            led = MINT if (ix + iy) % 3 != 1 else BLUE
            c.rect(x, y, x + 1, y + 1, tone(METAL_L, 0.92))
            c.hline(x, x + 1, y, GLINT)
            c.hline(x, x + 1, y + 1, led.light)
            c.vline(x + 1, y + 1, y + 1, tone(SHADE, 0.9))
    c.save(OUT / "textures" / "block" / "bulk_storage_manager.png")


def monitor(name: str, signal: bool):
    c = chassis()
    # screen
    c.rect(4, 4, 11, 9, SCREEN)
    c.hline(4, 11, 4, tone(METAL, 0.55))
    c.vline(4, 4, 9, tone(METAL, 0.55))
    c.hline(4, 11, 9, tone(METAL, 0.6))
    c.vline(11, 4, 9, tone(METAL, 0.6))
    grid = tone(SCREEN, 1.7)
    c.hline(5, 11, 6, grid)
    c.hline(5, 11, 8, grid)
    trace = MINT.light if signal else tone(MINT.light, 0.82)
    c.stroke([(5, 8), (7, 6), (9, 8), (11, 5)], trace)
    c.stroke([(5, 9), (7, 7), (9, 9), (11, 6)], tone(trace, 0.8))
    c.set(7, 6, GLINT)
    c.set(11, 5, GLINT)
    # status strip + threshold lamp
    c.rect(4, 10, 11, 11, tone(METAL, 0.58))
    c.hline(4, 11, 10, tone(SHADE, 1.05))
    lamp = ALARM_ON if signal else tone(ALARM, 0.55)
    c.set(10, 11, lamp)
    c.set(11, 11, tone(lamp, 0.8))
    if signal:
        c.set(10, 10, tone(ALARM_ON, 0.85))
    for x in (5, 6, 7):
        c.set(x, 11, tone(METAL, 0.82))
    c.save(OUT / "textures" / "block" / f"{name}.png")


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------
def render():
    for sub in ("item", "block"):
        (OUT / "textures" / sub).mkdir(parents=True, exist_ok=True)

    draw_cell("tag_bulk_storage_cell", MINT)
    draw_cell("advanced_tag_bulk_storage_cell", BLUE)
    draw_cell("mod_bulk_storage_cell", AMBER)
    draw_cell("quantum_bulk_storage_cell", VIOLET)

    draw_card("extended_tag_card", glyph_tags, MINT)
    draw_card("multi_compression_card", glyph_compress, MINT)
    draw_card("storage_limit_card", glyph_limit, BLUE)
    draw_card("energy_saving_card", glyph_energy, MINT)
    draw_card("auto_sorting_card", glyph_sorting, MINT, 0)
    draw_card("advanced_auto_sorting_card", glyph_sorting, BLUE, 1)
    draw_card("overclocked_auto_sorting_card", glyph_sorting, AMBER, 2)
    draw_card("quantum_auto_sorting_card", glyph_sorting, VIOLET, 3)

    backplane()
    interface("bulk_storage_interface", False, False)
    interface("bulk_storage_interface_online", False, True)
    interface("advanced_bulk_storage_interface", True, False)
    interface("advanced_bulk_storage_interface_online", True, True)

    migrator("bulk_storage_migrator_side", MINT, "side")
    migrator("bulk_storage_migrator_top", MINT, "top")
    migrator("overclocked_bulk_storage_migrator_side", AMBER, "side")
    migrator("overclocked_bulk_storage_migrator_top", AMBER, "top")
    migrator("quantum_bulk_storage_migrator_side", VIOLET, "side")
    migrator("quantum_bulk_storage_migrator_top", VIOLET, "top")

    drive()
    workbench()
    manager()
    monitor("bulk_storage_monitor", False)
    monitor("bulk_storage_monitor_signal", True)


def install():
    for sub in ("item", "block"):
        src = OUT / "textures" / sub
        dst = SRC / sub
        dst.mkdir(parents=True, exist_ok=True)
        for png in sorted(src.glob("*.png")):
            shutil.copy2(png, dst / png.name)
            print(f"installed {sub}/{png.name}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true",
                    help="copy rendered PNGs into src/main/resources")
    args = ap.parse_args()
    render()
    print(f"rendered review set -> {OUT}")
    if args.install:
        install()


if __name__ == "__main__":
    main()
