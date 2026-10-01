# -*- coding: utf-8 -*-
"""临时测试脚本：调用本地算法服务 POST /api/plan。"""
import json
import urllib.request

BASE = "http://127.0.0.1:8000"

def post(path, payload):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return resp.status, resp.read().decode("utf-8")

# health
try:
    with urllib.request.urlopen(BASE + "/health", timeout=8) as r:
        print("health:", r.status, r.read().decode())
except Exception as e:
    print("health 失败:", e)

# plan greedy
payload = {
    "algorithm": "greedy",
    "waypoints": [
        {"id": 1, "lng": 121.49, "lat": 31.230},
        {"id": 2, "lng": 121.51, "lat": 31.245},
        {"id": 3, "lng": 121.505, "lat": 31.218},
    ],
    "start_point": {"id": 0, "lng": 121.50, "lat": 31.23},
    "constraints": None,
}
try:
    status, text = post("/api/plan", payload)
    print("plan status:", status)
    print("plan body:", text)
except Exception as e:
    print("plan 失败:", e)
