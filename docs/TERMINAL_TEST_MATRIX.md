# Terminal test matrix

How NullKey behaves in the apps it is checked in, and how to check it on a
device. The unit-testable part of this table lives in
`app/src/test/.../TerminalMatrixTest.kt`; the escape bytes are pinned in
`TerminalEscapesTest.kt`.

## Settings involved

| Setting | Default | What it does |
|---|---|---|
| Terminal mode in Termux, JuiceSSH, ConnectBot and raw fields | On | Terminal row on; no suggestions, learning or swipe; Ctrl+A allowed |
| Terminal key output | Key events | How Esc, Tab, arrows, Home/End, PgUp/PgDn, Ins/Del, F1–F12 and Ctrl/Alt/Meta chords are sent |
| Allow Ctrl+A in normal text fields | Off | Ctrl-A safety: blocks select-all in normal fields, in both output modes |
| Two-finger chording | On | Hold Ctrl/Alt/Meta with one finger and tap a key with another |

### Terminal key output

- **Key events (default)**: real Android key events with Ctrl/Alt/Meta/Shift
  meta state. Best for Termux, JuiceSSH and ConnectBot.
- **Escape sequences in terminal apps**: while terminal mode is on, keys are
  committed as xterm text (`ESC [ A` for Up, `0x03` for Ctrl+C). Use it if a
  terminal app drops some key events.
- **Escape sequences everywhere (web terminals)**: the same, in every app. For
  browser terminals such as the Nullink web terminal, where Chrome may not
  pass key events through. Switch back for normal typing: in a normal text
  box arrows would type `^[[A`-style junk instead of moving the cursor.

Plain letters, digits and punctuation are always typed as normal text. Keys
with no xterm byte (e.g. Ctrl+1, Ctrl+é) still go out as key events.

## Expected behaviour

| Target | Terminal mode | Ctrl+A | Key events (default) | Escapes in terminal apps | Escapes everywhere |
|---|---|---|---|---|---|
| Termux (`com.termux`) | On | Sent | Works | Works (sequences) | Works (sequences) |
| JuiceSSH (`com.sonelli.juicessh`) | On | Sent | Works | Works (sequences) | Works (sequences) |
| ConnectBot (`org.connectbot`) | On | Sent | Works | Works (sequences) | Works (sequences) |
| Nullink web terminal in Chrome | Off | Blocked unless opted in | Arrows/Esc may be dropped by the page | Same as default | Works (sequences) |
| Normal text box (Keep, Messages…) | Off | Blocked unless opted in | Arrows move the cursor, Ctrl+C/V/X/Z work | Same as default | Not for normal typing |

## Device checklist

Run each row with the default output first, then with the escape mode that
the table says should work. Record pass/fail and the app version.

1. **Termux**: `cat -v`, then press Esc, ↑, F1, F5, Ctrl+C, Alt+B, Home/End.
   Expected with key events: `^[`, `^[[A`, `^[OP`, `^[[15~`, `cat` exits on
   Ctrl+C. Expected with escapes: the same bytes. Run `tmux`, press Ctrl+A or
   Ctrl+B then `c`: a new window opens.
2. **JuiceSSH / ConnectBot**: connect to a host, run `cat -v` and repeat the
   keys from step 1. Open `vim`, press `i`, type, then Esc and `:q!`.
3. **Nullink web terminal in Chrome**: open the terminal page. With the
   default, try ↑ (shell history), Esc, Ctrl+C. Then switch Terminal key
   output to "Escape sequences everywhere" and repeat; ↑ should recall the
   last command and Ctrl+C should interrupt `sleep 30`.
4. **Normal text box**: with the default output, type a sentence, use ← →
   Home End to move, Ctrl+A should do nothing (Ctrl-A safety), Ctrl+C/Ctrl+V
   copy and paste. Suggestions and swipe still work.
5. **Two-finger chording**: in Termux hold Ctrl and tap C with another finger:
   `^C`. Release Ctrl: the next key types normally.
