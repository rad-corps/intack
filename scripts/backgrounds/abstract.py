"""
Generates the soft colour backgrounds: a few large blurred colour blobs over a base colour, with a
gentle low-frequency wash and a touch of grain (which stops gradients banding on phone screens).
They're ours, so they're under the app's licence.
usage: python abstract.py OUT_DIR   (writes OUT_DIR/abstract-<name>.webp at 720x1280)
"""
import os, sys, zlib
import numpy as np
from PIL import Image

W, H = 720, 1280
out = sys.argv[1]
os.makedirs(out, exist_ok=True)

# name: (base, [(colour, x, y, radius)...]) with x/y/radius as fractions of the width/height.
PALETTES = {
    # Light
    "dawn": ("#f6e3d6", [("#f3b7a1", 0.15, 0.12, 0.75), ("#f7d9a8", 0.95, 0.40, 0.70), ("#e9a6b8", 0.25, 0.85, 0.80), ("#fbefe3", 0.70, 0.95, 0.55)]),
    "sage": ("#e3e8dc", [("#b7c9a8", 0.20, 0.15, 0.80), ("#dfe6c9", 0.90, 0.45, 0.70), ("#9fb69a", 0.15, 0.80, 0.70), ("#efe9d8", 0.80, 0.95, 0.60)]),
    "blush": ("#f7e6ea", [("#efb8c8", 0.85, 0.10, 0.75), ("#f6d5dd", 0.10, 0.45, 0.70), ("#e8a9bd", 0.80, 0.85, 0.70), ("#fdf1f3", 0.20, 0.95, 0.55)]),
    "peach": ("#fbe9dc", [("#f8c19c", 0.10, 0.20, 0.75), ("#fcdcc0", 0.90, 0.50, 0.70), ("#f4ad8a", 0.30, 0.90, 0.75), ("#fff3e8", 0.85, 0.05, 0.50)]),
    "butter": ("#fbf3d9", [("#f6dd8f", 0.20, 0.10, 0.75), ("#fbecb8", 0.85, 0.45, 0.70), ("#efd27a", 0.25, 0.85, 0.65), ("#fffaea", 0.80, 0.95, 0.55)]),
    "mint": ("#e2f2ea", [("#a8dcc3", 0.85, 0.15, 0.75), ("#cdebdc", 0.10, 0.50, 0.70), ("#93cfb6", 0.70, 0.90, 0.70), ("#f1faf5", 0.20, 0.05, 0.50)]),
    "sky": ("#e3eef8", [("#a9cbec", 0.15, 0.10, 0.80), ("#cfe2f4", 0.90, 0.45, 0.70), ("#94bbe3", 0.25, 0.88, 0.70), ("#f2f7fc", 0.80, 0.95, 0.55)]),
    "lilac": ("#ece6f5", [("#c7b5e6", 0.85, 0.12, 0.75), ("#ddd2ef", 0.10, 0.45, 0.70), ("#b6a2dc", 0.75, 0.88, 0.70), ("#f6f2fb", 0.20, 0.95, 0.55)]),
    "sand": ("#efe7da", [("#dcc7a6", 0.15, 0.15, 0.80), ("#e9dcc5", 0.90, 0.50, 0.70), ("#cdb38c", 0.30, 0.90, 0.70), ("#f7f2ea", 0.85, 0.05, 0.50)]),
    "mist": ("#e6eaee", [("#c1cbd6", 0.85, 0.15, 0.80), ("#d8dee5", 0.10, 0.50, 0.70), ("#adb9c7", 0.70, 0.88, 0.70), ("#f3f5f7", 0.20, 0.95, 0.55)]),
    # Dark
    "lagoon": ("#0f2e3a", [("#1d6f78", 0.10, 0.20, 0.80), ("#2a9d8f", 0.90, 0.55, 0.65), ("#174a6b", 0.30, 0.90, 0.85), ("#5fb3a6", 0.85, 0.05, 0.45)]),
    "dusk": ("#1c1830", [("#3c2f63", 0.15, 0.15, 0.85), ("#6b3f73", 0.95, 0.45, 0.70), ("#25335e", 0.30, 0.90, 0.85), ("#a0577a", 0.75, 0.05, 0.45)]),
    "ocean": ("#0d1b2e", [("#1b3f73", 0.80, 0.15, 0.85), ("#245a8c", 0.10, 0.55, 0.70), ("#122a52", 0.70, 0.92, 0.80), ("#3b7bb0", 0.20, 0.05, 0.45)]),
    "moss": ("#14211a", [("#2c4a33", 0.15, 0.15, 0.85), ("#3e6644", 0.90, 0.50, 0.70), ("#1f3a2a", 0.30, 0.90, 0.80), ("#5f8a5a", 0.80, 0.05, 0.45)]),
    "plum": ("#221424", [("#4d2550", 0.85, 0.15, 0.85), ("#6d3466", 0.10, 0.50, 0.70), ("#3a1c44", 0.70, 0.90, 0.80), ("#94507f", 0.20, 0.05, 0.45)]),
    "ember": ("#24140f", [("#5c2a1a", 0.15, 0.12, 0.85), ("#8a4122", 0.90, 0.50, 0.70), ("#3f1d16", 0.30, 0.90, 0.80), ("#b8642e", 0.80, 0.05, 0.45)]),
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
    rng = np.random.default_rng(zlib.crc32(name.encode()))
    img = np.broadcast_to(rgb(base), (H, W, 3)).copy()
    for colour, bx, by, radius in blobs:
        dist = np.hypot(xs - bx * W, (ys - by * H) * 0.75) / (radius * W)
        weight = np.exp(-dist ** 2 * 1.6)[..., None]
        img = img * (1 - weight) + rgb(colour) * weight
    # A gentle wash (coarse enough not to show as blotches), then fine grain.
    wash = smooth_noise(rng, 3, 5) - 0.5
    img += wash[..., None] * 0.035
    img += (rng.random((H, W, 1)).astype(np.float32) - 0.5) * 0.02
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(os.path.join(out, f"abstract-{name}.webp"), "WEBP", quality=80, method=6)
    print(name)
