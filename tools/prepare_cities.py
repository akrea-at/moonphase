"""Builds app/src/main/assets/cities.tsv from GeoNames cities15000.zip and admin1CodesASCII.txt.

Output: UTF-8, no header, one city per line, sorted by population descending, tab-separated:
  0 name         GeoNames display name, e.g. "Vienna"
  1 asciiname    ASCII form; empty when identical to name
  2 alternates   comma-separated Latin-script alternate names (e.g. "Wien"), only for cities with
                 population >= 100000; at most 10 (30 for >= 1M, 20 for >= 500k) of <= 20 chars, shortest first,
                 deduplicated case- and accent-insensitively against each other and the name
  3 admin1       first-level region name, e.g. "Vienna" or "Bavaria"; may be empty
  4 country      ISO 3166-1 alpha-2 code, e.g. "AT"
  5 lat          decimal degrees, 4 decimals
  6 lon          decimal degrees, 4 decimals
  7 timezone     IANA zone id, e.g. "Europe/Vienna"
  8 population   integer
Fields never contain tabs or commas. Searching should compare case- and accent-folded strings
against name, asciiname and every alternate.

Data: GeoNames (geonames.org), CC BY 4.0.
"""

import argparse
import io
import unicodedata
import zipfile
from pathlib import Path

ALT_MIN_POPULATION = 100_000


def is_latin(s):
    for ch in s:
        if ch in " -'.’":
            continue
        cp = ord(ch)
        if not (ch.isalpha() and (cp < 0x250 or 0x1E00 <= cp <= 0x1EFF)):
            return False
    return True


def fold(s):
    return "".join(c for c in unicodedata.normalize("NFKD", s.casefold()) if not unicodedata.combining(c))


def clean(s):
    return " ".join(s.replace("\t", " ").replace(",", " ").split())


def alt_limit(population):
    if population >= 1_000_000:
        return 30
    if population >= 500_000:
        return 20
    return 10


def pick_alternates(raw, name, ascii_name, population):
    if population < ALT_MIN_POPULATION or not raw:
        return []
    seen = {fold(name), fold(ascii_name)}
    candidates = []
    for alt in raw.split(","):
        alt = clean(alt)
        if not alt or len(alt) > 20 or not is_latin(alt):
            continue
        if alt.islower() or (alt.isupper() and len(alt) <= 4) or "Lungsod" in alt:
            continue
        candidates.append(alt)
    picked = []
    for alt in sorted(set(candidates), key=lambda a: (len(a), a)):
        key = fold(alt)
        if key in seen:
            continue
        seen.add(key)
        picked.append(alt)
        if len(picked) >= alt_limit(population):
            break
    return picked


def load_admin1(path):
    names = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) >= 2:
            names[parts[0]] = clean(parts[1])
    return names


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cities", required=True, help="cities15000.zip")
    ap.add_argument("--admin1", required=True, help="admin1CodesASCII.txt")
    ap.add_argument("--out", required=True, help="output cities.tsv")
    args = ap.parse_args()

    admin1 = load_admin1(args.admin1)
    with zipfile.ZipFile(args.cities) as z:
        text = io.TextIOWrapper(z.open("cities15000.txt"), encoding="utf-8").read()

    rows = []
    for line in text.splitlines():
        f = line.split("\t")
        if len(f) < 19:
            continue
        name, ascii_name = clean(f[1]), clean(f[2])
        population = int(f[14] or 0)
        alternates = pick_alternates(f[3], name, ascii_name, population)
        rows.append((
            population,
            "\t".join([
                name,
                "" if ascii_name == name else ascii_name,
                ",".join(alternates),
                admin1.get(f"{f[8]}.{f[10]}", ""),
                f[8],
                f"{float(f[4]):.4f}",
                f"{float(f[5]):.4f}",
                f[17],
                str(population),
            ]),
        ))
    rows.sort(key=lambda r: -r[0])

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("".join(r[1] + "\n" for r in rows), encoding="utf-8")
    print(f"{len(rows)} cities, {out.stat().st_size} bytes")


if __name__ == "__main__":
    main()
