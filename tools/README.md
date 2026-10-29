# Asset tools

Scripts that generate the bundled assets. Raw downloads are not part of the repository; keep them
outside the project and pass their paths in.

## Moon textures

Download from the NASA SVS CGI Moon Kit (https://svs.gsfc.nasa.gov/4720):
`lroc_color_poles_4k.tif` and `ldem_16_uint.tif`.

```sh
python3 -I tools/prepare_moon_textures.py --color lroc_color_poles_4k.tif --dem ldem_16_uint.tif \
    --out app/src/main/assets
```

Writes `moon_albedo.jpg`, `moon_slope_e.jpg` and `moon_slope_n.jpg` (2048x1024 equirectangular;
conventions in the script docstring).

## Reference renderer

`render_moon.py` is the reference the Kotlin renderer is ported from; its docstring holds the exact
formula and constants.

```sh
python3 -I tools/render_moon.py --assets app/src/main/assets --out moon.png --size 800 --phase 60 --chi 270
```

`app/src/main/res/drawable-nodpi/widget_preview.webp` is this render at `--size 400 --phase 60 --chi 270`,
saved as WebP (quality 90).

## Launcher icon

```sh
python3 -I tools/render_icon.py --assets app/src/main/assets \
    --foreground app/src/main/res/drawable-nodpi/ic_launcher_foreground.webp --full tools/out/icon_512.png
```

Writes the adaptive-icon foreground (waxing crescent on the 108 dp canvas) and a 512 px square icon for
store listings. The background and monochrome layers are vectors in `res/drawable/`.

## Cities

Download `cities15000.zip` and `admin1CodesASCII.txt` from https://download.geonames.org/export/dump/.

```sh
python3 -I tools/prepare_cities.py --cities cities15000.zip --admin1 admin1CodesASCII.txt \
    --out app/src/main/assets/cities.tsv
```

## Attribution

- Moon imagery: NASA's Scientific Visualization Studio / LRO (LROC & LOLA teams)
- City data: GeoNames (geonames.org), CC BY 4.0
