#!/usr/bin/env python3
"""Build CNflick Japanese lexicon TSV from SKK-JISYO.L.

Output format:
reading<TAB>cand1,cand2,...
"""

from __future__ import annotations

import argparse
import re
from collections import defaultdict
from pathlib import Path

PLAIN_READING = re.compile(r"^[ぁ-ゖー]+$")
OKURI_READING = re.compile(r"^([ぁ-ゖー]+)([A-Za-z])$")

# consonant marker -> base ending kana
ENDING = {
    "r": "る",
    "t": "つ",
    "n": "ぬ",
    "m": "む",
    "b": "ぶ",
    "k": "く",
    "g": "ぐ",
    "s": "す",
    "u": "う",
    "w": "う",
}

I_ROW = {
    "る": "り",
    "つ": "ち",
    "ぬ": "に",
    "む": "み",
    "ぶ": "び",
    "く": "き",
    "ぐ": "ぎ",
    "す": "し",
    "う": "い",
}

E_ROW = {
    "る": "れ",
    "つ": "て",
    "ぬ": "ね",
    "む": "め",
    "ぶ": "べ",
    "く": "け",
    "ぐ": "げ",
    "す": "せ",
    "う": "え",
}

A_ROW = {
    "る": "ら",
    "つ": "た",
    "ぬ": "な",
    "む": "ま",
    "ぶ": "ば",
    "く": "か",
    "ぐ": "が",
    "す": "さ",
    "う": "わ",
}

TE_TA = {
    "る": ("って", "った"),
    "つ": ("って", "った"),
    "う": ("って", "った"),
    "ぬ": ("んで", "んだ"),
    "ぶ": ("んで", "んだ"),
    "む": ("んで", "んだ"),
    "く": ("いて", "いた"),
    "ぐ": ("いで", "いだ"),
    "す": ("して", "した"),
}


def parse_candidates(raw: str) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for item in raw.split("/"):
        c = item.split(";", 1)[0].strip()
        if not c or c in seen:
            continue
        seen.add(c)
        out.append(c)
    return out


def add_entry(bucket: dict[str, list[str]], reading: str, cand: str, limit: int) -> None:
    if not reading or not cand:
        return
    vals = bucket[reading]
    if cand in vals:
        return
    if len(vals) < limit:
        vals.append(cand)


def expand_sahen(base: str, cand: str) -> list[tuple[str, str]]:
    forms = [
        (base + "する", cand + "する"),
        (base + "し", cand + "し"),
        (base + "して", cand + "して"),
        (base + "した", cand + "した"),
        (base + "せ", cand + "せ"),
        (base + "せる", cand + "せる"),
        (base + "さ", cand + "さ"),
        (base + "され", cand + "され"),
    ]
    return forms


def expand_godan(base: str, marker: str, cand: str) -> list[tuple[str, str]]:
    ending = ENDING.get(marker)
    if ending is None:
        return []

    i = I_ROW[ending]
    e = E_ROW[ending]
    a = A_ROW[ending]
    te, ta = TE_TA[ending]

    forms = [
        (base + ending, cand + ending),
        (base + i, cand + i),
        (base + e, cand + e),
        (base + a, cand + a),
        (base + te, cand + te),
        (base + ta, cand + ta),
        (base + a + "ない", cand + a + "ない"),
    ]
    return forms


def build(input_path: Path, output_path: Path, per_reading_limit: int) -> None:
    text = input_path.read_bytes().decode("euc_jp", errors="ignore")
    bucket: dict[str, list[str]] = defaultdict(list)

    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(";"):
            continue
        if " /" not in line:
            continue

        key, rest = line.split(" ", 1)
        cands = parse_candidates(rest)
        if not cands:
            continue

        if PLAIN_READING.fullmatch(key):
            for c in cands:
                add_entry(bucket, key, c, per_reading_limit)
            continue

        m = OKURI_READING.fullmatch(key)
        if not m:
            continue

        base, marker = m.group(1), m.group(2).lower()
        for c in cands:
            if marker == "s":
                for reading, word in expand_sahen(base, c):
                    add_entry(bucket, reading, word, per_reading_limit)
            else:
                for reading, word in expand_godan(base, marker, c):
                    add_entry(bucket, reading, word, per_reading_limit)

    with output_path.open("w", encoding="utf-8") as f:
        f.write("# generated from SKK-JISYO.L by tools/build_japanese_lexicon_from_skk.py\n")
        for reading in sorted(bucket.keys()):
            vals = bucket[reading]
            if not vals:
                continue
            f.write(f"{reading}\t{','.join(vals)}\n")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--input", required=True, help="path to SKK-JISYO.L")
    p.add_argument("--output", required=True, help="output tsv path")
    p.add_argument("--limit", type=int, default=10, help="max candidates per reading")
    args = p.parse_args()

    build(Path(args.input), Path(args.output), args.limit)


if __name__ == "__main__":
    main()
