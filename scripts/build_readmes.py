#!/usr/bin/env python3
"""Builds README.zh-CN.md, README.ja.md and README.ko.md from docs/readme/<lang>.md.

The translations carry <<CODE n>> and <<TABLE n>> placeholders; they are filled with the code
blocks and tables of README.md, so every language shows the same code (R-DOCS-1).

    python3 scripts/build_readmes.py          write the translations
    python3 scripts/build_readmes.py --check  fail when one is stale
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def blocks(text):
    lines = text.split("\n")
    code, tables, i = [], [], 0
    while i < len(lines):
        if lines[i].startswith("```"):
            j = i + 1
            while not lines[j].startswith("```"):
                j += 1
            code.append("\n".join(lines[i:j + 1]))
            i = j + 1
        elif lines[i].startswith("|"):
            j = i
            while j < len(lines) and lines[j].startswith("|"):
                j += 1
            tables.append("\n".join(lines[i:j]))
            i = j
        else:
            i += 1
    return code, tables


def main():
    code, tables = blocks(open(os.path.join(ROOT, "README.md"), encoding="utf-8").read())
    stale = []
    for lang in ("zh-CN", "ja", "ko"):
        src = open(os.path.join(ROOT, "docs", "readme", lang + ".md"), encoding="utf-8").read()
        for n, c in enumerate(code):
            src = src.replace("<<CODE %d>>" % n, c)
        for n, t in enumerate(tables):
            src = src.replace("<<TABLE %d>>" % n, t)
        if "<<" in src:
            raise SystemExit("unfilled placeholder in " + lang)
        out = os.path.join(ROOT, "README.%s.md" % lang)
        if "--check" in sys.argv:
            if not os.path.exists(out) or open(out, encoding="utf-8").read() != src:
                stale.append(out)
        else:
            open(out, "w", encoding="utf-8").write(src)
    if stale:
        raise SystemExit("stale: " + ", ".join(stale) + " (run python3 scripts/build_readmes.py)")


if __name__ == "__main__":
    main()
