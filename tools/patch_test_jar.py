"""Copy the release jar to the HMCL test instance and relax the NeoForge
version floor to the version actually installed there (21.1.234).

This only touches the copied test jar; build/libs stays on 21.1.265.
"""
from __future__ import annotations

import io
import zipfile
from pathlib import Path

SOURCE = Path("build/libs/meteorosprotocol-1.0.0.jar")
TARGET = Path(r"D:\mc\.minecraft\versions\Meteoros Test\mods\meteorosprotocol-1.0.0-test.jar")
OLD = b'versionRange="[21.1.265,)"'
NEW = b'versionRange="[21.1.234,)"'


def patch_toml(data: bytes) -> bytes:
    return data.replace(OLD, NEW)


def patch_jar_bytes(data: bytes) -> bytes:
    input_buf = io.BytesIO(data)
    output_buf = io.BytesIO()
    with zipfile.ZipFile(input_buf, "r") as src, zipfile.ZipFile(
        output_buf, "w", compression=zipfile.ZIP_DEFLATED
    ) as dst:
        for info in src.infolist():
            payload = src.read(info.filename)
            if info.filename == "META-INF/neoforge.mods.toml":
                payload = patch_toml(payload)
            elif info.filename.endswith(".jar") and "jarjar" in info.filename:
                # Localized rain is nested; patch its own metadata too.
                payload = patch_jar_bytes(payload)
            dst.writestr(info, payload)
    return output_buf.getvalue()


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"missing {SOURCE}")
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    raw = SOURCE.read_bytes()
    patched = patch_jar_bytes(raw)
    TARGET.write_bytes(patched)
    print(f"wrote {TARGET} ({len(patched):,} bytes)")
    with zipfile.ZipFile(TARGET) as jar:
        outer = jar.read("META-INF/neoforge.mods.toml")
        print("outer 265 remaining:", outer.count(OLD), "| 234 present:", outer.count(NEW))
        for name in jar.namelist():
            if name.endswith(".jar") and "jarjar" in name:
                nested = zipfile.ZipFile(io.BytesIO(jar.read(name)))
                toml = nested.read("META-INF/neoforge.mods.toml")
                print(f"nested {name}: 265 remaining {toml.count(OLD)}, 234 present {toml.count(NEW)}")


if __name__ == "__main__":
    main()
