"""advanced_bulk_storage - texture generator v3 (AE2-aligned).

Rendered from AE2's actual art language, taken from the AE2 jar textures
(item_storage_cell_*, basic/advanced_card, io_port_*, cell_workbench_*):

casing  = bright top row, 1px groove under it, flat light grey face, a
          horizontal seam, a lower plate with vent dots, dark bottom row;
          our v2 heavy silver frame + corner rivets is gone.
cell    = diamond gem: dark outline, silver shell ring lit top-left and
          shaded bottom-right, tier coloured core, white pulse, gold contacts
          on the near edge.  (Not a flat colour slab.)
card    = 10x16 light grey body, dark inset panel with the function mark,
          four tier coloured edge contacts on the right, plug fingers below.

    python tools/draw_assets_v3.py            # -> art-review/v3/
    python tools/draw_assets_v3.py --install  # copy PNGs into src/main/resources
"""

import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "art-review" / "v3"
SRC = ROOT / "src" / "main" / "resources" / "assets" / "advanced_bulk_storage"

# --- casing greys (AE2 "grey machine" family) --------------------------------
W = (242, 242, 242)      # f2f2f2  brightest edge
L = (232, 232, 234)      # e8e8ea
M = (222, 223, 227)      # dedfe3  main face
B = (213, 214, 220)      # d5d6dc  groove
S = (203, 204, 212)      # cbccd4  seam
M2 = (179, 182, 198)     # b3b6c6  lower plate
M3 = (168, 172, 190)     # a8acbe
D = (154, 159, 180)      # 9a9fb4
D2 = (135, 143, 165)     # 878fa5
D3 = (120, 126, 151)     # 787e97  dark bottom row
INSET = (105, 109, 136)  # 696d88
INSET2 = (77, 77, 103)   # 4d4d67
INSET3 = (65, 63, 84)    # 413f54
NAVY = (38, 43, 84)      # 262b54  dark slot / screen
NAVY_DEEP = (20, 18, 35)  # 141223

# --- card body ---------------------------------------------------------------
CARD_OUT_HI = (78, 83, 90)    # 4e535a
CARD_OUT_LO = (53, 57, 64)    # 353940
CARD_FACE = (165, 167, 172)   # a5a7ac
CARD_HI = (222, 223, 226)     # dedfe2
CARD_MID = (193, 195, 199)    # c1c3c7
CARD_SH = (121, 126, 132)     # 797e84
CARD_SH2 = (97, 102, 109)     # 61666d

# --- accents -----------------------------------------------------------------
GLINT = (247, 252, 255)
BLUE_W = (218, 255, 255)
BLUE_HI = (156, 211, 255)
BLUE = (125, 169, 210)
BLUE_D = (112, 140, 186)
GOLD_HI = (250, 214, 74)
GOLD = (233, 177, 21)
GOLD_D = (220, 150, 19)
GOLD_DK = (178, 100, 17)
RED = (214, 75, 60)
RED_D = (140, 40, 32)
VIOLET_HI = (201, 174, 232)
VIOLET = (169, 143, 216)
VIOLET_D = (143, 111, 196)
VIOLET_DK = (91, 63, 138)


class Tier:
    """deep / base / light / glow for one storage tier."""

    def __init__(self, name, deep, base, light, glow, metal_dk, metal, metal_hi):
        self.name = name
        self.deep = deep
        self.base = base
        self.light = light
        self.glow = glow
        self.metal_dk = metal_dk      # edge contact shades
        self.metal = metal
        self.metal_hi = metal_hi


MINT = Tier("mint", (17, 114, 122), (28, 145, 154), (26, 170, 167), (32, 197, 181),
            (17, 114, 122), (28, 145, 154), (32, 197, 181))
BLUE_T = Tier("blue", (112, 140, 186), (125, 169, 210), (156, 211, 255), (218, 255, 255),
              (112, 140, 186), (125, 169, 210), (218, 255, 255))
AMBER = Tier("amber", (178, 100, 17), (220, 150, 19), (233, 177, 21), (250, 214, 74),
             (178, 100, 17), (220, 150, 19), (250, 214, 74))
VIOLET_T = Tier("violet", (91, 63, 138), (143, 111, 196), (169, 143, 216), (201, 174, 232),
                (91, 63, 138), (143, 111, 196), (201, 174, 232))
DESAT = Tier("desat", (90, 94, 104), (120, 125, 136), (150, 155, 166), (176, 181, 190),
             (120, 125, 136), (150, 155, 166), (176, 181, 190))


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


class Canvas:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()

    def set(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h and color is not None:
            self.px[x, y] = (color[0], color[1], color[2], 255)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            p = self.px[x, y]
            return (p[0], p[1], p[2]) if p[3] else None
        return None

    def rect(self, x0, y0, x1, y1, color):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                self.set(x, y, color)

    def frame(self, x0, y0, x1, y1, color):
        self.hline(x0, x1, y0, color)
        self.hline(x0, x1, y1, color)
        self.vline(x0, y0, y1, color)
        self.vline(x1, y0, y1, color)

    def hline(self, x0, x1, y, color):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            self.set(x, y, color)

    def vline(self, x, y0, y1, color):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            self.set(x, y, color)

    def stroke(self, pts, color):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            dx, dy = abs(x1 - x0), abs(y1 - y0)
            sx = 1 if x0 < x1 else -1
            sy = 1 if y0 < y1 else -1
            err = dx - dy
            x, y = x0, y0
            while True:
                self.set(x, y, color)
                if x == x1 and y == y1:
                    break
                e2 = 2 * err
                if e2 > -dy:
                    err -= dy
                    x += sx
                if e2 < dx:
                    err += dx
                    y += sy

    def diamond(self, cx, cy, r, fill=None, outline=None):
        """Solid diamond with radius r (Manhattan), used for cells."""
        for y in range(cy - r, cy + r + 1):
            span = r - abs(y - cy)
            for x in range(cx - span, cx + span + 1):
                if fill is not None:
                    self.set(x, y, fill)
                if outline is not None and abs(x - cx) + abs(y - cy) == r:
                    self.set(x, y, outline)

    def poly(self, pts, fill=None, outline=None):
        ys = [p[1] for p in pts]
        for y in range(min(ys), max(ys) + 1):
            xs = []
            for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]):
                if y0 == y1:
                    continue
                if min(y0, y1) <= y < max(y0, y1):
                    xs.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                for x in range(int(round(xs[i])), int(round(xs[i + 1])) + 1):
                    if fill is not None:
                        self.set(x, y, fill)
            if outline is not None:
                for i in range(0, len(xs) - 1, 2):
                    self.set(int(round(xs[i])), y, outline)
                    self.set(int(round(xs[i + 1])), y, outline)

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(path)
        return path


def raised(c, x0, y0, x1, y1, fill, hi=W, lo=D):
    c.rect(x0, y0, x1, y1, fill)
    c.hline(x0, x1, y0, hi)
    c.vline(x0, y0, y1, hi)
    c.hline(x0, x1, y1, lo)
    c.vline(x1, y0, y1, lo)


# =============================================================================
# blocks: AE2 casing with one feature panel
# =============================================================================
def casing():
    c = Canvas()
    c.rect(0, 0, 15, 15, M)
    c.hline(0, 15, 0, W)
    c.hline(0, 15, 1, B)          # groove just under the bright edge
    c.hline(0, 15, 14, D)
    c.hline(0, 15, 15, D3)        # dark bottom row
    c.vline(0, 1, 14, L)          # lit left edge
    c.vline(15, 1, 14, M2)        # shaded right edge
    c.set(0, 15, D2)
    c.set(15, 15, D3)
    c.set(0, 0, W)
    for x in range(1, 15, 3):
        c.set(x, 0, L)
    c.hline(1, 14, 12, S)         # plate seam
    c.hline(1, 14, 13, M2)
    for x in (4, 7, 10, 13):      # vent dots on the lower plate
        c.set(x, 13, INSET2)
    return c


def panel(c, x0=2, y0=2, x1=13, y1=11, floor=None, lip=D):
    c.rect(x0, y0, x1, y1, floor if floor else M2)
    c.hline(x0, x1, y0, INSET2)
    c.vline(x0, y0, y1, INSET2)
    c.hline(x0, x1, y1, lip)
    c.vline(x1, y0, y1, lip)
    c.set(x0, y1, lip)
    c.set(x1, y0, INSET2)


def slot(c, x0, y0, x1, y1, bar=None, lit=False):
    """AE2 io_port style: dark recess with a bright bar inside."""
    c.rect(x0, y0, x1, y1, NAVY)
    c.hline(x0, x1, y0, NAVY_DEEP)
    c.vline(x0, y0, y1, NAVY_DEEP)
    c.hline(x0, x1, y1, INSET3)
    if bar:
        c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, bar)
        c.hline(x0 + 1, x1 - 1, y0 + 1, mix(bar, GLINT, 0.55))
        if lit:
            c.set(x0 + 1, y0 + 1, GLINT)
            c.set(x1 - 1, y0 + 1, GLINT)


def backplane():
    """Shared substrate: a bare plate with one trace and two pads."""
    c = casing()
    panel(c, 3, 4, 12, 9)
    c.hline(4, 11, 6, MINT.base)
    c.hline(4, 11, 7, MINT.deep)
    c.set(4, 6, MINT.light)
    c.set(11, 6, MINT.light)
    c.set(7, 6, INSET2)
    c.set(8, 7, INSET2)
    for x in (5, 10):
        c.rect(x, 5, x, 8, NAVY)
        c.set(x, 5, INSET2)
        c.set(x, 8, NAVY_DEEP)
    c.set(6, 8, GOLD)
    c.set(9, 5, GOLD)
    return c


def interface(advanced=False, online=False):
    c = casing()
    panel(c)
    if advanced:
        for x0 in (3, 8):
            slot(c, x0, 4, x0 + 4, 9, bar=VIOLET_HI if online else M3, lit=online)
            c.vline(x0 + 2, 5, 5, VIOLET if online else D)
            if online:
                c.hline(x0 + 1, x0 + 3, 4, VIOLET_HI)
                c.set(x0 + 1, 9, VIOLET)
        c.vline(7, 2, 11, INSET3)
    else:
        slot(c, 3, 5, 12, 9, bar=BLUE_HI if online else M3, lit=online)
        if online:
            c.hline(4, 11, 5, BLUE_W)
            c.set(4, 8, BLUE)
            c.set(11, 8, BLUE)
            c.set(7, 7, BLUE_D)
        else:
            c.hline(4, 11, 8, D2)
            c.set(7, 7, INSET2)
    return c


def rail(c, x0, x1, y, tier, lit=True):
    c.hline(x0, x1, y - 1, INSET3)
    c.hline(x0, x1, y, tier.base)
    c.hline(x0, x1, y + 1, tier.deep)
    if lit:
        for x in range(x0 + 1, x1, 3):
            c.set(x, y, tier.glow)


def arrows(c, cy, tier):
    """Opposed transfer arrows: the migrator's whole point."""
    c.hline(4, 11, cy, tier.deep)
    c.hline(5, 10, cy, tier.base)
    c.poly([(3, cy), (5, cy - 2), (5, cy + 2)], fill=tier.light)
    c.poly([(12, cy), (10, cy - 2), (10, cy + 2)], fill=tier.light)
    c.set(3, cy, tier.glow)
    c.set(12, cy, tier.glow)
    c.set(7, cy, tier.light)
    c.set(8, cy, tier.light)


def processor(c, tier, x0=5, y0=5, x1=10, y1=9):
    """Chip package: metal body, dark die cavity, small lit core, heat contacts."""
    raised(c, x0, y0, x1, y1, M2, hi=L, lo=D)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, INSET3)
    c.hline(x0 + 1, x1 - 1, y0 + 1, NAVY_DEEP)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    c.rect(cx - 1, cy - 1, cx, cy, tier.deep)
    c.set(cx - 1, cy - 1, tier.light)
    c.set(cx, cy - 1, tier.base)
    for x in (x0 + 1, x1 - 1):
        c.set(x, y0 - 1, GOLD_D)
        c.set(x, y1 + 1, GOLD_D)
        c.set(x, y0 - 2, GOLD_DK)
        c.set(x, y1 + 2, GOLD_DK)


def quantum_ring(c, tier, r=2):
    cx, cy = 7, 7
    c.diamond(cx, cy, r, outline=tier.base)
    for k in range(1, r):
        c.set(cx - k, cy - (r - k), tier.light)
        c.set(cx + k, cy - (r - k), tier.light)
        c.set(cx - k, cy + (r - k), tier.deep)
        c.set(cx + k, cy + (r - k), tier.deep)
    c.set(cx, cy - r, GLINT)
    c.set(cx, cy + r, tier.deep)
    c.set(cx - 1, cy, tier.glow)
    c.set(cx, cy, tier.glow)


def migrator(tier, top=False):
    c = casing()
    panel(c, 2, 2, 13, 11, floor=M2)
    if top:
        raised(c, 3, 4, 12, 9, M, hi=L, lo=D)
        if tier is AMBER:
            processor(c, tier, 5, 5, 10, 8)
        elif tier is VIOLET_T:
            quantum_ring(c, tier, 2)
            c.hline(4, 11, 4, tier.base)
            c.hline(4, 11, 9, tier.deep)
        else:
            c.hline(4, 11, 4, tier.base)
            c.hline(4, 11, 5, tier.deep)
            c.hline(4, 11, 8, tier.base)
            c.hline(4, 11, 9, tier.deep)
            arrows(c, 7, tier)
    else:
        rail(c, 3, 12, 3, tier)
        rail(c, 3, 12, 10, tier)
        if tier is AMBER:
            processor(c, tier, 5, 5, 10, 9)
        elif tier is VIOLET_T:
            quantum_ring(c, tier, 2)
        else:
            arrows(c, 7, tier)
    return c


def drive():
    c = casing()
    panel(c, 2, 2, 13, 11, floor=M2)
    for y in (3, 5, 7, 9):
        c.rect(3, y, 12, y + 1, NAVY)
        c.hline(3, 12, y, NAVY_DEEP)
        c.hline(4, 11, y + 1, INSET3)
        c.set(3, y + 1, MINT.light)
        c.set(4, y + 1, MINT.base)
        c.set(11, y + 1, GOLD)
        c.set(12, y + 1, GOLD_D)
        c.hline(6, 9, y + 1, S)
    return c


def workbench():
    c = casing()
    panel(c, 2, 2, 13, 11, floor=M2)
    # left: cell socket
    c.rect(3, 3, 6, 10, INSET3)
    c.hline(3, 6, 3, NAVY_DEEP)
    c.vline(3, 3, 10, NAVY_DEEP)
    c.hline(4, 5, 10, BLUE_D)
    for y in (5, 7, 9):
        c.set(4, y, BLUE)
        c.set(5, y, BLUE_HI)
    # right: tool dock with two rails
    c.rect(8, 3, 12, 10, M3)
    c.hline(8, 12, 3, D)
    c.hline(8, 12, 10, M)
    for y in (5, 8):
        c.hline(9, 11, y, GOLD)
        c.hline(9, 11, y + 1, GOLD_DK)
    c.set(9, 3, GOLD_D)
    c.set(11, 3, GOLD_D)
    return c


def manager():
    c = casing()
    panel(c, 2, 2, 13, 11, floor=M2)
    for iy, y in enumerate((3, 6, 9)):
        for ix, x in enumerate((3, 6, 9)):
            led = MINT if (ix + iy) % 3 else BLUE_T
            c.rect(x, y, x + 2, y + 1, M)
            c.hline(x, x + 2, y, L)
            c.hline(x, x + 2, y + 1, M2)
            c.set(x + 1, y + 1, led.light)
            c.set(x, y + 1, D)
    return c


def monitor(signal=False):
    c = casing()
    panel(c, 2, 2, 13, 11, floor=M2)
    c.rect(3, 3, 12, 9, NAVY)
    c.hline(3, 12, 3, NAVY_DEEP)
    c.vline(3, 3, 9, NAVY_DEEP)
    c.hline(4, 11, 9, INSET3)
    c.vline(12, 4, 9, INSET3)
    for y in (5, 7):
        c.hline(4, 11, y, mix(NAVY, BLUE_D, 0.25))
    for x in (6, 9):
        c.vline(x, 4, 8, mix(NAVY, BLUE_D, 0.25))
    trace = BLUE_W if not signal else GLINT
    c.stroke([(4, 8), (6, 5), (8, 8), (10, 4), (11, 6)], trace)
    c.set(6, 5, GLINT)
    c.set(10, 4, GLINT)
    # threshold lamp on the lower plate
    lamp = RED if signal else RED_D
    c.rect(6, 11, 9, 11, lamp)
    c.set(6, 11, mix(lamp, GLINT, 0.4) if signal else lamp)
    if signal:
        c.set(5, 11, mix(RED, GLINT, 0.5))
        c.set(10, 11, mix(RED, GLINT, 0.5))
    return c


# =============================================================================
# items: AE2 cell (pixel-traced) + AE2 upgrade card
# =============================================================================
# The grid below is the AE2 1.21 item_storage_cell_1k texture, traced pixel by
# pixel (assets/ae2/textures/item/item_storage_cell_1k.png).  Every token keeps
# AE2's own grey; only the three core tokens 1/2/3 are re-coloured per tier, so
# the silhouette, the facet shading and the white pulse stay AE2's.
#
#   .  transparent (AE2 stores leftover RGB on alpha=0 pixels; we clear it)
#   o  #413f54  deep outline        d  #4d4d67  outline
#   s  #cbccd4  shell               h  #dedfe3  shell highlight
#   m  #9a9fb4  shell shade         k  #696d88  deep shade
#   v  #878fa5  darker shade        b  #adb0c4  mid highlight
#   w  #f2f2f2  white pulse
#   1  #2d69e1 -> tier.base         2  #3894ff -> tier.light
#   3  #40c1ff -> tier.glow
AE2_CELL = """
................
.......ddo......
.....ddssso.....
...ddsshssso....
.ddssshssssso...
dhshhhs111shso..
dm1111hs221shso.
dm13221hs231shso
dm113221hh231kko
.dm21whhhww21vko
..dm2bwwwmv11kko
...dmbbsmovkkoo.
....dmbsmvkoo...
.....dmbvoo.....
......doo.......
................
"""

AE2_CELL_INK = {
    "o": (65, 63, 84),
    "d": (77, 77, 103),
    "s": (203, 204, 212),
    "h": (222, 223, 227),
    "m": (154, 159, 180),
    "k": (105, 109, 136),
    "v": (135, 143, 165),
    "b": (173, 176, 196),
    "w": (242, 242, 242),
}

# Shell ramps. The token grid above stays identical, only the ink changes, so
# "AE2 structure, other mod's material" is a one-line switch.
#   AE2        = AE2's own silver shell (the original)
#   MEGA_BULK  = MEGA Cells' bulk_item_cell ramp (warm gunmetal, #111111..#737173)
#   MEGA_DARK  = MEGA Cells' mega_item_cell_housing ramp (near-black, #141515..#393e39)
# Both MEGA ramps are transcribed 1:1 from the corresponding jar texture, pixel
# for pixel at the same coordinates (the three silhouettes are identical).
SHELL_AE2 = AE2_CELL_INK
SHELL_MEGA_BULK = {
    "o": (0x11, 0x11, 0x11),
    "d": (0x27, 0x1c, 0x1d),
    "s": (0x4d, 0x49, 0x4d),
    "h": (0x5a, 0x57, 0x5a),
    "m": (0x4c, 0x41, 0x43),
    "k": (0x31, 0x29, 0x2a),
    "v": (0x3c, 0x32, 0x32),
    "b": (0x3b, 0x39, 0x3b),
    "w": (0x73, 0x71, 0x73),
}
SHELL_MEGA_DARK = {
    "o": (0x14, 0x15, 0x15),
    "d": (0x16, 0x17, 0x17),
    "s": (0x2d, 0x2f, 0x2d),
    "h": (0x33, 0x36, 0x33),
    "m": (0x1f, 0x22, 0x21),
    "k": (0x1a, 0x1c, 0x1c),
    "v": (0x1f, 0x1f, 0x1f),
    "b": (0x26, 0x28, 0x26),
    "w": (0x39, 0x3e, 0x39),
}

SHELLS = {"ae2": SHELL_AE2, "bulk": SHELL_MEGA_BULK, "dark": SHELL_MEGA_DARK}
SHELL = SHELL_MEGA_BULK   # default proposal: MEGA Cells' bulk_item_cell material


def cell(name, tier):
    """AE2 cell silhouette, only the core hue swapped for this tier."""
    c = Canvas()
    core = {1: tier.base, 2: tier.light, 3: tier.glow}
    for y, row in enumerate(AE2_CELL.strip("\n").split("\n")):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c.set(x, y, core.get(int(ch)) if ch.isdigit() else SHELL[ch])
    return c



def card(name, tier, mark):
    c = Canvas()
    x0, y0, x1, y1 = 2, 0, 11, 15
    c.rect(x0 + 1, y0, x1 - 1, y1, CARD_FACE)
    c.frame(x0, y0, x1, y1, CARD_OUT_HI)
    c.hline(x0, x1, y0, CARD_OUT_HI)
    c.vline(x0, y0, 9, CARD_OUT_HI)
    c.vline(x1, 10, y1, CARD_OUT_LO)
    c.hline(x0, x1, y1, CARD_OUT_LO)
    c.hline(x0 + 1, x1 - 1, y0 + 1, CARD_HI)
    c.vline(x0 + 1, y0 + 1, 9, CARD_HI)
    c.hline(x0 + 1, x1 - 1, 10, CARD_MID)
    c.rect(x0 + 1, 11, x1 - 1, 12, CARD_MID)
    c.hline(x0 + 1, x1 - 1, 12, CARD_HI)
    c.rect(x0 + 1, 13, x1 - 1, 14, CARD_SH2)
    for x in (x0 + 2, x0 + 4, x0 + 6, x0 + 8):
        c.set(x, 13, CARD_SH)
        c.set(x, 14, CARD_OUT_LO)
    # dark inset panel holding the function mark
    c.rect(3, 3, 10, 10, NAVY)
    c.hline(3, 10, 3, NAVY_DEEP)
    c.vline(3, 3, 10, NAVY_DEEP)
    c.hline(3, 10, 10, INSET3)
    c.vline(10, 3, 10, INSET3)
    # tier edge contacts on the right: four pads with dark gaps between
    for row in range(3, 11):
        if row % 2:
            c.set(12, row, tier.metal_dk)
            c.set(13, row, tier.metal)
        else:
            c.set(12, row, CARD_OUT_LO)
            c.set(13, row, CARD_SH2)
    c.set(12, 12, CARD_SH2)
    c.set(13, 12, CARD_SH2)
    c.set(12, 11, CARD_MID)
    c.set(13, 11, CARD_SH)
    mark(c, tier)
    return c


def mark_tags(c, tier):
    # back tag, dark; front tag lit with one punch hole
    c.poly([(5, 4), (7, 4), (8, 5), (7, 6), (5, 6)], fill=tier.deep)
    c.poly([(5, 6), (7, 6), (9, 8), (7, 9), (5, 9)], fill=tier.light)
    c.set(6, 7, NAVY)
    c.set(7, 7, mix(tier.light, GLINT, 0.6))


def mark_compress(c, tier):
    # three plates narrowing towards the bottom: a compression stack
    for x0, x1, y in ((5, 9, 4), (6, 9, 6), (7, 9, 8)):
        c.hline(x0, x1, y, tier.light if y != 6 else GLINT)
        c.hline(x0, x1, y + 1, tier.deep)


def mark_limit(c, tier):
    # hard ceiling, then a gauge that is cut short by the cap
    c.hline(4, 9, 4, GLINT)
    c.hline(4, 9, 5, tier.deep)
    c.hline(4, 7, 6, tier.light)
    c.hline(4, 7, 7, tier.base)
    c.hline(4, 6, 8, tier.light)
    c.hline(4, 6, 9, tier.base)
    c.vline(8, 6, 7, INSET3)
    c.vline(9, 4, 9, tier.deep)
    c.set(9, 4, GLINT)


def mark_energy(c, tier):
    bolt = [(8, 4), (5, 8), (7, 8), (6, 10), (9, 6), (7, 6)]
    c.poly(bolt, fill=GLINT)
    c.set(8, 4, tier.light)
    c.set(5, 8, tier.light)


def mark_sorting(level):
    def draw(c, tier):
        c.vline(7, 4, 5, GLINT)
        c.stroke([(7, 5), (5, 8)], tier.light)
        c.stroke([(7, 5), (9, 8)], tier.light)
        c.poly([(4, 9), (4, 7), (6, 8)], fill=tier.base)
        c.poly([(10, 9), (10, 7), (8, 8)], fill=tier.base)
        c.set(4, 9, tier.light)
        c.set(10, 9, tier.light)
        if level >= 1:
            c.set(8, 5, tier.glow)
        if level >= 2:
            c.set(6, 5, tier.glow)
            c.set(7, 6, tier.base)
        if level >= 3:
            c.set(7, 10, GLINT)
            c.set(4, 6, tier.glow)
            c.set(10, 6, tier.glow)
    return draw


# =============================================================================
def write(canvas, kind, name):
    return canvas.save(OUTPUT / "textures" / kind / f"{name}.png")


def build():
    item = OUTPUT / "textures" / "item"
    block = OUTPUT / "textures" / "block"
    for d in (item, block):
        d.mkdir(parents=True, exist_ok=True)

    for name, tier in (("tag_bulk_storage_cell", MINT),
                       ("advanced_tag_bulk_storage_cell", BLUE_T),
                       ("mod_bulk_storage_cell", AMBER),
                       ("quantum_bulk_storage_cell", VIOLET_T)):
        write(cell(name, tier), "item", name)

    cards = [
        ("extended_tag_card", MINT, mark_tags),
        ("multi_compression_card", AMBER, mark_compress),
        ("storage_limit_card", BLUE_T, mark_limit),
        ("energy_saving_card", AMBER, mark_energy),
        ("auto_sorting_card", MINT, mark_sorting(0)),
        ("advanced_auto_sorting_card", BLUE_T, mark_sorting(1)),
        ("overclocked_auto_sorting_card", AMBER, mark_sorting(2)),
        ("quantum_auto_sorting_card", VIOLET_T, mark_sorting(3)),
    ]
    for name, tier, mark_fn in cards:
        write(card(name, tier, mark_fn), "item", name)

    write(backplane(), "block", "backplane")
    write(interface(False, False), "block", "bulk_storage_interface")
    write(interface(False, True), "block", "bulk_storage_interface_online")
    write(interface(True, False), "block", "advanced_bulk_storage_interface")
    write(interface(True, True), "block", "advanced_bulk_storage_interface_online")
    for prefix, tier in (("bulk_storage", MINT), ("overclocked_bulk_storage", AMBER),
                         ("quantum_bulk_storage", VIOLET_T)):
        write(migrator(tier, top=False), "block", f"{prefix}_migrator_side")
        write(migrator(tier, top=True), "block", f"{prefix}_migrator_top")
    write(drive(), "block", "bulk_storage_drive")
    write(workbench(), "block", "advanced_cell_workbench")
    write(manager(), "block", "bulk_storage_manager")
    write(monitor(False), "block", "bulk_storage_monitor")
    write(monitor(True), "block", "bulk_storage_monitor_signal")
    print(f"rendered -> {OUTPUT}")


def install():
    for kind in ("item", "block"):
        src_dir = OUTPUT / "textures" / kind
        dst_dir = SRC / "textures" / kind
        dst_dir.mkdir(parents=True, exist_ok=True)
        for png in sorted(src_dir.glob("*.png")):
            shutil.copy2(png, dst_dir / png.name)
            print(f"installed {kind}/{png.name}")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if args and args[0] in SHELLS:
        SHELL = SHELLS[args[0]]
        print(f"cell shell = {args[0]}")
    build()
    if "--install" in sys.argv:
        install()
