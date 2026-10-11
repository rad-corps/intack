"""
Generates soft abstract backgrounds: a few large blurred colour blobs over a base colour, with a
low-frequency wash and a touch of grain (which stops gradients banding on phone screens).
usage: python abstract.py OUT_DIR
"""
import os, sys
import numpy as np
from PIL import Image

W, H = 720, 1600
out = sys.argv[1]
os.makedirs(out, exist_ok=True)

PALETTES = {
    # id: (base, [(colour, x, y, radius)...]) with x/y/radius as fractions of the width/height.
    "abstract-dawn": ("#f6e3d6", [("#f3b7a1", 0.15, 0.12, 0.75), ("#f7d9a8", 0.95, 0.40, 0.70), ("#e9a6b8", 0.25, 0.85, 0.80), ("#fbefe3", 0.70, 0.95, 0.55)]),
    "abstract-lagoon": ("#0f2e3a", [("#1d6f78", 0.10, 0.20, 0.80), ("#2a9d8f", 0.90, 0.55, 0.65), ("#174a6b", 0.30, 0.90, 0.85), ("#5fb3a6", 0.85, 0.05, 0.45)]),
    "abstract-sage": ("#e3e8dc", [("#b7c9a8", 0.20, 0.15, 0.80), ("#dfe6c9", 0.90, 0.45, 0.70), ("#9fb69a", 0.15, 0.80, 0.70), ("#efe9d8", 0.80, 0.95, 0.60)]),
    "abstract-dusk": ("#1c1830", [("#3c2f63", 0.15, 0.15, 0.85), ("#6b3f73", 0.95, 0.45, 0.70), ("#25335e", 0.30, 0.90, 0.85), ("#a0577a", 0.75, 0.05, 0.45)]),
}


def rgb(hex_colour):
    return np.array([int(hex_colour[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float32) / 255


def smooth_noise(rng, cells_x, cells_y):
    """Value noise: a coarse random grid smoothly upscaled to the image size."""
    grid = rng.random((cells_y, cells_x)).astype(np.float32)
    img = Image.fromarray((grid * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)
    return np.asarray(img, dtype=np.float32) / 255


ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)
for name, (base, blobs) in PALETTES.items():
    rng = np.random.default_rng(abs(hash(name)) % 2**32)
    img = np.broadcast_to(rgb(base), (H, W, 3)).copy()
    for colour, bx, by, radius in blobs:
        dist = np.hypot(xs - bx * W, (ys - by * H) * 0.75) / (radius * W)
        weight = np.exp(-dist ** 2 * 1.6)[..., None]
        img = img * (1 - weight) + rgb(colour) * weight
    # A gentle watercolour-like wash, then fine grain.
    wash = smooth_noise(rng, 5, 9) - 0.5
    img += wash[..., None] * 0.06
    img += (rng.random((H, W, 1)).astype(np.float32) - 0.5) * 0.02
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(os.path.join(out, name + ".png"))
    print(name)
