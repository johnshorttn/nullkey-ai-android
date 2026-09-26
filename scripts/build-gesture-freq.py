#!/usr/bin/env python3
"""Build app/src/main/assets/gesture/en_top_n.txt from public-domain English text.

Counts word frequencies in Project Gutenberg plain texts (downloaded once into
third_party/gesture_freq/corpus/ when network is available), intersects with
letter-keys (len>=2, a-z) from the bundled SCOWL spelling list, and writes
the top-N word<TAB>weight TSV (descending weight). Runtime never downloads.

Usage:
  python3 scripts/build-gesture-freq.py [--n 10000] [--spelling PATH] [--out PATH]
  python3 scripts/build-gesture-freq.py --offline
      # Prefer committed third_party/gesture_freq/corpus_counts.tsv; else Zipf/SCOWL
  python3 scripts/build-gesture-freq.py --from-counts PATH
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SPELLING = ROOT / "app/src/main/assets/spelling/en_words.txt"
DEFAULT_OUT = ROOT / "app/src/main/assets/gesture/en_top_n.txt"
CORPUS_DIR = ROOT / "third_party/gesture_freq/corpus"
COUNTS_TSV = ROOT / "third_party/gesture_freq/corpus_counts.tsv"

# Small Gutenberg UTF-8 plain texts (US public domain). Fetched only by this
# script; raw books are discarded after counting unless --keep-corpus is set.
GUTENBERG_SOURCES = (
    # Alice's Adventures in Wonderland — Lewis Carroll
    ("pg11.txt", "https://www.gutenberg.org/files/11/11-0.txt"),
    # The Adventures of Sherlock Holmes — Arthur Conan Doyle
    ("pg1661.txt", "https://www.gutenberg.org/files/1661/1661-0.txt"),
    # Pride and Prejudice — Jane Austen
    ("pg1342.txt", "https://www.gutenberg.org/files/1342/1342-0.txt"),
    # Frankenstein — Mary Shelley
    ("pg84.txt", "https://www.gutenberg.org/files/84/84-0.txt"),
    # A Christmas Carol — Charles Dickens
    ("pg46.txt", "https://www.gutenberg.org/files/46/46-0.txt"),
    # The Time Machine — H. G. Wells
    ("pg35.txt", "https://www.gutenberg.org/files/35/35-0.txt"),
)

TOKEN_RE = re.compile(r"[a-z]+(?:'[a-z]+)?", re.IGNORECASE)

GUTENBERG_NOTE = (
    "Weights are raw occurrence counts from Project Gutenberg plain texts "
    f"({', '.join(name for name, _ in GUTENBERG_SOURCES)}); US public domain. "
    "See third_party/gesture_freq/README.md."
)


def relpath(path: Path) -> str:
    resolved = path.expanduser().resolve()
    try:
        return str(resolved.relative_to(ROOT))
    except ValueError:
        return str(path)


def letter_key(word: str) -> str | None:
    letters = "".join(c for c in word.lower() if "a" <= c <= "z")
    if len(letters) < 2:
        return None
    return letters


def load_spelling_keys(path: Path) -> list[str]:
    """Letter-keys from SCOWL asset, preserving first-seen (size-class) order."""
    keys: list[str] = []
    seen: set[str] = set()
    with path.open(encoding="utf-8") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            key = letter_key(line)
            if key is None or key in seen:
                continue
            seen.add(key)
            keys.append(key)
    return keys


def download_corpus() -> list[Path]:
    CORPUS_DIR.mkdir(parents=True, exist_ok=True)
    paths: list[Path] = []
    for name, url in GUTENBERG_SOURCES:
        dest = CORPUS_DIR / name
        if dest.exists() and dest.stat().st_size > 1000:
            print(f"  reuse {relpath(dest)}", file=sys.stderr)
            paths.append(dest)
            continue
        print(f"  download {url}", file=sys.stderr)
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "NullKeyGestureFreqBuilder/1.0 (offline asset regen)"},
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = resp.read()
        dest.write_bytes(data)
        paths.append(dest)
    return paths


def strip_gutenberg_boilerplate(text: str) -> str:
    start = re.search(r"\*\*\*\s*START OF .+?\*\*\*", text, re.IGNORECASE)
    end = re.search(r"\*\*\*\s*END OF .+?\*\*\*", text, re.IGNORECASE)
    if start and end and end.start() > start.end():
        return text[start.end() : end.start()]
    return text


def count_corpus(paths: list[Path]) -> Counter[str]:
    counts: Counter[str] = Counter()
    for path in paths:
        text = strip_gutenberg_boilerplate(
            path.read_text(encoding="utf-8", errors="replace")
        )
        for match in TOKEN_RE.finditer(text):
            key = letter_key(match.group(0))
            if key is not None:
                counts[key] += 1
    return counts


def zipf_weights(keys: list[str]) -> Counter[str]:
    """Deterministic synthetic Zipf over SCOWL size-class order (offline fallback)."""
    weights: Counter[str] = Counter()
    for i, key in enumerate(keys):
        weights[key] = max(1, int(round(1_000_000 / ((i + 1) ** 0.9))))
    return weights


def load_counts_tsv(path: Path) -> Counter[str]:
    counts: Counter[str] = Counter()
    with path.open(encoding="utf-8") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 2:
                continue
            word, weight_s = parts
            try:
                counts[word] = int(weight_s)
            except ValueError:
                continue
    return counts


def write_counts_tsv(counts: Counter[str], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rows = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
    with path.open("w", encoding="utf-8") as fh:
        fh.write(
            "# word<TAB>count — self-count of Gutenberg PD texts; not CC-BY-SA wordfreq\n"
        )
        fh.write(f"# Sources: {', '.join(name for name, _ in GUTENBERG_SOURCES)}\n")
        for word, count in rows:
            fh.write(f"{word}\t{count}\n")


def write_top_n(
    spelling_keys: list[str],
    counts: Counter[str],
    out: Path,
    n: int,
    source_note: str,
) -> int:
    scored: list[tuple[str, int]] = []
    for key in spelling_keys:
        w = int(counts.get(key, 0))
        if w <= 0:
            continue
        scored.append((key, w))
    scored.sort(key=lambda kv: (-kv[1], kv[0]))
    top = scored[:n]
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as fh:
        fh.write(
            f"# NullKey gesture frequency top-{len(top)} (word<TAB>weight, descending).\n"
            f"# {source_note}\n"
            f"# Intersected with letter-keys (len>=2, a-z) from spelling/en_words.txt.\n"
            f"# Regenerate: python3 scripts/build-gesture-freq.py\n"
            f"# Runtime never downloads. See gesture/NOTICE.txt.\n"
        )
        for word, weight in top:
            fh.write(f"{word}\t{weight}\n")
    return len(top)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--n", type=int, default=10000, help="top-N words (default 10000)")
    parser.add_argument("--spelling", type=Path, default=DEFAULT_SPELLING)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Skip downloads; use corpus_counts.tsv if present, else Zipf/SCOWL",
    )
    parser.add_argument(
        "--keep-corpus",
        action="store_true",
        help="Keep downloaded Gutenberg txt under third_party/gesture_freq/corpus/",
    )
    parser.add_argument(
        "--from-counts",
        type=Path,
        default=None,
        help="Use an existing corpus_counts.tsv instead of downloading",
    )
    args = parser.parse_args()

    spelling_path = args.spelling.expanduser()
    if not spelling_path.is_file():
        print(f"error: spelling list not found: {spelling_path}", file=sys.stderr)
        return 1

    spelling_keys = load_spelling_keys(spelling_path)
    print(f"spelling letter-keys: {len(spelling_keys)}", file=sys.stderr)

    source_note: str
    counts: Counter[str]

    if args.from_counts is not None:
        counts_path = args.from_counts.expanduser().resolve()
        counts = load_counts_tsv(counts_path)
        source_note = (
            f"{GUTENBERG_NOTE} Regenerated from {relpath(counts_path)}."
        )
        print(f"loaded counts: {len(counts)}", file=sys.stderr)
    elif args.offline:
        if COUNTS_TSV.is_file():
            counts = load_counts_tsv(COUNTS_TSV)
            source_note = (
                f"{GUTENBERG_NOTE} Regenerated offline from {relpath(COUNTS_TSV)}."
            )
            print(f"offline committed counts: {len(counts)}", file=sys.stderr)
        else:
            counts = zipf_weights(spelling_keys)
            source_note = (
                "Weights are a deterministic Zipf over SCOWL size-class order "
                "(offline fallback; not corpus self-count)."
            )
            print("offline Zipf weights over SCOWL order", file=sys.stderr)
    else:
        try:
            paths = download_corpus()
            counts = count_corpus(paths)
            write_counts_tsv(counts, COUNTS_TSV)
            source_note = GUTENBERG_NOTE
            print(f"corpus unique keys: {len(counts)}", file=sys.stderr)
            if not args.keep_corpus:
                for path in paths:
                    try:
                        path.unlink()
                    except OSError:
                        pass
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            if COUNTS_TSV.is_file():
                print(f"download failed ({exc}); using committed counts", file=sys.stderr)
                counts = load_counts_tsv(COUNTS_TSV)
                source_note = (
                    f"{GUTENBERG_NOTE} Regenerated from {relpath(COUNTS_TSV)} "
                    f"after download failure ({exc})."
                )
            else:
                print(f"download failed ({exc}); falling back to Zipf", file=sys.stderr)
                counts = zipf_weights(spelling_keys)
                source_note = (
                    "Weights are a deterministic Zipf over SCOWL size-class order "
                    f"(Gutenberg download failed: {exc})."
                )

    written = write_top_n(spelling_keys, counts, args.out, args.n, source_note)
    print(f"wrote {written} data rows -> {relpath(args.out)}", file=sys.stderr)

    # If the corpus intersection is far short of N, pad with Zipf/SCOWL so CI
    # still gets a full top-N (corpus weights always outrank the pad).
    if written < min(args.n, max(1000, len(spelling_keys) // 4)):
        print(
            f"warning: only {written} corpus-backed keys; blending Zipf for remainder",
            file=sys.stderr,
        )
        zipf = zipf_weights(spelling_keys)
        min_corpus = min((c for c in counts.values() if c > 0), default=1)
        scale = max(1, min_corpus) / max(zipf.values())
        blended: Counter[str] = Counter()
        for key in spelling_keys:
            if counts.get(key, 0) > 0:
                blended[key] = counts[key]
            else:
                blended[key] = max(1, int(zipf[key] * scale))
        source_note = (
            source_note
            + " Keys absent from the corpus use a scaled Zipf over SCOWL order "
            "so the top-N stays fully populated."
        )
        written = write_top_n(spelling_keys, blended, args.out, args.n, source_note)
        print(f"rewrote {written} data rows (blended) -> {relpath(args.out)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
