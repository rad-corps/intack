"""
Finds CC0 / public-domain photo candidates on Openverse and makes a numbered contact sheet per query.
usage: python search.py OUT_DIR "query" [more queries...]
Writes OUT_DIR/<slug>.json (candidate metadata) and OUT_DIR/<slug>.jpg (contact sheet).
"""
import io, json, os, sys, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageDraw

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
UA = {"User-Agent": "Intack-background-search/1.0"}


def get(url, timeout=30):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout).read()


def search(q):
    params = urllib.parse.urlencode({"q": q, "license": "cc0,pdm", "page_size": 20, "mature": "false"})
    results = json.loads(get(f"https://api.openverse.org/v1/images/?{params}"))["results"]
    # Big enough for a phone background.
    return [r for r in results if max(r.get("width") or 0, r.get("height") or 0) >= 1000]


def thumb(r):
    try:
        img = Image.open(io.BytesIO(get(r["thumbnail"], 20))).convert("RGB")
        img.thumbnail((300, 300))
        return img
    except Exception:
        return None


for q in sys.argv[2:]:
    slug = q.replace(" ", "_")
    results = search(q)[:20]
    with ThreadPoolExecutor(8) as pool:
        thumbs = list(pool.map(thumb, results))
    keep = [(r, t) for r, t in zip(results, thumbs) if t is not None]
    json.dump([r for r, _ in keep], open(os.path.join(out, slug + ".json"), "w"), indent=1)
    cols = 5
    rows = max(1, (len(keep) + cols - 1) // cols)
    sheet = Image.new("RGB", (cols * 300, rows * 300), "white")
    draw = ImageDraw.Draw(sheet)
    for i, (r, t) in enumerate(keep):
        x, y = (i % cols) * 300, (i // cols) * 300
        sheet.paste(t, (x, y))
        draw.rectangle([x, y, x + 34, y + 22], fill="black")
        draw.text((x + 4, y + 4), str(i), fill="yellow")
    sheet.save(os.path.join(out, slug + ".jpg"), quality=80)
    print(q, len(keep))
