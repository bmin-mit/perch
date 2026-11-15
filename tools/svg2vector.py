"""Convert Lucide stroke SVGs into Android vector drawables (stroke-only, 24x24)."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NS = "{http://www.w3.org/2000/svg}"
STROKE = "1.75"  # a touch lighter than Lucide's 2 for a calmer look at large sizes


def to_path(el):
    tag = el.tag.replace(NS, "")
    a = el.attrib
    if tag == "path":
        return a["d"]
    if tag == "circle":
        cx, cy, r = (float(a[k]) for k in ("cx", "cy", "r"))
        return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0"
    if tag == "line":
        return f"M{a['x1']},{a['y1']}L{a['x2']},{a['y2']}"
    if tag in ("polyline", "polygon"):
        pts = a["points"].replace(",", " ").split()
        pairs = [f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts), 2)]
        return "M" + "L".join(pairs) + ("Z" if tag == "polygon" else "")
    if tag == "rect":
        x, y, w, h = (float(a.get(k, 0)) for k in ("x", "y", "width", "height"))
        return f"M{x},{y}h{w}v{h}h{-w}Z"
    raise ValueError(f"unsupported element {tag}")


def convert(src: Path) -> str:
    root = ET.parse(src).getroot()
    paths = [to_path(el) for el in root.iter() if el is not root and el.tag.replace(NS, "") != "title"]
    body = "\n".join(
        f'    <path\n        android:pathData="{d}"\n        android:strokeColor="#FFFFFFFF"\n'
        f'        android:strokeWidth="{STROKE}"\n        android:strokeLineCap="round"\n'
        f'        android:strokeLineJoin="round" />'
        for d in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Lucide \"{src.stem}\" (ISC license, lucide.dev), converted for Perch. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n    android:height="24dp"\n'
        '    android:viewportWidth="24"\n    android:viewportHeight="24">\n'
        f"{body}\n</vector>\n"
    )


if __name__ == "__main__":
    # Usage: svg2vector.py <svg dir> <res/drawable dir> [prefix]   (prefix: wx_ for weather, ic_ otherwise)
    src_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    prefix = sys.argv[3] if len(sys.argv) > 3 else "wx_"
    for svg in sorted(src_dir.glob("*.svg")):
        name = prefix + svg.stem.replace("-", "_")
        (out_dir / f"{name}.xml").write_text(convert(svg))
        print(name)
