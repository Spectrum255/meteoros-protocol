import os
import re
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ZIP = r"D:\Users\xw\Downloads\AI模组开发效率工具包.zip"
OUT = os.path.join(os.environ["TEMP"], "ai-toolkit-py")

os.makedirs(OUT, exist_ok=True)

with zipfile.ZipFile(ZIP) as z:
    for info in z.infolist():
        name = info.filename
        # The archive has no UTF-8 flag; recover the original GBK filename.
        try:
            name = name.encode("cp437").decode("utf-8")
        except Exception:
            pass
        name = re.sub(r'[<>:"|?*]', "_", name)
        if not name.strip():
            continue
        path = os.path.join(OUT, name)
        if info.is_dir():
            os.makedirs(path, exist_ok=True)
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with z.open(info) as src, open(path, "wb") as dst:
            dst.write(src.read())
        print(f"{info.file_size}\t{name}")

print(f"\nOUT={OUT}")
