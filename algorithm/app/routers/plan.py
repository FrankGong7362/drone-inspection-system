"""路径规划接口：接收请求并按算法分派，返回统一响应。"""
import time

from fastapi import APIRouter, HTTPException

from app import algorithms
from app.algorithms.avoid import build_full_route_polyline
from app.schemas import PlanRequest, PlanResponse

router = APIRouter()


@router.post("/plan", response_model=PlanResponse)
def plan(request: PlanRequest):
    algo = request.algorithm
    factory = {
        "ga": algorithms.genetic,
        "aco": algorithms.ant_colony,
        "astar": algorithms.astar_algo,
        "greedy": algorithms.greedy,
        "nearest": algorithms.nearest,
    }
    if algo not in factory:
        raise HTTPException(status_code=400, detail=f"未知算法: {algo}")

    start = time.time()
    # 各算法返回 3~5 元组：(route, distance, feasible, [convergence], [message])
    result = factory[algo](request)
    route, distance, feasible = result[:3]
    convergence = result[3] if len(result) > 3 else None
    message = result[4] if len(result) > 4 else ""
    elapsed = int((time.time() - start) * 1000)

    # 构建完整避障折线（detail_paths），供前端绘制绕行路径
    zones = list(request.constraints.no_fly_zones or []) if request.constraints else []
    buffer_km = (request.constraints.no_fly_buffer_km
                 if request.constraints and request.constraints.no_fly_buffer_km is not None else 0.5)
    polyline, poly_total, poly_feasible, poly_msg = build_full_route_polyline(
        request.start_point, list(request.waypoints), route, zones, buffer_km=buffer_km
    )
    detail = polyline if polyline else None
    # 算法判不可行（如超续航/禁飞区阻隔）：保留其 message；
    # 折线仍随响应返回（超续航时航线本身可画，前端展示绕行路径）
    if not feasible:
        return PlanResponse(
            status="ok", algorithm=algo, route=route,
            total_distance_km=round(distance, 2), feasible=False,
            run_time_ms=elapsed, message=message,
            convergence=convergence,
            detail_paths=detail,
        )

    return PlanResponse(
        status="ok",
        algorithm=algo,
        route=route,
        total_distance_km=round(distance, 2),
        feasible=poly_feasible,
        run_time_ms=elapsed,
        message=poly_msg,
        convergence=convergence,
        detail_paths=detail,
    )
