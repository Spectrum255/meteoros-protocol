"""Add the six per-phase empty-screen textures to the v4 .bbmodel.

The exported Java model was patched to `display_empty_ae/th/cr/vp/vm/aq`, but
the Blockbench source still pointed all six `Screen_*` faces at the old
neutral `display_empty` texture.  This script mirrors the export change into
the .bbmodel so opening the source in Blockbench shows the same six colours.

Run from the project root:
    python art/patch_bbmodel_screens.py
"""

from __future__ import annotations

import base64
import copy
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BBMODEL = ROOT / "art" / "intake-tower-v4.bbmodel"
TEXTURES = ROOT / "art" / "export" / "particle_intake" / "textures"

PHASE_KEYS = ["ae", "th", "cr", "vp", "vm", "aq"]
SCREEN_NAMES = {
    "ae": "Screen_Ae",
    "th": "Screen_Th",
    "cr": "Screen_Cr",
    "vp": "Screen_Vp",
    "vm": "Screen_Vm",
    "aq": "Screen_Aq",
}


def data_url(path: Path) -> str:
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


def main() -> None:
    data = json.loads(BBMODEL.read_text(encoding="utf-8"))
    textures = data["textures"]
    if not any(t["name"] == "display_empty" for t in textures):
        raise SystemExit("display_empty template not found")

    template = next(t for t in textures if t["name"] == "display_empty")
    added: dict[str, int] = {}
    for key in PHASE_KEYS:
        name = f"display_empty_{key}"
        if any(t["name"] == name for t in textures):
            added[key] = next(i for i, t in enumerate(textures) if t["name"] == name)
            continue
        entry = copy.deepcopy(template)
        entry["name"] = name
        entry["id"] = str(len(textures))
        entry["uuid"] = str(uuid.uuid4())
        entry["source"] = data_url(TEXTURES / f"{name}.png")
        entry["width"] = entry["height"] = entry["uv_width"] = entry["uv_height"] = 16
        textures.append(entry)
        added[key] = len(textures) - 1

    patched = 0
    for element in data["elements"]:
        name = element.get("name")
        for key, screen in SCREEN_NAMES.items():
            if name == screen:
                north = element["faces"].get("north")
                if north is None:
                    raise SystemExit(f"{screen} has no north face")
                north["texture"] = added[key]
                patched += 1
    if patched != 6:
        raise SystemExit(f"expected 6 screens, patched {patched}")

    BBMODEL.write_text(json.dumps(data, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"updated {BBMODEL.name}: {len(textures)} textures, indices {added}")


if __name__ == "__main__":
    main()
