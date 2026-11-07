#!/usr/bin/env python3
"""GUI 编辑器服务冒烟测试（需要先启动 server.py）。

    python tools/gui-editor/smoke_test.py
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8765"


def call(path: str, payload: dict | None = None) -> tuple[int, dict | bytes]:
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers,
                                 method="POST" if payload is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=9) as rsp:
            body = rsp.read()
            try:
                return rsp.status, json.loads(body)
            except (json.JSONDecodeError, UnicodeDecodeError):
                return rsp.status, body
    except urllib.error.HTTPError as exc:
        body = exc.read()
        try:
            return exc.code, json.loads(body)
        except (json.JSONDecodeError, UnicodeDecodeError):
            return exc.code, body


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    failures = 0

    code, data = call("/api/screens")
    if code != 200 or not isinstance(data, dict):
        print(f"FAIL /api/screens -> {code} {data}")
        return 1
    screens = data["screens"]
    print(f"ok   /api/screens -> {screens}")

    for scr in screens:
        code, data = call(f"/api/layout/{scr}")
        if code != 200 or "layout" not in data:
            print(f"FAIL GET {scr} -> {code} {data}")
            failures += 1
            continue
        layout = data["layout"]
        print(f"ok   GET {scr} -> {len(layout['elements'])} elements, "
              f"{layout['size'][0]}x{layout['size'][1]}")
        code, data = call(f"/api/layout/{scr}", layout)
        if code != 200 or not data.get("ok"):
            print(f"FAIL POST {scr} -> {code} {data}")
            failures += 1
        else:
            print(f"ok   POST {scr} -> 校验通过并写回")

        broken = json.loads(json.dumps(layout))
        broken["elements"][0]["x"] = 400
        code, data = call(f"/api/layout/{scr}", broken)
        if code == 400 and not data.get("ok"):
            print(f"ok   越界布局被拒绝：{data['errors'][:2]}")
        else:
            print(f"FAIL 越界布局居然被接受 -> {code} {data}")
            failures += 1

    code, body = call("/assets/icons/icon_power.png")
    if code == 200 and isinstance(body, (bytes, bytearray)) and body[:4] == b"\x89PNG":
        print(f"ok   图标 -> {len(body)} B")
    else:
        print(f"FAIL 图标 -> {code}")
        failures += 1

    print("\n" + ("ALL OK" if failures == 0 else f"{failures} FAILURE(S)"))
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
