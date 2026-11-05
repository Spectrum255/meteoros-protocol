import json
from pathlib import Path

files = [
    Path("src/main/resources/assets/meteorosprotocol/models/block/particle_intake.json"),
    Path("src/main/resources/assets/meteorosprotocol/models/block/phase_separator.json"),
    Path("src/main/resources/assets/meteorosprotocol/models/block/thermal_generator.json"),
]

for path in files:
    text = path.read_text(encoding="utf-8")
    if '"parent"' in text:
        print(f"skip {path.name}: already has parent")
        continue
    patched = text.replace("{\n", '{\n  "parent": "minecraft:block/block",\n', 1)
    if patched == text:
        patched = text.replace("{", '{"parent": "minecraft:block/block",', 1)
    json.loads(patched)
    path.write_text(patched, encoding="utf-8")
    print(f"patched {path.name}")
