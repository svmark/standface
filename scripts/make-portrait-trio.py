#!/usr/bin/env python3
"""README의 세로 예시 3장(플립 반쯤 접기 · 폴드 펼쳐 반쯤 접기 · S 시리즈 세로)을 한 장으로 합친다.
화면 스냅숏 기준 그림(app/screenshots)을 다시 만들었으면 이것도 다시 돌린다.
    python3 scripts/make-portrait-trio.py
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
NAMES = ["flip8_tabletop_clock", "fold8_inner_tabletop", "galaxy_s_port_calendar"]
HEIGHT, GAP = 560, 24

ims = [Image.open(ROOT / "app/screenshots" / f"{n}.png").convert("RGBA") for n in NAMES]
ims = [im.resize((round(im.width * HEIGHT / im.height), HEIGHT), Image.LANCZOS) for im in ims]
out = Image.new("RGBA", (sum(i.width for i in ims) + GAP * (len(ims) - 1), HEIGHT), (0, 0, 0, 0))
x = 0
for im in ims:
    out.paste(im, (x, 0))
    x += im.width + GAP
out.save(ROOT / "screenshots/portrait-trio.png", optimize=True)
print("screenshots/portrait-trio.png", out.size)
