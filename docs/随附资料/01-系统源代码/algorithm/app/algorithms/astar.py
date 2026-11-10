"""改进 A* 算法：段间避障采用改进 A*（加权启发 + 防角点穿越 + 视线平滑），
巡检顺序采用"最近邻定序 + 2-opt 局部搜索"，在避障航程上逼近最优闭环。

solve 返回 (route, obstacle_total_km, feasible, [])：
- route 含起点往返的 waypoint.id 序列；
- obstacle_total_km 为避障距离闭环总航程（绕开禁飞区）；
- 禁飞区阻隔时返回 5 元组，第 5 位为中文原因。
"""
from typing import List, Optional, Tuple

from app.schemas import PlanRequest
from app.algorithms.avoid import obstacle_distance_matrix
from app.algorithms.ga import _two_opt


def solve(req: PlanRequest) -> Tuple[List[int], float, bool, Optional[List[float]]]:
    start = req.start_point
    waypoints = list(req.waypoints)
    n = len(waypoints)
    if n == 0:
        return [start.id], 0.0, True, []

    zones = list(req.constraints.no_fly_zones or []) if req.constraints else []
    buffer_km = (req.constraints.no_fly_buffer_km
                 if req.constraints and req.constraints.no_fly_buffer_km is not None else 0.5)
    # 改进 A*：加权启发 + 平滑，用于避障距离矩阵
    dm = obstacle_distance_matrix(start, waypoints, zones, buffer_km=buffer_km, improved=True)
    if dm is None:
        return [start.id], 0.0, False, [], "禁飞区阻隔，无法构建可行距离矩阵"

    # 最近邻定序（节点 0=起点，1..n=航点，perm 存航点索引）
    visited = [False] * (n + 1)
    visited[0] = True
    perm: List[int] = []
    total = 0.0
    cur = 0
    for _ in range(n):
        nxt = min((j for j in range(1, n + 1) if not visited[j]), key=lambda j: dm[cur][j])
        total += dm[cur][nxt]
        perm.append(nxt)
        visited[nxt] = True
        cur = nxt
    total += dm[cur][0]  # 回到起点

    # 2-opt 局部搜索：消除最近邻的交叉边，进一步缩短闭环航程
    perm, total = _two_opt(dm, perm, total)

    nodes = [start] + waypoints
    order = [start.id] + [nodes[i].id for i in perm] + [start.id]

    feasible = True
    if req.constraints and req.constraints.max_flight_range_km is not None:
        feasible = total <= req.constraints.max_flight_range_km

    return order, round(total, 2), feasible, []
