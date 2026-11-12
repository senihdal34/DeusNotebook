#!/usr/bin/env python3
"""Checks the exported note of the long test page. Usage: verify_export.py <exported-folder>"""
import os, re, sys, subprocess, unicodedata, urllib.parse
from pathlib import Path

root = Path(sys.argv[1])
fails = []
def check(ok, msg):
    print(("PASS  " if ok else "FAIL  ") + msg)
    if not ok: fails.append(msg)

mds = sorted(root.glob("*.md"))
check(len(mds) == 1, f"exactly one markdown file for the page (found {len(mds)}: {[m.name for m in mds]})")
if not mds:
    sys.exit(1)
md = mds[0].read_text(encoding="utf-8")
body = md.split("\n---\n", 2)[-1] if md.startswith("---") else md
parts = re.split(r"\n---\n", md)
text = parts[1] if len(parts) > 2 else md   # between frontmatter and image trailer
lines = [l for l in text.splitlines() if l.strip()]

# --- text ---
check(len(lines) >= 15, f"recognized text has >= 15 lines (got {len(lines)})")
norm = unicodedata.normalize("NFKD", text.lower())
norm = "".join(c for c in norm if not unicodedata.combining(c)).replace("ı", "i")
# Words the seed note is expected to contain. The defaults are generic; the list that matches your own
# test note lives outside the repo: put one word per line in .e2e-seed/expected-words.txt (gitignored)
# or point E2E_EXPECTED_WORDS at another file.
def _words(name, default):
    f = Path(os.environ.get(name) or ".e2e-seed/expected-words.txt")
    if not f.is_file():
        return default
    fold = lambda w: "".join(c for c in unicodedata.normalize("NFKD", w.strip().lower()) if not unicodedata.combining(c)).replace("ı", "i")
    return [fold(w) for w in f.read_text(encoding="utf-8").splitlines() if w.strip() and not w.startswith("#")]
must = _words("E2E_EXPECTED_WORDS", ["linkedin", "windowsta", "outsource", "regulation", "pulse", "bollinger"])
missing = [w for w in must if w not in norm]
check(not missing, f"expected words present, missing: {missing}")
hard = ["operasyon", "sofistike", "stratejiler", "trendde"]   # lost when the recognizer crashed on that block
check(any(w in norm for w in hard), f"at least one word from the block that used to crash the recognizer ({hard})")
check("could not be recognized" not in text, "no 'could not be recognized' placeholder")
struck = re.findall(r"~~[^~\n]+~~", text)
check(len(struck) >= 5, f"struck-through lines are marked ~~like this~~ (found {len(struck)}, expected >= 5)")
# A word you wrote under one of the strike lines (set E2E_STRIKE_WORD, ASCII, lowercase); by default any struck text will do.
strike_word = os.environ.get("E2E_STRIKE_WORD", "").strip().lower()
if strike_word:
    check(strike_word in norm, f"text under a strike line is readable ({strike_word})")
else:
    check(any(len(re.findall(r"[^\W\d_]", t)) >= 3 for t in struck), "text under a strike line is readable")

# --- image ---
m = re.search(r"!\[[^\]]*\]\(([^)]+)\)", md)
check(m is not None, "image embed uses standard markdown ![..](..)")
check("![[" not in md, "no Obsidian-only ![[ ]] embed")
if m:
    rel = urllib.parse.unquote(m.group(1))
    img = root / rel
    check(img.is_file(), f"embedded image exists on disk: {rel}")
    if img.is_file():
        out = subprocess.run(["sips", "-g", "pixelHeight", str(img)], capture_output=True, text=True).stdout
        h = int(re.search(r"pixelHeight: (\d+)", out).group(1))
        check(h >= 4000, f"image shows the whole long page (height {h}px, expected >= 4000)")
        check(img.stat().st_size > 100_000, f"image is not blank (size {img.stat().st_size} bytes)")
bad = [p.name for p in root.rglob("*") if p.is_file() and re.search(r"[\[\]#^|]", p.name)]
check(not bad, f"no file names with [ ] # ^ | ({bad})")
jpgs = list((root / "jpg_archive").glob("*.jpg"))
check(len(jpgs) == 1, f"no stale duplicate images (found {len(jpgs)})")

print("\nRESULT:", "FAILED" if fails else "ALL PASSED")
sys.exit(1 if fails else 0)
