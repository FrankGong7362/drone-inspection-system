"""模拟数据生成脚本（固定随机种子，结果可复现）。

生成并写入 data/ 目录：
  - waypoints.csv      巡检航点（模拟某园区/电力线路区域）
  - drones.csv         无人机档案参数
  - no_fly_zones.csv   禁飞区（圆心 + 半径）

运行：python gen_data.py
"""
import csv
import random
from pathlib import Path

# 固定种子，保证论文实验可复现
random.seed(42)

# 模拟巡检区域：上海浦东某区域中心（示例坐标，可自行替换为真实场景）
REGION_CENTER = (121.50, 31.23)   # (lng, lat)
SPAN_LNG, SPAN_LAT = 0.04, 0.03   # 覆盖范围（度）

DATA_DIR = Path(__file__).parent
WAYPOINT_COUNT = 20


def gen_waypoints(n: int):
    rows = []
    for i in range(1, n + 1):
        lng = REGION_CENTER[0] + random.uniform(-SPAN_LNG / 2, SPAN_LNG / 2)
        lat = REGION_CENTER[1] + random.uniform(-SPAN_LAT / 2, SPAN_LAT / 2)
        rows.append({"id": i, "name": f"巡检点{i}", "lng": round(lng, 6), "lat": round(lat, 6)})
    return rows


def gen_drones():
    return [
        {"drone_no": "DR-001", "model": "示例型A", "max_flight_range_km": 20.0, "max_speed_kmh": 60.0},
        {"drone_no": "DR-002", "model": "示例型B", "max_flight_range_km": 15.0, "max_speed_kmh": 55.0},
    ]


def gen_no_fly_zones():
    return [
        {"name": "禁飞区1", "center_lng": round(REGION_CENTER[0] + 0.015, 6),
         "center_lat": round(REGION_CENTER[1] + 0.010, 6), "radius_km": 0.5},
        {"name": "禁飞区2", "center_lng": round(REGION_CENTER[0] - 0.012, 6),
         "center_lat": round(REGION_CENTER[1] - 0.008, 6), "radius_km": 0.3},
    ]


def write_csv(filename, fieldnames, rows):
    path = DATA_DIR / filename
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"生成 {path}")


if __name__ == "__main__":
    write_csv("waypoints.csv", ["id", "name", "lng", "lat"], gen_waypoints(WAYPOINT_COUNT))
    write_csv("drones.csv", ["drone_no", "model", "max_flight_range_km", "max_speed_kmh"], gen_drones())
    write_csv("no_fly_zones.csv", ["name", "center_lng", "center_lat", "radius_km"], gen_no_fly_zones())
    print("模拟数据生成完成（seed=42，可复现）")
