"""
Generates the "Pixel hills" background: a retro platformer scene drawn as pixel art — banded deep
blue sky with blocky clouds, a far ridge, a jagged grass edge and brown checkerboard ground. Drawn
at 180x320 and scaled up 4x without smoothing, so the pixels stay crisp. Ours, under the app's licence.
usage: python pixel_hills.py OUT_DIR   (writes OUT_DIR/pixel-hills.webp at 720x1280)
"""
import os, random, sys
from PIL import Image, ImageDraw

W, H, SCALE = 180, 320, 4
out = sys.argv[1]
rng = random.Random(7)
img = Image.new("RGB", (W, H))
d = ImageDraw.Draw(img)

# Sky: bands of blue, darker at the top.
SKY = ["#1414a8", "#1a1ab4", "#2020c0", "#2828cc", "#3030d8", "#3a3ae2"]
GROUND = 168
band = GROUND // len(SKY)
for i, c in enumerate(SKY):
    d.rectangle([0, i * band, W, (i + 1) * band + 1], fill=c)

# Blocky clouds: a few stacked white slabs with a pale blue underside.
def cloud(x, y, w):
    d.rectangle([x + 3, y, x + w - 3, y + 2], fill="#ffffff")
    d.rectangle([x, y + 2, x + w, y + 6], fill="#ffffff")
    d.rectangle([x + 2, y + 6, x + w - 2, y + 8], fill="#b8c8ff")
for x, y, w in [(8, 18, 26), (70, 10, 18), (120, 30, 30), (40, 62, 22), (140, 80, 20), (10, 100, 16), (96, 112, 24)]:
    cloud(x, y, w)

# Far ridge: jagged brown peaks along the horizon.
ridge = []
x = 0
while x <= W:
    ridge.append((x, GROUND - rng.randint(10, 22)))
    x += rng.randint(4, 8)
    ridge.append((x, GROUND - rng.randint(2, 6)))
    x += rng.randint(3, 6)
d.polygon([(0, GROUND)] + ridge + [(W, GROUND)], fill="#8a4a14")
d.polygon([(0, GROUND)] + [(px, py + 5) for px, py in ridge] + [(W, GROUND)], fill="#6a3410")

# Checkerboard ground, two oranges, with a darker band every few rows.
SQ = 8
for row, y in enumerate(range(GROUND, H, SQ)):
    for col, x in enumerate(range(0, W, SQ)):
        light, dark = ("#c46418", "#8c4010") if (row // 4) % 2 == 0 else ("#b45a14", "#7c380c")
        d.rectangle([x, y, x + SQ - 1, y + SQ - 1], fill=light if (row + col) % 2 == 0 else dark)
# Shadow under the grass.
d.rectangle([0, GROUND, W, GROUND + 3], fill="#5a2a08")

# Grass: a solid strip with a jagged tufted top and a lighter highlight.
d.rectangle([0, GROUND - 4, W, GROUND + 1], fill="#2f9e1e")
for x in range(0, W, 3):
    h = rng.randint(3, 8)
    d.polygon([(x, GROUND - 4), (x + 1, GROUND - 4 - h), (x + 3, GROUND - 4)], fill="#3cbf24")
d.line([0, GROUND - 4, W, GROUND - 4], fill="#62d83a")

os.makedirs(out, exist_ok=True)
img.resize((W * SCALE, H * SCALE), Image.NEAREST).save(os.path.join(out, "pixel-hills.webp"), "WEBP", lossless=True)
print("pixel-hills")
