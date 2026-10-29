"""Reference moon renderer; the Kotlin renderer is a direct port of render().

Frames
  Screen S: x right, y up, z towards the viewer; celestial north up, celestial east LEFT.
  Body   B: X towards selenographic (lon 90E, lat 0), Y towards the north pole, Z towards (0, 0).
         A point at (lon, lat) is p(lon, lat) = (cos lat sin lon, sin lat, cos lat cos lon).

Orientation (libration l, b and axis position angle P, all degrees; 0 for a plain view)
  z_S = p(l, b)                                    sub-Earth point
  y0  = normalise(Y_B - (Y_B . z_S) z_S)           lunar north projected on the sky
  x0  = y0 x z_S
  x_S = cos P x0 - sin P y0,  y_S = sin P x0 + cos P y0
  With l = b = P = 0 these are the identity: screen +x = lon +90, +y = lat +90, +z = (0, 0).

Sun direction from phase angle i (0 full, 180 new) and bright-limb position angle chi
(from celestial north through east; 270 = lit on the right):
  s_S = (-sin i sin chi, sin i cos chi, cos i),  s_B = s_x x_S + s_y y_S + s_z z_S

Small outputs first box-reduce the maps by a power of two F (see reduce_maps(): start at F = 1 and
double F while W / (2F) >= 2N); on Android this is BitmapFactory.Options.inSampleSize = F.

Per output pixel (px, py) of an N x N image, disc radius R = N / 2 - 1, centre c = N / 2:
  x = (px + 0.5 - c) / R,  y = (c - py - 0.5) / R,  r = sqrt(x^2 + y^2)
  alpha = clamp((1 - r) * R + 0.5, 0, 1); pixels with alpha = 0 stay fully transparent
  r_max = 1 - 0.5 / R; if r > r_max: x, y = x * r_max / r, y * r_max / r   (edge band)
  z = sqrt(max(0, 1 - x^2 - y^2))
  n = x x_S + y y_S + z z_S                        (body frame)
  lat = asin(n_Y), lon = atan2(n_X, n_Z)
  sample maps bilinearly (lon wraps, lat clamps) at u = (lon_deg + 180) / 360 * W - 0.5,
                                                    v = (90 - lat_deg) / 180 * H - 0.5
  a  = albedo / 255,  A = a ^ GAMMA                (to linear)
  se = (slope_e - 128) / 400,  sn = (slope_n - 128) / 400
  e  = (cos lon, 0, -sin lon),  u = (-sin lat sin lon, cos lat, -sin lat cos lon)
  k  = RELIEF * min(1, z / LIMB_FADE)              (keeps perturbed normals facing the viewer)
  n' = normalise(n - k se e - k sn u)
  mu0 = n' . s_B,  mu = max(n' . z_S, 0)
  f  = mu0 > 0 ? 2 mu0 / (mu0 + mu + 1e-6) : 0    (Lommel-Seeliger, 1 at full-moon centre)
  L_c = A * (f + EARTHSHINE * TINT_c)              per channel c in (r, g, b)
  T(L) = L                                          for L <= KNEE
       = KNEE + (1 - KNEE) (1 - exp(-(L - KNEE) / (1 - KNEE)))  otherwise
  out_c = round(255 * T(L_c) ^ (1 / GAMMA)), clamped to 0..255; alpha = round(255 * alpha)
  (straight, non-premultiplied alpha)

Constants
  GAMMA = 2.2, RELIEF = 1.6, LIMB_FADE = 0.6, EARTHSHINE = 0.04, TINT = (0.82, 0.92, 1.10),
  KNEE = 0.8, slope K = 400
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image

GAMMA = 2.2
RELIEF = 1.6
LIMB_FADE = 0.6
EARTHSHINE = 0.04
TINT = np.array([0.82, 0.92, 1.10])
KNEE = 0.8
SLOPE_K = 400.0


def load_maps(assets):
    assets = Path(assets)
    load = lambda f: np.asarray(Image.open(assets / f).convert("L"), dtype=np.float64)
    return load("moon_albedo.jpg"), load("moon_slope_e.jpg"), load("moon_slope_n.jpg")


def sphere_point(lon, lat):
    return np.array([np.cos(lat) * np.sin(lon), np.sin(lat), np.cos(lat) * np.cos(lon)])


def view_frame(l_deg, b_deg, p_deg):
    z = sphere_point(np.radians(l_deg), np.radians(b_deg))
    north = np.array([0.0, 1.0, 0.0])
    y0 = north - north.dot(z) * z
    y0 /= np.linalg.norm(y0)
    x0 = np.cross(y0, z)
    p = np.radians(p_deg)
    x = np.cos(p) * x0 - np.sin(p) * y0
    y = np.sin(p) * x0 + np.cos(p) * y0
    return x, y, z


def tone(x):
    return np.where(x <= KNEE, x, KNEE + (1.0 - KNEE) * (1.0 - np.exp(-(x - KNEE) / (1.0 - KNEE))))


def bilinear(m, u, v):
    h, w = m.shape
    x0 = np.floor(u).astype(int)
    y0 = np.floor(v).astype(int)
    fx = u - x0
    fy = v - y0
    xa, xb = x0 % w, (x0 + 1) % w
    ya, yb = np.clip(y0, 0, h - 1), np.clip(y0 + 1, 0, h - 1)
    top = m[ya, xa] * (1 - fx) + m[ya, xb] * fx
    bot = m[yb, xa] * (1 - fx) + m[yb, xb] * fx
    return top * (1 - fy) + bot * fy


def reduce_maps(maps, size):
    w = maps[0].shape[1]
    factor = 1
    while w / (factor * 2) >= 2 * size:
        factor *= 2
    if factor == 1:
        return maps
    h2, w2 = maps[0].shape[0] // factor, w // factor
    return tuple(np.asarray(Image.fromarray(m.astype(np.float32), "F").resize((w2, h2), Image.BOX), dtype=np.float64) for m in maps)


def render(maps, size, phase_deg, chi_deg, l_deg=0.0, b_deg=0.0, p_deg=0.0):
    albedo, slope_e, slope_n = reduce_maps(maps, size)
    h, w = albedo.shape
    xs, ys, zs = view_frame(l_deg, b_deg, p_deg)

    i, chi = np.radians(phase_deg), np.radians(chi_deg)
    s_scr = np.array([-np.sin(i) * np.sin(chi), np.sin(i) * np.cos(chi), np.cos(i)])
    s = s_scr[0] * xs + s_scr[1] * ys + s_scr[2] * zs

    radius = size / 2.0 - 1.0
    c = size / 2.0
    px, py = np.meshgrid(np.arange(size) + 0.5, np.arange(size) + 0.5)
    x = (px - c) / radius
    y = (c - py) / radius
    r = np.sqrt(x * x + y * y)
    alpha = np.clip((1.0 - r) * radius + 0.5, 0.0, 1.0)
    mask = alpha > 0
    x, y, r = x[mask], y[mask], r[mask]
    r_max = 1.0 - 0.5 / radius
    scale = np.where(r > r_max, r_max / np.maximum(r, 1e-9), 1.0)
    x, y = x * scale, y * scale
    z = np.sqrt(np.maximum(0.0, 1.0 - x * x - y * y))

    n = np.outer(x, xs) + np.outer(y, ys) + np.outer(z, zs)
    lat = np.arcsin(np.clip(n[:, 1], -1.0, 1.0))
    lon = np.arctan2(n[:, 0], n[:, 2])

    u = (np.degrees(lon) + 180.0) / 360.0 * w - 0.5
    v = (90.0 - np.degrees(lat)) / 180.0 * h - 0.5
    a = bilinear(albedo, u, v) / 255.0
    lin = a ** GAMMA
    se = (bilinear(slope_e, u, v) - 128.0) / SLOPE_K
    sn = (bilinear(slope_n, u, v) - 128.0) / SLOPE_K

    sl, cl = np.sin(lon), np.cos(lon)
    st, ct = np.sin(lat), np.cos(lat)
    east = np.stack([cl, np.zeros_like(cl), -sl], axis=1)
    northv = np.stack([-st * sl, ct, -st * cl], axis=1)
    relief = RELIEF * np.minimum(1.0, z / LIMB_FADE)
    nn = n - (relief * se)[:, None] * east - (relief * sn)[:, None] * northv
    nn /= np.linalg.norm(nn, axis=1)[:, None]

    mu0 = nn @ s
    mu = np.maximum(nn @ zs, 0.0)
    f = np.where(mu0 > 0, 2.0 * mu0 / (mu0 + mu + 1e-6), 0.0)

    light = lin[:, None] * (f[:, None] + EARTHSHINE * TINT[None, :])
    rgb = tone(light) ** (1.0 / GAMMA)

    out = np.zeros((size, size, 4), dtype=np.uint8)
    out[mask, :3] = np.clip(np.round(rgb * 255.0), 0, 255).astype(np.uint8)
    out[..., 3] = np.round(alpha * 255.0).astype(np.uint8)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--assets", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=800)
    ap.add_argument("--phase", type=float, required=True, help="phase angle i in degrees, 0 = full")
    ap.add_argument("--chi", type=float, default=270.0, help="bright-limb position angle in degrees")
    ap.add_argument("--l", type=float, default=0.0)
    ap.add_argument("--b", type=float, default=0.0)
    ap.add_argument("--p", type=float, default=0.0)
    ap.add_argument("--background", default=None, help="hex colour to flatten onto, e.g. 101820")
    args = ap.parse_args()

    img = Image.fromarray(render(load_maps(args.assets), args.size, args.phase, args.chi, args.l, args.b, args.p), "RGBA")
    if args.background:
        bg = Image.new("RGBA", img.size, "#" + args.background)
        img = Image.alpha_composite(bg, img)
    img.save(args.out, optimize=True)


if __name__ == "__main__":
    main()
