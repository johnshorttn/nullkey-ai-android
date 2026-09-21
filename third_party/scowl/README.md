# SCOWL word list (bundled spelling dictionary)

NullKey ships a subset of [SCOWL](http://wordlist.aspell.net/) 2020.12.07 for on-device spelling. Nothing is downloaded at runtime.

Included, in frequency-class order (smaller class first, de-duplicated, lowercased):

- `english-words`, `american-words`, and `english-contractions` through size 35 (SCOWL “small”)
- later `english-contractions` files (40, 50, 60) so common forms such as “I’ll” and “we’ve” are recognized

The generated asset is `app/src/main/assets/spelling/en_words.txt`. The required copyright notice ships beside it as `SCOWL_COPYRIGHT.txt` and is copied here.

SCOWL size 50+ (medium and larger) is intentionally omitted so suggestions stay on common English and the asset stays under half a megabyte uncompressed.
