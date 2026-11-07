#!/usr/bin/env python3
"""Meteoros Protocol — GUI 布局编辑器本地服务（Python 标准库，无第三方依赖）。

用法：
    python tools/gui-editor/server.py            # 默认 127.0.0.1:8765
    python tools/gui-editor/server.py --port 9000

职责：
  * 托管 tools/gui-editor/ 下的静态前端
  * 读写 src/main/resources/assets/meteorosprotocol/gui/<id>.json（带校验）
  * 暴露 /assets/icons/<name>.png（映射到 art/gui/icons/）
  * Build 按钮：跑 gradlew build → tools/make-test-jar.ps1，日志流式返回
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import socket
import subprocess
import sys
import threading
import time
import uuid
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parents[2]          # 仓库根
EDITOR_DIR = ROOT / "tools" / "gui-editor"
GUI_DIR = ROOT / "src" / "main" / "resources" / "assets" / "meteorosprotocol" / "gui"
ICON_DIR = ROOT / "art" / "gui" / "icons"
LIB_DIR = ROOT / "build" / "libs"
TEST_MODS = Path(r"D:\mc\.minecraft\versions\Meteoros Test\mods")

JAVA_HOME = r"C:\Program Files\Microsoft\jdk-21.0.6.7-hotspot"
PROXY = ("127.0.0.1", 7890)

VALID_TYPES = {
    "title", "text", "phase_rows", "bar_phase", "bar_energy",
    "slot", "icon_button", "inventory", "spacer",
}
VALID_ACTIONS = {"toggle", "redstone", "extract", "auto_io", "side_config"}
VALID_ROLES = {"filter", "canister"}

# ---------------------------------------------------------------- build jobs


class Job:
    def __init__(self) -> None:
        self.id = uuid.uuid4().hex[:12]
        self.lines: list[str] = []
        self.done = False
        self.ok = False
        self.result: dict | None = None
        self.started = time.time()
        self._lock = threading.Lock()

    def log(self, text: str) -> None:
        for raw in text.splitlines():
            with self._lock:
                self.lines.append(raw.rstrip())
        sys.stdout.write(text if text.endswith("\n") else text + "\n")
        sys.stdout.flush()

    def snapshot(self, since: int) -> dict:
        with self._lock:
            chunk = self.lines[since:]
            total = len(self.lines)
        return {
            "ok": True,
            "job": self.id,
            "lines": chunk,
            "next": total,
            "done": self.done,
            "result": self.result,
        }


JOBS: dict[str, Job] = {}


def proxy_open() -> bool:
    try:
        with socket.create_connection(PROXY, timeout=0.4):
            return True
    except OSError:
        return False


def newest(pattern: str, folder: Path) -> Path | None:
    if not folder.is_dir():
        return None
    found = [p for p in folder.glob(pattern) if p.is_file()]
    return max(found, key=lambda p: p.stat().st_mtime) if found else None


def run_job(job: Job) -> None:
    env = dict(os.environ)
    env["JAVA_HOME"] = JAVA_HOME
    if proxy_open():
        proxy = "127.0.0.1:7890"
        env["GRADLE_OPTS"] = (
            f"-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 "
            f"-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890"
        )
        job.log(f"[info] 检测到代理 {proxy}，已传给 Gradle")
    else:
        job.log("[info] 7890 代理未开启，按直连构建（依赖已缓存时通常没问题）")

    try:
        job.log("[step 1/2] gradlew build ...")
        proc = subprocess.Popen(
            [str(ROOT / "gradlew.bat"), "build", "--console=plain"],
            cwd=str(ROOT), env=env, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
        )
        assert proc.stdout is not None
        for line in proc.stdout:
            job.log(line)
        code = proc.wait()
        if code != 0:
            job.log(f"[error] gradlew build 失败，exit code {code}")
            job.done, job.ok = True, False
            return

        jar = newest("meteorosprotocol-*.jar", LIB_DIR)
        if jar is None:
            job.log("[error] 构建成功但 build/libs 里找不到 jar")
            job.done, job.ok = True, False
            return
        with zipfile.ZipFile(jar) as zf:
            entries = len(zf.namelist())
        jar_stat = jar.stat()
        job.log(f"[info] jar: {jar.name}  {jar_stat.st_size:,} B  "
                f"{time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(jar_stat.st_mtime))}  "
                f"entries={entries}")

        job.log("[step 2/2] make-test-jar.ps1 ...")
        ps = subprocess.Popen(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
             "-File", str(ROOT / "tools" / "make-test-jar.ps1")],
            cwd=str(ROOT), env=env, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
        )
        assert ps.stdout is not None
        for line in ps.stdout:
            job.log(line)
        code = ps.wait()
        if code != 0:
            job.log(f"[warn] make-test-jar.ps1 退出码 {code}（jar 已产出，可手动再跑一次）")

        test_jar = newest("meteorosprotocol-*-test.jar", TEST_MODS)
        result = {
            "jar": jar.name,
            "jarBytes": jar_stat.st_size,
            "jarTime": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(jar_stat.st_mtime)),
            "entries": entries,
            "testJar": test_jar.name if test_jar else None,
            "testJarBytes": test_jar.stat().st_size if test_jar else None,
        }
        job.result = result
        job.ok = True
        job.log("[done] " + json.dumps(result, ensure_ascii=False))
    except Exception as exc:  # noqa: BLE001 - 服务不能因为构建异常挂掉
        job.log(f"[error] {type(exc).__name__}: {exc}")
        job.ok = False
    finally:
        job.done = True


# ---------------------------------------------------------------- validation


def rect_of(el: dict) -> tuple[int, int, int, int]:
    """返回元素在布局坐标里的占位矩形（用于越界/重叠校验）。"""
    etype = el.get("type")
    x, y = int(el.get("x", 0)), int(el.get("y", 0))
    if etype == "inventory":
        rows = [int(r) for r in (el.get("rows") or [])]
        step = int(el.get("step", 18))
        w = 9 * step
        label_y = int((el.get("label") or [x, y])[1])
        y0 = min([label_y] + rows) if (rows or label_y) else y
        y1 = max(rows + [y0]) + step
        return x, y0, w, y1 - y0
    if etype == "slot":
        return x, y, 18, 18
    if etype == "icon_button":
        s = int(el.get("size", 20))
        return x, y, s, s
    if etype == "phase_rows":
        step = int(el.get("step", 11))
        vis = int(el.get("visible", 1))
        return x, y, int(el.get("w", 0)), max(int(el.get("h", 10)), step * vis)
    # title / text 宽度由文字决定，不参与尺寸校验
    return x, y, int(el.get("w", 0)), int(el.get("h", 0))


def validate(layout: dict) -> list[str]:
    errors: list[str] = []
    if not isinstance(layout, dict):
        return ["根节点必须是对象"]
    if int(layout.get("schema", 0)) != 1:
        errors.append(f"schema 必须是 1，当前 {layout.get('schema')!r}")
    size = layout.get("size") or []
    if not (isinstance(size, list) and len(size) == 2):
        return errors + ["size 必须是 [w, h]"]
    bw, bh = int(size[0]), int(size[1])
    if bw < 64 or bh < 64:
        errors.append(f"size 太小：{bw}x{bh}")
    elements = layout.get("elements")
    if not isinstance(elements, list) or not elements:
        return errors + ["elements 必须是非空数组"]

    seen_actions: set[str] = set()
    seen_roles: set[str] = set()
    boxes: list[tuple[str, tuple[int, int, int, int]]] = []

    for i, el in enumerate(elements):
        if not isinstance(el, dict):
            errors.append(f"elements[{i}] 不是对象")
            continue
        etype = el.get("type")
        if etype not in VALID_TYPES:
            errors.append(f"elements[{i}] 未知 type {etype!r}")
            continue
        x, y, w, h = rect_of(el)
        texty = etype in {"title", "text"}
        if not texty:
            if w <= 0 or h <= 0:
                errors.append(f"elements[{i}] ({etype}) 尺寸非正：{w}x{h}")
            if x < 0 or y < 0 or x + w > bw or y + h > bh:
                errors.append(
                    f"elements[{i}] ({etype}) 超出边界：({x},{y}) {w}x{h} > {bw}x{bh}")
        elif x < 0 or y < 0 or y + 10 > bh or x >= bw:
            errors.append(f"elements[{i}] ({etype}) 起点越界：({x},{y})")
        if etype == "phase_rows":
            if el.get("scrollbar"):
                sb = el["scrollbar"]
                if int(sb.get("h", 0)) <= 0 or int(sb.get("w", 0)) <= 0:
                    errors.append(f"elements[{i}] (phase_rows) scrollbar 尺寸非正")
                elif (int(sb["x"]) + int(sb["w"]) > bw
                      or int(sb["y"]) + int(sb["h"]) > bh):
                    errors.append(f"elements[{i}] (phase_rows) scrollbar 超出界面边界")
            boxes.append((etype, (x, y, w, h)))
        elif etype in {"bar_energy", "bar_phase", "slot", "icon_button", "text", "title", "spacer"}:
            boxes.append((etype, (x, y, w, h)))
        if etype == "icon_button":
            action = el.get("action")
            if action not in VALID_ACTIONS:
                errors.append(f"elements[{i}] 未知 action {action!r}")
            if action in seen_actions:
                errors.append(f"action {action!r} 重复")
            seen_actions.add(action)
        if etype == "slot":
            role = el.get("role")
            if role not in VALID_ROLES:
                errors.append(f"elements[{i}] 未知 slot role {role!r}")
            if role in seen_roles:
                errors.append(f"slot role {role!r} 重复")
            seen_roles.add(role)

    # 规则 3：phase_rows 与其它元素不重叠
    rows = [b for b in boxes if b[0] == "phase_rows"]
    others = [b for b in boxes if b[0] != "phase_rows"]

    def overlap(a, b) -> bool:
        ax, ay, aw, ah = a
        bx, by, bw2, bh2 = b
        return ax < bx + bw2 and bx < ax + aw and ay < by + bh2 and by < ay + ah

    for _, ra in rows:
        for etype, rb in others:
            if etype == "title":
                continue
            if overlap(ra, rb):
                errors.append(f"phase_rows 与 {etype} 重叠：{ra} vs {rb}")
    return errors


def load_layout(screen: str) -> tuple[dict | None, str | None]:
    path = GUI_DIR / f"{screen}.json"
    if not path.is_file():
        return None, f"缺少布局文件 {path.name}"
    try:
        return json.loads(path.read_text(encoding="utf-8")), None
    except json.JSONDecodeError as exc:
        return None, f"{path.name} JSON 解析失败：{exc}"


NUM_ARRAY = re.compile(r"\[\s*(-?\d+(?:\s*,\s*-?\d+)*)\s*\]")


def pretty_layout(layout: dict) -> str:
    """缩进 2 空格的 JSON，但把纯数字数组（size/label/rows）压成一行，便于 diff。"""
    raw = json.dumps(layout, ensure_ascii=False, indent=2)
    raw = NUM_ARRAY.sub(lambda m: "[" + re.sub(r"\s+", " ", m.group(1)) + "]", raw)
    return raw + "\n"


def save_layout(screen: str, layout: dict) -> tuple[bool, list[str]]:
    errors = validate(layout)
    if errors:
        return False, errors
    path = GUI_DIR / f"{screen}.json"
    path.write_text(pretty_layout(layout), encoding="utf-8")
    return True, []


# ------------------------------------------------------------------ handler


class Handler(BaseHTTPRequestHandler):
    server_version = "MeteorosGuiEditor/1.0"

    def log_message(self, fmt: str, *args) -> None:  # 安静一点
        sys.stdout.write("[http] " + fmt % args + "\n")

    # -- helpers
    def send_json(self, code: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def send_file(self, path: Path) -> None:
        if not path.is_file():
            self.send_json(404, {"ok": False, "error": "not found"})
            return
        data = path.read_bytes()
        ctype = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        if path.suffix == ".js":
            ctype = "text/javascript"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def preview_for(self, screen: str) -> Path | None:
        """art/gui/preview_<id>_<w>x<h>.png；缺文件或比 JSON 旧时自动重跑 preview_gui_mock.py。"""
        layout, _ = load_layout(screen)
        if layout is None:
            return None
        size = layout.get("size") or [176, 224]
        try:
            if isinstance(size, dict):
                w, h = int(size.get("w", 176)), int(size.get("h", 224))
            else:
                w, h = int(size[0]), int(size[1])
        except (IndexError, TypeError, ValueError):
            w, h = 176, 224
        preview = ROOT / "art" / "gui" / f"preview_{screen}_{w}x{h}.png"
        json_path = GUI_DIR / f"{screen}.json"
        stale = (not preview.is_file()) or (
            json_path.is_file() and preview.stat().st_mtime < json_path.stat().st_mtime
        )
        if stale:
            try:
                subprocess.run(
                    [sys.executable, str(ROOT / "tools" / "preview_gui_mock.py"), screen],
                    cwd=str(ROOT),
                    capture_output=True,
                    timeout=120,
                    check=False,
                )
            except (OSError, subprocess.SubprocessError):
                pass
        return preview if preview.is_file() else None

    def do_GET(self) -> None:  # noqa: N802
        url = urlparse(self.path)
        path = url.path
        if path in ("/", "/index.html"):
            self.send_file(EDITOR_DIR / "index.html")
            return
        if path.startswith("/api/layout/"):
            screen = path.rsplit("/", 1)[-1]
            if not re.fullmatch(r"[a-z0-9_]+", screen):
                self.send_json(400, {"ok": False, "error": "bad screen id"})
                return
            layout, err = load_layout(screen)
            if layout is None:
                self.send_json(404, {"ok": False, "error": err})
                return
            self.send_json(200, {"ok": True, "layout": layout})
            return
        if path == "/api/screens":
            ids = sorted(p.stem for p in GUI_DIR.glob("*.json"))
            self.send_json(200, {"ok": True, "screens": ids})
            return
        if path == "/api/build/log":
            q = parse_qs(url.query)
            job_id = (q.get("job") or [""])[0]
            try:
                since = int((q.get("since") or ["0"])[0])
            except ValueError:
                since = 0
            job = JOBS.get(job_id)
            if job is None:
                self.send_json(404, {"ok": False, "error": "unknown job"})
                return
            self.send_json(200, job.snapshot(since))
            return
        if path.startswith("/assets/icons/"):
            name = path.rsplit("/", 1)[-1]
            if not re.fullmatch(r"[A-Za-z0-9_]+\.png", name):
                self.send_json(400, {"ok": False, "error": "bad icon name"})
                return
            self.send_file(ICON_DIR / name)
            return
        if path.startswith("/assets/preview/"):
            name = path.rsplit("/", 1)[-1]
            if not re.fullmatch(r"[a-z0-9_]+\.png", name):
                self.send_json(400, {"ok": False, "error": "bad preview name"})
                return
            screen = name[: -len(".png")]
            preview = self.preview_for(screen)
            if preview is None:
                self.send_json(404, {"ok": False, "error": "no preview for " + screen})
                return
            self.send_file(preview)
            return
        # 静态资源
        rel = path.lstrip("/")
        target = (EDITOR_DIR / rel).resolve()
        try:
            target.relative_to(EDITOR_DIR.resolve())
        except ValueError:
            self.send_json(403, {"ok": False, "error": "forbidden"})
            return
        self.send_file(target)

    def do_POST(self) -> None:  # noqa: N802
        url = urlparse(self.path)
        path = url.path
        if path.startswith("/api/layout/"):
            screen = path.rsplit("/", 1)[-1]
            if not re.fullmatch(r"[a-z0-9_]+", screen):
                self.send_json(400, {"ok": False, "error": "bad screen id"})
                return
            try:
                layout = json.loads(self.read_body().decode("utf-8"))
            except (json.JSONDecodeError, UnicodeDecodeError) as exc:
                self.send_json(400, {"ok": False, "errors": [f"请求体不是合法 JSON：{exc}"]})
                return
            if layout.get("id") != screen:
                self.send_json(400, {"ok": False,
                                     "errors": [f"layout.id={layout.get('id')!r} 与 URL 不一致"]})
                return
            ok, errors = save_layout(screen, layout)
            self.send_json(200 if ok else 400, {"ok": ok, "errors": errors})
            return
        if path == "/api/build":
            job = Job()
            JOBS[job.id] = job
            threading.Thread(target=run_job, args=(job,), daemon=True).start()
            self.send_json(200, {"ok": True, "job": job.id})
            return
        self.send_json(404, {"ok": False, "error": "not found"})


def main() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    if not GUI_DIR.is_dir():
        print(f"[fatal] 找不到 {GUI_DIR}", file=sys.stderr)
        raise SystemExit(1)
    for d in (ROOT / "build" / "libs",):
        d.mkdir(parents=True, exist_ok=True)

    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Meteoros GUI editor: http://{args.host}:{args.port}/")
    print(f"  layouts : {GUI_DIR}")
    print(f"  icons   : {ICON_DIR}")
    print(f"  mods    : {TEST_MODS}")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\nbye")


if __name__ == "__main__":
    main()
