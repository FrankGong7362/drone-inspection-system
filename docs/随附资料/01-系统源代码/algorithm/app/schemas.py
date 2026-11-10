"""请求 / 响应数据模型，与后端接口契约一致（见 docs/api-contract.md）。"""
from typing import List, Optional

from pydantic import BaseModel


class Waypoint(BaseModel):
    id: int
    lng: float
    lat: float


class Point(BaseModel):
    id: int
    lng: float
    lat: float


class NoFlyZone(BaseModel):
    center_lng: float
    center_lat: float
    radius_km: float


class Constraint(BaseModel):
    max_flight_range_km: Optional[float] = None
    no_fly_zones: Optional[List[NoFlyZone]] = None
    # 禁飞区安全距离 buffer（km）：路径与禁飞区圆保持的最小余量
    no_fly_buffer_km: Optional[float] = 0.5


class PlanRequest(BaseModel):
    algorithm: str = "ga"          # ga / aco / astar / greedy / nearest
    waypoints: List[Waypoint]
    start_point: Point
    constraints: Optional[Constraint] = None


class PlanResponse(BaseModel):
    status: str
    algorithm: str
    route: List[int]
    total_distance_km: float
    feasible: bool
    run_time_ms: int
    message: str = ""
    # 可选增量字段：每代全局最优总航程（km），GA/ACO 返回，供收敛曲线绘图
    convergence: Optional[List[float]] = None
    # 避障折线：完整航线经纬度序列（Leaflet 顺序 [[lat, lng], ...]），有禁飞区时含弯折
    detail_paths: Optional[List[List[float]]] = None
