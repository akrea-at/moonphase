"""Launcher icon renders built on render_moon.render().

Writes the adaptive-icon foreground (432 x 432 px = the 108 dp canvas at 4 px/dp, transparent) and a
square full icon (the moon filling a transparent canvas) for store listings. The moon is MOON_DP across, the
72 dp visible area, and the adaptive background is transparent, so nothing shows around the moon.

The moon is a waxing crescent: phase angle PHASE (k = (1 + cos i) / 2 = 0.33), bright limb at CHI
(lit towards the lower right). The albedo is brightened by GAIN (linear light) so the crescent stands out
at launcher sizes, with EARTHSHINE set relative to that.
"""

import argparse
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import render_moon  # noqa: E402

PHASE = 110.0
CHI = 255.0
EARTHSHINE = 0.02
GAIN = 2.2
CANVAS_DP = 108.0
MOON_DP = 72.0


def moon(maps, size):
    render_moon.EARTHSHINE = EARTHSHINE
    return Image.fromarray(render_moon.render(maps, size, PHASE, CHI), "RGBA")


def placed_moon(maps, size, px_per_dp):
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    diameter = int(round(MOON_DP * px_per_dp)) + 2
    offset = (size - diameter) // 2
    canvas.alpha_composite(moon(maps, diameter), (offset, offset))
    return canvas


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--foreground", required=True, help="432 px adaptive foreground (.webp or .png)")
    ap.add_argument("--full", required=True, help="square full icon (.png)")
    ap.add_argument("--full-size", type=int, default=512)
    args = ap.parse_args()

    albedo, slope_e, slope_n = render_moon.load_maps(args.assets)
    maps = (albedo * GAIN ** (1.0 / render_moon.GAMMA), slope_e, slope_n)

    fg = placed_moon(maps, 432, 432 / CANVAS_DP)
    if args.foreground.endswith(".webp"):
        fg.save(args.foreground, lossless=True, method=6)
    else:
        fg.save(args.foreground, optimize=True)

    Path(args.full).parent.mkdir(parents=True, exist_ok=True)
    moon(maps, args.full_size).save(args.full, optimize=True)


if __name__ == "__main__":
    main()
