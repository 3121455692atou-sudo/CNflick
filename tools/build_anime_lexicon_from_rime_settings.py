#!/usr/bin/env python3
import argparse
import re
from pathlib import Path

from opencc import OpenCC
from pypinyin import Style, lazy_pinyin

HANZI = re.compile(r"[\u3400-\u9fff]")


def normalize_pinyin(text: str) -> str:
    py = "".join(lazy_pinyin(text, style=Style.NORMAL, strict=False, errors="ignore"))
    py = py.replace("u:", "v").replace("ü", "v")
    py = re.sub(r"[^a-zv]", "", py)
    return py


def parse_rime_plain_word_list(src: Path) -> list[str]:
    lines = src.read_text(encoding="utf-8", errors="ignore").splitlines()
    started = False
    words: list[str] = []
    for raw in lines:
        line = raw.strip()
        if line == "...":
            started = True
            continue
        if not started or not line or line.startswith("#"):
            continue
        word = line.split("\t", 1)[0].strip()
        if not word:
            continue
        if not HANZI.search(word):
            continue
        words.append(word)
    return words


def main():
    parser = argparse.ArgumentParser(description="Build anime character/ACG lexicon (pinyin -> Chinese) from open-source Rime anime dictionary")
    parser.add_argument("--source", required=True, help="Path to open-source anime dict yaml, e.g. luna_pinyin.anime.dict.yaml")
    parser.add_argument("--output", default="app/src/main/assets/lexicons/anime_character_cn.tsv")
    args = parser.parse_args()

    src = Path(args.source)
    out = Path(args.output)

    cc = OpenCC("t2s")
    words = parse_rime_plain_word_list(src)

    seen_words = set()
    rows: list[tuple[str, str]] = []
    for w in words:
        simp = re.sub(r"\s+", "", cc.convert(w))
        if not simp or simp in seen_words:
            continue
        seen_words.add(simp)
        py = normalize_pinyin(simp)
        if not py:
            continue
        rows.append((py, simp))

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(f"{py}\t{hanzi}" for py, hanzi in rows) + "\n", encoding="utf-8")
    print(f"Built {out} with {len(rows)} entries")


if __name__ == "__main__":
    main()
