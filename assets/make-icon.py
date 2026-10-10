#!/usr/bin/env python3
"""PairedPortals project icon - deterministic, drawn from code (no image model, no AI art).

Layout: the "PP" of PairedPortals as two blocky capital P's on a 16x16 cell grid.
The left P sits toward the top-left, the right P toward the bottom-right, so the
pair reads as a staircase. Each P carries a white outline. Background is a flat
obsidian colour; the letters are the nether-portal colour.

Palette provenance (sampled from this repository's own in-game evidence,
test/client-evidence/*.png - see the numbers in the docstrings below):

  obsidian  #130C1D  median of every low-value violet pixel (hue 235-285 deg,
                     saturation 0.35-0.80, value 0.02-0.30) across the 23
                     evidence screenshots: n = 255486, clusters #06040A 24%,
                     #0E0916 26%, #190F25 19%, #211730 16%, #29223E 15%
  portal    #6F17CF  mean of the brightest half of the nether-portal pixels of
                     03-f20-after-travel.png (the full-screen portal overlay =
                     the vanilla nether_portal texture at full brightness):
                     n = 89849, dark half mean #4301B1, bright half mean #6F17CF,
                     top 30% mean #7D21D5
  outline   #FFFFFF  plain white

Usage:
    python make-icon.py                       # 512 and 128 into this directory
    python make-icon.py --size 256 --out x.png
"""

from __future__ import annotations

import argparse
import os

from PIL import Image, ImageDraw, ImageFilter

OBSIDIAN = (0x13, 0x0C, 0x1D)
PORTAL = (0x6F, 0x17, 0xCF)
OUTLINE = (0xFF, 0xFF, 0xFF)

CELLS = 16  # the canvas is a 16x16 grid of square cells
CELL_512 = 512 // CELLS  # 32 px per cell at 512
OUTLINE_512 = 10  # white outline thickness, in px, at 512

# One blocky capital P: 6 cells wide, 10 cells tall. '#' is ink, '.' is a hole.
GLYPH = (
    "######",
    "##..##",
    "##..##",
    "######",
    "##....",
    "##....",
    "##....",
    "##....",
    "##....",
    "##....",
)

LEFT = (1, 2)  # (column, row) of the left glyph's top-left cell
RIGHT = (9, 4)  # ... and of the right one: 2 cells lower, so P1 sits high-left

DEFAULT_SIZES = (512, 128)


def render(size: int) -> Image.Image:
    """Draw the icon at `size` x `size`. `size` must be a multiple of CELLS."""
    if size % CELLS:
        raise SystemExit(f"size must be a multiple of {CELLS}: {size}")
    cell = size // CELLS
    outline_px = max(1, round(OUTLINE_512 * size / 512))

    # 1. the two glyphs, as a hard-edged mask
    mask = Image.new("L", (size, size), 0)
    pen = ImageDraw.Draw(mask)
    for col0, row0 in (LEFT, RIGHT):
        for row, line in enumerate(GLYPH):
            for col, ch in enumerate(line):
                if ch != "#":
                    continue
                x, y = (col0 + col) * cell, (row0 + row) * cell
                pen.rectangle((x, y, x + cell - 1, y + cell - 1), fill=255)

    # 2. dilate the mask into the outline, then stamp outline under ink
    grown = mask.filter(ImageFilter.MaxFilter(outline_px * 2 + 1))
    icon = Image.new("RGB", (size, size), OBSIDIAN)
    icon.paste(OUTLINE, mask=grown)
    icon.paste(PORTAL, mask=mask)
    return icon


def main() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--size", type=int, default=None, help="render just this size")
    ap.add_argument("--out", default=None, help="output path (needs --size)")
    args = ap.parse_args()

    if args.out and not args.size:
        raise SystemExit("--out needs --size")
    if args.size:
        out = args.out or os.path.join(here, f"icon-{args.size}.png")
        render(args.size).save(out)
        print(f"wrote {out} ({args.size}x{args.size})")
        return

    for size in DEFAULT_SIZES:
        out = os.path.join(here, f"pairedportals-icon-{size}.png")
        render(size).save(out)
        print(f"wrote {out} ({size}x{size}, {os.path.getsize(out)} bytes)")


if __name__ == "__main__":
    main()
