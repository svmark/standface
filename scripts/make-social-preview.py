#!/usr/bin/env python3
"""GitHub 소셜 미리보기 이미지(1280×640)를 만든다. 화면 그림은 screenshots/에서 가져온다.
    python3 scripts/make-social-preview.py
만든 뒤 GitHub 저장소 Settings › Social preview에 직접 올린다(API로는 못 바꾼다).
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
KO = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
NUNITO = ROOT.parent / "app/src/main/res/font/nunito.ttf"  # 이 Mac의 fold-standby 안에 있을 때

W, H = 1280, 640
bg = Image.new("RGB", (W, H), (10, 10, 12))
d = ImageDraw.Draw(bg)


def ko(size, idx):
    return ImageFont.truetype(KO, size, index=idx)


def shot(path, h=None, w=None, r=20):
    im = Image.open(path).convert("RGB")
    if w:
        h = round(im.height * w / im.width)
    else:
        w = round(im.width * h / im.height)
    im = im.resize((w, h), Image.LANCZOS)
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255)
    return im, m


def framed(img, mask, x, y, r):
    d.rounded_rectangle((x - 6, y - 6, x + img.width + 5, y + img.height + 5), r + 6, fill=(10, 10, 12))
    d.rounded_rectangle((x - 3, y - 3, x + img.width + 2, y + img.height + 2), r + 3, fill=(52, 52, 58))
    bg.paste(img, (x, y), mask)


big, bm = shot(ROOT / "screenshots/fold8_inner_land_photos.png", w=600, r=22)
bx, by = W - big.width - 44, (H - big.height) // 2 - 30
framed(big, bm, bx, by, 22)
tall, tm = shot(ROOT / "screenshots/flip8_tabletop_clock.png", h=330, r=18)
framed(tall, tm, bx - tall.width // 2 - 10, H - tall.height - 28, 18)

title = ImageFont.truetype(str(NUNITO), 84)
title.set_variation_by_axes([900])
orange = (245, 158, 46)
d.text((62, 150), "Standface", font=title, fill=(240, 237, 246))
d.text((66, 268), "갤럭시를 세워 두면", font=ko(36, 6), fill=(240, 237, 246))
d.text((66, 316), "켜지는 시계 · 달력 화면", font=ko(36, 6), fill=(240, 237, 246))
d.text((66, 388), "폴드 · 플립 · S 시리즈", font=ko(28, 6), fill=orange)
d.text((66, 430), "무료 · 광고 없음 · 한국어", font=ko(24, 3), fill=(169, 165, 180))
d.text((66, 548), "github.com/svmark/standface", font=ko(22, 3), fill=(110, 107, 118))
bg.save(ROOT / "social-preview.png")
print("social-preview.png", bg.size)
