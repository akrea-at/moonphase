"""Builds app/src/main/assets/cities.tsv from GeoNames cities15000.zip, admin1CodesASCII.txt and
alternateNamesV2.zip.

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
  9 name_en      English name, e.g. "Cologne"; empty when identical to name
 10 name_de      German name, e.g. "Wien"; empty when identical to name
 11 region_en    English region name; empty when identical to admin1
 12 region_de    German region name, e.g. "Bayern"; empty when identical to admin1
Trailing empty columns are omitted, so a line may have 9 to 13 fields. Localised names come from
alternateNamesV2, ignoring colloquial and historic names and names limited to a period. Cities: the
preferred name, else the first plain one, never a short name ("Pölten" for St. Pölten). Regions: short
names first ("Wien", "Bayern" rather than "Bundesland Wien", "Free State of Bavaria"), then preferred,
then plain. GeoNames names are already English apart from local spellings, so an
English name only replaces a name with non-ASCII letters (Köln, Zürich) and must itself be ASCII.
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
LANGUAGES = (b"en", b"de")


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
        if len(parts) >= 4:
            names[parts[0]] = (clean(parts[1]), int(parts[3]))
    return names


def load_localised(path, city_ids, region_ids):
    best = {}
    with zipfile.ZipFile(path) as z, z.open("alternateNamesV2.txt") as raw:
        for line in raw:
            head = line.split(b"\t", 3)
            if len(head) < 4 or head[2] not in LANGUAGES:
                continue
            gid = int(head[1])
            region = gid in region_ids
            if not region and gid not in city_ids:
                continue
            f = line.decode("utf-8").rstrip("\r\n").split("\t")
            f += [""] * (10 - len(f))
            preferred, short = f[4] == "1", f[5] == "1"
            if "1" in (f[6], f[7]) or f[8] or f[9] or (short and not region):
                continue
            name = clean(f[3])
            if not name or not is_latin(name):
                continue
            rank = (not short, not preferred)
            key = (gid, f[2])
            if key not in best or rank < best[key][0]:
                best[key] = (rank, name)
    return {key: name for key, (_, name) in best.items()}


def localised(names, gid, base):
    en, de = names.get((gid, "en"), base), names.get((gid, "de"), base)
    if base.isascii() or not en.isascii():
        en = base
    return ["" if en == base else en, "" if de == base else de]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cities", required=True, help="cities15000.zip")
    ap.add_argument("--admin1", required=True, help="admin1CodesASCII.txt")
    ap.add_argument("--alternates", required=True, help="alternateNamesV2.zip")
    ap.add_argument("--out", required=True, help="output cities.tsv")
    args = ap.parse_args()

    admin1 = load_admin1(args.admin1)
    with zipfile.ZipFile(args.cities) as z:
        text = io.TextIOWrapper(z.open("cities15000.txt"), encoding="utf-8").read()

    records = [f for f in (line.split("\t") for line in text.splitlines()) if len(f) >= 19]
    names = load_localised(args.alternates, {int(f[0]) for f in records}, {gid for _, gid in admin1.values()})

    rows = []
    for f in records:
        name, ascii_name = clean(f[1]), clean(f[2])
        population = int(f[14] or 0)
        alternates = pick_alternates(f[3], name, ascii_name, population)
        region, region_id = admin1.get(f"{f[8]}.{f[10]}", ("", None))
        columns = [
            name,
            "" if ascii_name == name else ascii_name,
            ",".join(alternates),
            region,
            f[8],
            f"{float(f[4]):.4f}",
            f"{float(f[5]):.4f}",
            f[17],
            str(population),
            *localised(names, int(f[0]), name),
            *(localised(names, region_id, region) if region_id else ["", ""]),
        ]
        while len(columns) > 9 and not columns[-1]:
            columns.pop()
        rows.append((population, "\t".join(columns)))
    rows.sort(key=lambda r: -r[0])

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("".join(r[1] + "\n" for r in rows), encoding="utf-8")
    print(f"{len(rows)} cities, {out.stat().st_size} bytes")


if __name__ == "__main__":
    main()
