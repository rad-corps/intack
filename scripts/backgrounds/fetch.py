"""
Downloads the chosen photos at the best size available without an account.
usage: python fetch.py picks.json OUT_DIR
Flickr: looks on the photo page for the larger (h: 1600 px, k: 2048 px) sizes. StockSnap: the
download link gives the original. Others: the given url.
"""
import io, json, os, re, sys, urllib.request
from PIL import Image

UA = {"User-Agent": "Mozilla/5.0 (TwoDo background fetch)"}
picks = json.load(open(sys.argv[1]))
out = sys.argv[2]
os.makedirs(out, exist_ok=True)


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()


def candidates(p):
    landing = p["landing"]
    if "flickr.com" in landing:
        html = get(landing).decode("utf-8", "replace").replace("\\/", "/")
        photo_id = landing.rstrip("/").split("/")[-1]
        for size in ("k", "h"):
            m = re.search(rf'live\.staticflickr\.com/\d+/{photo_id}_[0-9a-f]+_{size}\.jpg', html)
            if m:
                yield "https://" + m.group(0)
    if "stocksnap.io/photo/" in landing:
        yield "https://stocksnap.io/download-photo/" + landing.rstrip("/").split("-")[-1]
    yield p["url"]


for p in picks:
    for url in candidates(p):
        try:
            data = get(url)
            img = Image.open(io.BytesIO(data))
            img.load()
        except Exception as e:
            print(f"  {p['id']}: {url} failed: {e}")
            continue
        img.convert("RGB").save(os.path.join(out, p["id"] + ".jpg"), quality=95)
        p["downloaded"] = url
        print(f"{p['id']}: {img.size[0]}x{img.size[1]} from {url[:80]}")
        break
json.dump(picks, open(os.path.join(out, "picks.json"), "w"), indent=1)
