#!/usr/bin/env python3
"""Download only the merchant album URLs recorded in the reviewed manifest."""
import concurrent.futures
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "frontend/html/hmdp/imgs/campus"
CATALOG = ROOT / "src/main/resources/campus-shops.json"


def download(item):
    target = DEST / (item["key"] + "-" + str(item["index"]) + ".jpg")
    try:
        if not target.exists():
            request = urllib.request.Request(item["url"], headers={"User-Agent": "Mozilla/5.0", "Referer": item["source"]})
            with urllib.request.urlopen(request, timeout=15) as response:
                content = response.read(8 * 1024 * 1024)
                if not response.headers.get("Content-Type", "").startswith("image/"):
                    raise ValueError("not an image")
            if len(content) < 1000:
                raise ValueError("empty image")
            target.write_bytes(content)
        return item["key"], item["index"], "/imgs/campus/" + target.name
    except Exception as error:
        print(item["key"], item["index"], "download failed:", type(error).__name__)
        return item["key"], item["index"], None


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / "scripts/campus-photo-sources.json").read_text())
    photos = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        for key, index, path in executor.map(download, manifest):
            if path:
                photos.setdefault(key, []).append((index, path))
    catalog = json.loads(CATALOG.read_text())
    area_photos = [path for _, path in sorted(photos.get("area-2", []))]
    sources = {item["key"]: item["source"] for item in manifest}
    for shop in catalog:
        shop["images"] = ",".join(path for _, path in sorted(photos.get(shop["catalogKey"], [])))
        if shop["images"]:
            shop["photoSourceUrl"] = sources[shop["catalogKey"]]
        elif area_photos:
            shop["images"] = area_photos[(shop["typeId"] - 1) % len(area_photos)]
            shop["photoSourceUrl"] = sources["area-2"]
    CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    print("Merchant albums:", sum(key.startswith("qsh-") for key in photos), "; area covers:", len(area_photos))


if __name__ == "__main__":
    main()
