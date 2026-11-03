#!/usr/bin/env python3
"""Regenerates assets/ui/font-glyphs.fnt + font-glyphs.png (UI bitmap font) from LiberationSans-Regular.ttf (SIL OFL 1.1).
Charset: ASCII, Latin-1, Latin Extended-A (Polish), Greek, and a few symbols. Run: python3 tool/font/gen_font.py
Named font-glyphs (not font) so Skin loads this png instead of the stale "font" region of uiskin.atlas."""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "assets", "ui")
SIZE, LINE_HEIGHT, BASE, PAD, W = 13, 21, 15, 1, 512
chars = list(range(32, 127)) + list(range(0xA0, 0x180)) + list(range(0x391, 0x3CA)) + [0x2022, 0x2026, 0x2212, 0x2192]
font = ImageFont.truetype(os.path.join(HERE, "LiberationSans-Regular.ttf"), SIZE)
glyphs, x, y, rowh = [], 0, 0, 0
for cp in chars:
    ch = chr(cp)
    adv = round(font.getlength(ch))
    bbox = font.getbbox(ch, anchor="ls")  # relative to baseline origin
    if cp == 32 or bbox[2] <= bbox[0]:
        glyphs.append((cp, 0, 0, 0, 0, 0, 0, adv)); continue
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    if x + w + PAD > W:
        x, y, rowh = 0, y + rowh + PAD, 0
    glyphs.append((cp, x, y, w, h, bbox[0], BASE + bbox[1], adv))
    x += w + PAD; rowh = max(rowh, h)
H = 1
while H < y + rowh + PAD: H *= 2
img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
for cp, gx, gy, w, h, xo, yo, adv in glyphs:
    if w == 0: continue
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).text((-font.getbbox(chr(cp), anchor="ls")[0], -font.getbbox(chr(cp), anchor="ls")[1]), chr(cp), font=font, fill=255, anchor="ls")
    img.paste(Image.new("RGBA", (w, h), (255, 255, 255, 255)), (gx, gy), mask)
img.save(os.path.join(OUT, "font-glyphs.png"))
with open(os.path.join(OUT, "font-glyphs.fnt"), "w") as f:
    f.write(f'info face="font" size=12 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=2 padding=0,0,0,0 spacing=0,0\n')
    f.write(f'common lineHeight={LINE_HEIGHT} base={BASE} scaleW={W} scaleH={H} pages=1 packed=0 alphaChnl=0 redChnl=0 greenChnl=0 blueChnl=0\n')
    f.write('page id=0 file="font-glyphs.png"\n')
    f.write(f'chars count={len(glyphs)}\n')
    for cp, gx, gy, w, h, xo, yo, adv in glyphs:
        f.write(f'char id={cp} x={gx} y={gy} width={w} height={h} xoffset={xo} yoffset={yo} xadvance={adv} page=0 chnl=0\n')
