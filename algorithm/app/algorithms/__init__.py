"""路径规划算法集合。

统一约定：每个函数接收 PlanRequest，返回 (route: List[int], distance: float, feasible: bool)。
route 为航点 id 的访问顺序（含起点与回到起点的往返，元素为 waypoint.id）。
"""
from app.schemas import PlanRequest

from . import ga, aco, astar, baselines
from .baselines import greedy, nearest

__all__ = ["ga", "aco", "astar", "baselines", "genetic", "ant_colony", "astar_algo", "greedy", "nearest"]


def genetic(req: PlanRequest):
    return ga.solve(req)


def ant_colony(req: PlanRequest):
    return aco.solve(req)


def astar_algo(req: PlanRequest):
    return astar.solve(req)
