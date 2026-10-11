"""
Turns downloaded photos and generated abstracts into the app's background assets: portrait 9:16,
at most 900x1600, WebP. Even textures are mirror-tiled at their own scale rather than enlarged.
usage: python process.py PHOTOS_DIR ABSTRACT_DIR OUT_DIR
"""
import os, sys
from PIL import Image, ImageOps

photos, abstract, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
W, H = 900, 1600
TILE = {"cork", "linen", "sea"}


def portrait(img):
    """Centre crop to 9:16, then shrink to fit W x H (never enlarge)."""
    w, h = img.size
    if w / h > W / H:
        cw = round(h * W / H)
        img = img.crop(((w - cw) // 2, 0, (w - cw) // 2 + cw, h))
    else:
        ch = round(w * H / W)
        img = img.crop((0, (h - ch) // 2, w, (h - ch) // 2 + ch))
    if img.width > W:
        img = img.resize((W, H), Image.LANCZOS)
    return img


def tiled(img):
    """Fills W x H with the texture and its mirror images, so the seams match."""
    w, h = img.size
    block = Image.new("RGB", (w * 2, h * 2))
    block.paste(img, (0, 0))
    block.paste(ImageOps.mirror(img), (w, 0))
    block.paste(ImageOps.flip(img), (0, h))
    block.paste(ImageOps.flip(ImageOps.mirror(img)), (w, h))
    canvas = Image.new("RGB", (W, H))
    for y in range(0, H, h * 2):
        for x in range(0, W, w * 2):
            canvas.paste(block, (x, y))
    return canvas


total = 0
for folder in (photos, abstract):
    for name in sorted(os.listdir(folder)):
        stem, ext = os.path.splitext(name)
        if ext.lower() not in (".jpg", ".png"):
            continue
        img = Image.open(os.path.join(folder, name)).convert("RGB")
        img = tiled(img) if stem in TILE else portrait(img)
        path = os.path.join(out, stem + ".webp")
        img.save(path, "WEBP", quality=72, method=6)
        size = os.path.getsize(path)
        total += size
        print(f"{stem}: {img.size[0]}x{img.size[1]} {size // 1024} KB")
print(f"total {total // 1024} KB")

# Preview sheet.
files = sorted(f for f in os.listdir(out) if f.endswith(".webp"))
cols = 9
sheet = Image.new("RGB", (cols * 150, ((len(files) + cols - 1) // cols) * 266), "white")
for i, f in enumerate(files):
    sheet.paste(Image.open(os.path.join(out, f)).resize((144, 256)), ((i % cols) * 150, (i // cols) * 266))
sheet.save(os.path.join(os.path.dirname(out.rstrip("/\\")) or ".", "assets_sheet.jpg"), quality=85)
