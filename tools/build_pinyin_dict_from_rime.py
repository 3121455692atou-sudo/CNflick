#!/usr/bin/env python3
import argparse
import re
import sqlite3
from collections import defaultdict
from pathlib import Path

# Keep source weighting strategy, but default to 0 when unknown files are added upstream.
SOURCE_BONUS = {
    "8105.dict.yaml": 300,
    "41448.dict.yaml": 80,
    "base.dict.yaml": 220,
    "ext.dict.yaml": 320,
    "tencent.dict.yaml": 420,
    "others.dict.yaml": 360,
    "corrections.dict.yaml": 500,
}

PINYIN_KEEP = re.compile(r"[a-zv]+")
HANZI_BLOCK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")


def normalize_pinyin(raw: str) -> str:
    raw = raw.strip().lower().replace("u:", "v").replace("ü", "v")
    tokens = PINYIN_KEEP.findall(raw)
    return "".join(tokens)


def has_hanzi(text: str) -> bool:
    return HANZI_BLOCK.search(text) is not None


def parse_weight(raw: str) -> int:
    try:
        return int(raw)
    except ValueError:
        return 1


def parse_line(line: str):
    cols = line.rstrip("\n").split("\t")
    if len(cols) < 2:
        return None
    text = cols[0].strip()
    if not text or text.startswith("#") or not has_hanzi(text):
        return None

    pinyin = normalize_pinyin(cols[1])
    if not pinyin:
        return None

    weight = parse_weight(cols[2]) if len(cols) >= 3 else 1
    return text, pinyin, weight


def discover_source_files(root: Path):
    cn_root = root / "cn_dicts"
    if not cn_root.exists():
        return []
    files = sorted(cn_root.rglob("*.dict.yaml"))
    # full, no trimming: include every dict file under cn_dicts
    return files


def iter_dict_rows(root: Path, verbose: bool = False):
    files = discover_source_files(root)
    if verbose:
        print(f"[build] discovered {len(files)} dict files under {root / 'cn_dicts'}")
    for path in files:
        bonus = SOURCE_BONUS.get(path.name, 0)
        if verbose:
            rel = path.relative_to(root)
            print(f"[build] reading {rel} (bonus={bonus})")
        in_body = False
        with path.open("r", encoding="utf-8") as f:
            for raw in f:
                line = raw.strip()
                if line == "...":
                    in_body = True
                    continue
                if not in_body:
                    continue
                if not line or line.startswith("#"):
                    continue
                parsed = parse_line(raw)
                if not parsed:
                    continue
                text, pinyin, weight = parsed
                freq = max(1, weight) + bonus
                yield pinyin, text, min(freq, 2_000_000_000)


def build_db(rows, output_db: Path):
    output_db.parent.mkdir(parents=True, exist_ok=True)
    if output_db.exists():
        output_db.unlink()

    agg = defaultdict(int)
    for pinyin, hanzi, freq in rows:
        key = (pinyin, hanzi)
        agg[key] = min(2_000_000_000, agg[key] + freq)

    conn = sqlite3.connect(str(output_db))
    try:
        cur = conn.cursor()
        cur.execute("CREATE TABLE dict (pinyin TEXT NOT NULL, hanzi TEXT NOT NULL, freq INTEGER NOT NULL DEFAULT 1)")
        cur.execute("CREATE INDEX idx_dict_pinyin_freq ON dict(pinyin, freq DESC)")
        cur.executemany(
            "INSERT INTO dict(pinyin, hanzi, freq) VALUES (?, ?, ?)",
            ((p, h, f) for (p, h), f in agg.items()),
        )
        conn.commit()
    finally:
        conn.close()

    return len(agg)


def main():
    parser = argparse.ArgumentParser(description="Build CNflick pinyin sqlite dict from rime-frost dictionaries")
    parser.add_argument("--rime-root", required=True, help="Path to rime-frost repository")
    parser.add_argument("--output-db", default="app/src/main/assets/pinyin_dict_v2.db")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    root = Path(args.rime_root)
    output = Path(args.output_db)
    count = build_db(iter_dict_rows(root, verbose=args.verbose), output)
    print(f"Built {output} with {count} entries")


if __name__ == "__main__":
    main()
