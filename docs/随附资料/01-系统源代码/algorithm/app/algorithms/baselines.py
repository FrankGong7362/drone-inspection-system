"""基线算法：贪心与最近邻。作为论文中与 GA/ACO 对比的基准，已可直接运行。"""
import math

from app.schemas import PlanRequest
from app.algorithms.avoid import obstacle_distance_matrix

EARTH_RADIUS_KM = 6371.0


def _dist(p1, p2) -> float:
    """Haversine 球面距离（km）。"""
    lat1, lon1, lat2, lon2 = map(math.radians, [p1.lat, p1.lng, p2.lat, p2.lng])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(a))


def _check_range(req: PlanRequest, total: float) -> bool:
    """续航约束校验：超过 max_flight_range_km 判为不可行。"""
    c = req.constraints
    if c is not None and c.max_flight_range_km is not None:
        return total <= c.max_flight_range_km
    return True


def greedy(req: PlanRequest):
    """贪心：用避障距离矩阵做最近邻选点（无禁飞区时自动直线）。"""
    zones = list(req.constraints.no_fly_zones or []) if req.constraints else []
    buffer_km = (req.constraints.no_fly_buffer_km
                 if req.constraints and req.constraints.no_fly_buffer_km is not None else 0.5)
    dm = obstacle_distance_matrix(req.start_point, req.waypoints, zones, buffer_km=buffer_km)
    if dm is None:
        return [req.start_point.id], 0.0, False, [], "禁飞区阻隔，无法构建可行距离矩阵"

    nodes = [req.start_point] + list(req.waypoints)
    n = len(nodes)
    visited = [False] * n
    visited[0] = True
    order = [req.start_point.id]
    total = 0.0
    cur = 0
    for _ in range(n - 1):
        nxt = min((j for j in range(1, n) if not visited[j]), key=lambda j: dm[cur][j])
        total += dm[cur][nxt]
        order.append(nodes[nxt].id)
        visited[nxt] = True
        cur = nxt
    total += dm[cur][0]  # 回到起点
    order.append(req.start_point.id)
    return order, round(total, 2), _check_range(req, total), []


def nearest(req: PlanRequest):
    """最近邻：从起点出发，每次选最近点，等价于贪心；此处作为独立基线保留接口。"""
    return greedy(req)
