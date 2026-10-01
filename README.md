# 基于无人机巡检的任务管理与路径规划系统

毕业设计工程项目。前后端分离 + 独立算法服务，三层可分别开发、调试与部署。

## 目录结构

```
drone-inspection-system/
├── backend/      # Java 后端（Spring Boot 3.x + MyBatis-Plus）
├── frontend/     # 前端（Vue 3 + Element Plus + Leaflet）
├── algorithm/    # 路径规划算法服务（Python + FastAPI）
├── db/           # 数据库建表 SQL 与模拟数据
└── docs/         # 技术文档（接口契约等）
```

## 调用链

前端(Vue) → 后端(Spring Boot, :8080) → 算法服务(FastAPI, :8000)

## 启动顺序

1. 数据库：导入 `db/schema.sql`，配置 `backend/src/main/resources/application.yml` 中的数据库账号密码。
2. 算法服务：`cd algorithm && pip install -r requirements.txt && uvicorn app.main:app --port 8000`
3. 后端：在 Trae 中打开 `backend`，运行 `UavApplication`。
4. 前端：`cd frontend && npm install && npm run dev`

## 技术栈

- 后端：JDK 17 / Spring Boot 3.x / MyBatis-Plus / MySQL 8
- 前端：Vue 3 / Element Plus / Leaflet + 高德或 OSM 瓦片
- 算法：Python 3.10+ / FastAPI / numpy（遗传、蚁群、改进A*、基线贪心/最近邻）

> 模拟数据生成脚本见 `algorithm/data/gen_data.py`（固定随机种子，可复现）。
