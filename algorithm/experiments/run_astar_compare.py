"""标准 A* 与改进 A* 的段级对比实验（论文第 5 章"改进 A* 有效性"一节）。

对比两种配置：
- 标准 A*：w=1，无视线平滑，对角步不检查角点；
- 改进 A*：w=1.5 加权启发 + 防角点穿越 + 视线平滑。

指标：扩展节点数、单段耗时、路径长度、路径节点数、转折点数、最小安全余量。
所有路径长度统一用同一栅格上的球面长度重算，保证口径一致。

用法（在 algorithm 目录下执行）：
    python experiments/run_astar_compare.py
输出：experiments/results/astar_compare_raw.csv / astar_compare_summary.md
"""
import csv
import math
import os
import random
import statistics
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from app.algorithms.avoid import (ASTAR_WEIGHT, AvoidGrid, _haversine,  # noqa: E402
                                 segment_crosses_zone)
from app.schemas import NoFlyZone  # noqa: E402

CENTER = (121.50, 31.23)
ZONES = [
    NoFlyZone(center_lng=121.515, center_lat=31.240, radius_km=0.5),
    NoFlyZone(center_lng=121.488, center_lat=31.222, radius_km=0.3),
]
BUFFER_KM = 0.5
PAIRS = 30


def outside_safe_zone(lat, lng) -> bool:
    return all(_haversine(lat, lng, z.center_lat, z.center_lng)
               >= z.radius_km + BUFFER_KM + 0.3 for z in ZONES)


def gen_crossing_pairs(count: int):
    """生成"直线穿过禁飞区"的起终点对，保证端点位于安全距离之外。"""
    pairs = []
    seed = 0
    while len(pairs) < count:
        seed += 1
        rng = random.Random(seed)
        for _ in range(200):
            p = (CENTER[1] + rng.uniform(-0.015, 0.015),
                 CENTER[0] + rng.uniform(-0.030, 0.030))   # (lat, lng)
            q = (CENTER[1] + rng.uniform(-0.015, 0.015),
                 CENTER[0] + rng.uniform(-0.030, 0.030))
            if not (outside_safe_zone(*p) and outside_safe_zone(*q)):
                continue
            if segment_crosses_zone(p, q, ZONES, BUFFER_KM):
                pairs.append((seed, p, q))
                break
        if seed > 5000:
            break
    return pairs


def turns(path) -> int:
    count = 0
    for a, b, c in zip(path, path[1:], path[2:]):
        if (b[0] - a[0], b[1] - a[1]) != (c[0] - b[0], c[1] - b[1]):
            count += 1
    return count


def min_clearance_km(path, grid) -> float:
    """路径节点到禁飞区边缘的最小距离（km），应不小于 buffer。"""
    best = math.inf
    for (c, r) in path:
        lat = grid.min_lat + r * grid.cell
        lng = grid.min_lng + c * grid.cell
        for z in ZONES:
            best = min(best, _haversine(lat, lng, z.center_lat, z.center_lng) - z.radius_km)
    return best


def main() -> None:
    out_dir = os.path.join(ROOT, "experiments", "results")
    os.makedirs(out_dir, exist_ok=True)
    pairs = gen_crossing_pairs(PAIRS)
    all_points = [(p[1], p[0]) for _, p, q in pairs] + [(q[1], q[0]) for _, p, q in pairs]
    grid = AvoidGrid(ZONES, all_points, buffer_km=BUFFER_KM)

    rows = []
    for idx, (seed, p, q) in enumerate(pairs, start=1):
        direct_km = _haversine(p[0], p[1], q[0], q[1])
        for variant, kw in (("standard", {"weight": 1.0, "smooth": False}),
                            ("improved", {"weight": ASTAR_WEIGHT, "smooth": True})):
            stats = {}
            t0 = time.perf_counter()
            dist, path = grid.astar((p[1], p[0]), (q[1], q[0]), stats=stats, **kw)
            runtime_ms = (time.perf_counter() - t0) * 1000.0
            if path is None:
                print(f"pair {idx} {variant}: 不可达，跳过")
                continue
            rows.append({
                "pair": idx,
                "seed": seed,
                "variant": variant,
                "expanded": stats.get("expanded", 0),
                "runtime_ms": round(runtime_ms, 3),
                "nodes": len(path),
                "turns": turns(path),
                "path_km": round(grid._path_length_km(path), 4),
                "direct_km": round(direct_km, 4),
                "min_clearance_km": round(min_clearance_km(path, grid), 4),
            })
        print(f"[{idx}/{len(pairs)}] pair done", flush=True)

    with open(os.path.join(out_dir, "astar_compare_raw.csv"), "w", newline="",
              encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    def agg(variant, key):
        vals = [r[key] for r in rows if r["variant"] == variant]
        return statistics.mean(vals), statistics.pstdev(vals), min(vals), max(vals)

    def line(key, label, fmt="{:.2f}"):
        std = agg("standard", key)
        imp = agg("improved", key)
        delta = (std[0] - imp[0]) / std[0] * 100 if std[0] else 0.0
        sign = "↓" if delta > 0 else ("↑" if delta < 0 else "=")
        return (f"| {label} | {fmt.format(std[0])} ± {fmt.format(std[1])} "
                f"| {fmt.format(imp[0])} ± {fmt.format(imp[1])} | {sign} {abs(delta):.1f}% |")

    lines = [
        "# 标准 A* 与改进 A* 段级对比\n",
        f"- 测试样本：{len(pairs)} 条直线穿越禁飞区的航段（固定随机种子生成）",
        f"- 改进配置：启发权重 w={ASTAR_WEIGHT}、防角点穿越、视线平滑",
        f"- 禁飞区：2 个圆（r=0.5km / 0.3km），安全距离 buffer={BUFFER_KM}km",
        "- 路径长度：同一栅格上的球面长度重算，口径一致\n",
        "| 指标 | 标准 A*（mean±std） | 改进 A*（mean±std） | 变化 |",
        "|---|---|---|---|",
        line("expanded", "扩展节点数", "{:.0f}"),
        line("runtime_ms", "单段耗时（ms）", "{:.3f}"),
        line("path_km", "路径长度（km）", "{:.3f}"),
        line("nodes", "路径节点数", "{:.1f}"),
        line("turns", "转折点数", "{:.1f}"),
    ]
    std_clear = agg("standard", "min_clearance_km")
    imp_clear = agg("improved", "min_clearance_km")
    lines += [
        "",
        f"- 最小安全余量（距禁飞区圆的最近距离）：标准 {std_clear[2]:.3f} km，"
        f"改进 {imp_clear[2]:.3f} km（均须 ≥ buffer {BUFFER_KM}km）",
        f"- 路径长度相对直线距离的平均绕行率：标准 "
        f"{statistics.mean(r['path_km'] / r['direct_km'] for r in rows if r['variant'] == 'standard'):.2f}×，改进 "
        f"{statistics.mean(r['path_km'] / r['direct_km'] for r in rows if r['variant'] == 'improved'):.2f}×",
    ]
    with open(os.path.join(out_dir, "astar_compare_summary.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print("\n".join(lines))


if __name__ == "__main__":
    main()
