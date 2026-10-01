"""无人机巡检路径规划算法服务入口。

启动：uvicorn app.main:app --port 8000
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import plan

app = FastAPI(title="UAV Path Planning Algorithm Service", version="0.0.1")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(plan.router, prefix="/api")


@app.get("/health")
def health():
    return {"status": "ok"}
