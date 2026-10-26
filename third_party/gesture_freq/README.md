# Gesture frequency corpus (build-time only)

NullKey ships a top-N English frequency table for future swipe ranking:

`app/src/main/assets/gesture/en_top_n.txt`

Runtime **never** downloads this data. There is no `INTERNET` permission.

## What is committed

| Path | Purpose |
| --- | --- |
| `corpus_counts.tsv` | Word → raw count from the public-domain sample |
| `corpus/.gitkeep` | Placeholder; raw Gutenberg `.txt` files are not committed |
| `../app/src/main/assets/gesture/en_top_n.txt` | Top-N (default 10 000) `word<TAB>weight`, descending |
| `../app/src/main/assets/gesture/NOTICE.txt` | License / provenance notice |

## Regenerate

From the repo root (network used only by this script, optional):

```bash
python3 scripts/build-gesture-freq.py
# or, offline from the committed TSV:
python3 scripts/build-gesture-freq.py --offline
# or explicitly:
python3 scripts/build-gesture-freq.py --from-counts third_party/gesture_freq/corpus_counts.tsv
```

Defaults: `--n 10000`, spelling list `app/src/main/assets/spelling/en_words.txt`.

The script:

1. Downloads a handful of Project Gutenberg UTF-8 plain texts into `corpus/` (skipped when `--offline` / `--from-counts`).
2. Counts letter-keys (a–z, length ≥ 2).
3. Writes `corpus_counts.tsv`, then intersects with the SCOWL spelling letter-keys and emits the top-N asset.
4. Deletes the raw `.txt` books unless `--keep-corpus` is set (keeps the tree small).

If Gutenberg is unreachable and no counts TSV exists, the script falls back to a deterministic Zipf over SCOWL size-class order so CI can still produce an asset.

## License constraints

- **Allowed:** self-counted Project Gutenberg (US PD) text; SCOWL intersection (see `third_party/scowl/`).
- **Not allowed as the committed data source:** wordfreq / FrequencyWords or other CC-BY-SA lists.

See `app/src/main/assets/gesture/NOTICE.txt`.
