"""Builds the moon texture assets from the NASA SVS CGI Moon Kit (https://svs.gsfc.nasa.gov/4720).

Inputs:  lroc_color_poles_4k.tif (LROC colour map, 4096x2048, 8-bit RGB)
         ldem_16_uint.tif        (LOLA heights, 5760x2880, uint16 half-metres offset by +20000)

Outputs (all equirectangular 2048x1024, greyscale JPEG):
  moon_albedo.jpg   albedo (sRGB-encoded luminance), white point stretched so the 99.7th percentile is 255
  moon_slope_e.jpg  east slope  dh/dx_east  (dimensionless), v = clamp(round(128 + slope*K), 0, 255)
  moon_slope_n.jpg  north slope dh/dy_north (dimensionless), same encoding

Map convention (same for every output, matching the source maps):
  pixel (x, y) has its centre at lon = -180 + (x + 0.5) * 360 / W,  lat = 90 - (y + 0.5) * 180 / H
  so the middle column is selenographic longitude 0 (centre of the near side), longitude grows
  eastwards to the right (Mare Crisium, 17N 59E, sits right of centre) and row 0 is the north pole.

Slopes are computed with central differences on the full-resolution DEM using metric spacing
(east spacing = 1737.4 km * cos(lat) * dlon) after a 3x3 box blur, area-averaged down to the
output size, mixed 50/50 with a further 3x3 box blur to tame JPEG noise and attenuated to zero
between |lat| = 75 and 90 degrees. K = 400, so the 8-bit range covers |slope| <= 0.32 (about
0.1 % of pixels clip); decode with slope = (v - 128) / 400.
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

W, H = 2048, 1024
MOON_RADIUS_M = 1737400.0
SLOPE_K = 400.0
POLE_FADE_START_DEG = 75.0


def resize_float(a, w, h):
    return np.asarray(Image.fromarray(a.astype(np.float32), mode="F").resize((w, h), Image.BOX), dtype=np.float64)


def box_blur(a, r=1):
    pad = np.pad(a, ((r, r), (0, 0)), mode="edge")
    pad = np.pad(pad, ((0, 0), (r, r)), mode="wrap")
    out = np.zeros_like(a)
    n = 2 * r + 1
    for dy in range(n):
        for dx in range(n):
            out += pad[dy:dy + a.shape[0], dx:dx + a.shape[1]]
    return out / (n * n)


def lat_of_rows(h):
    return np.radians(90.0 - (np.arange(h) + 0.5) * 180.0 / h)


def build_albedo(path):
    rgb = np.asarray(Image.open(path).convert("RGB"), dtype=np.float64)
    grey = 0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]
    grey = resize_float(grey, W, H)
    grey = grey / np.percentile(grey, 99.7) * 255.0
    return np.clip(np.round(grey), 0, 255).astype(np.uint8)


def build_slopes(path):
    raw = np.asarray(Image.open(path), dtype=np.float64)
    h_m = (raw - 20000.0) / 2.0
    rows, cols = h_m.shape
    dlon = 2.0 * np.pi / cols
    dlat = np.pi / rows
    cos_lat = np.maximum(np.cos(lat_of_rows(rows)), 1e-3)[:, None]

    h_m = box_blur(h_m, 1)
    dh_e = (np.roll(h_m, -1, axis=1) - np.roll(h_m, 1, axis=1)) / (2.0 * MOON_RADIUS_M * cos_lat * dlon)
    north = np.vstack([h_m[:1], h_m[:-1]])
    south = np.vstack([h_m[1:], h_m[-1:]])
    dh_n = (north - south) / (2.0 * MOON_RADIUS_M * dlat)

    se = resize_float(dh_e, W, H)
    sn = resize_float(dh_n, W, H)
    se = 0.5 * se + 0.5 * box_blur(se, 1)
    sn = 0.5 * sn + 0.5 * box_blur(sn, 1)
    lat_deg = np.degrees(np.abs(lat_of_rows(H)))[:, None]
    fade = np.clip((90.0 - lat_deg) / (90.0 - POLE_FADE_START_DEG), 0.0, 1.0)
    return se * fade, sn * fade


def encode_slope(s):
    return np.clip(np.round(128.0 + s * SLOPE_K), 0, 255).astype(np.uint8)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--color", required=True, help="lroc_color_poles_4k.tif")
    ap.add_argument("--dem", required=True, help="ldem_16_uint.tif")
    ap.add_argument("--out", required=True, help="assets directory")
    ap.add_argument("--albedo-quality", type=int, default=88)
    ap.add_argument("--slope-quality", type=int, default=82)
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    albedo = build_albedo(args.color)
    Image.fromarray(albedo, mode="L").save(out / "moon_albedo.jpg", quality=args.albedo_quality, optimize=True)

    se, sn = build_slopes(args.dem)
    for name, s in (("e", se), ("n", sn)):
        p = np.percentile(np.abs(s), [50, 99, 99.9])
        clipped = np.mean(np.abs(s) * SLOPE_K > 127) * 100
        print(f"slope_{name}: |s| p50={p[0]:.4f} p99={p[1]:.4f} p99.9={p[2]:.4f} clipped={clipped:.3f}%")
        Image.fromarray(encode_slope(s), mode="L").save(out / f"moon_slope_{name}.jpg", quality=args.slope_quality, optimize=True)

    for f in ("moon_albedo.jpg", "moon_slope_e.jpg", "moon_slope_n.jpg"):
        print(f, (out / f).stat().st_size, "bytes")


if __name__ == "__main__":
    main()
