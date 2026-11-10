# 毕业设计随附资料清单

课题：无人机巡检任务管理与路径规划系统设计与实现
学生：龚孟恬　学号：240274025　日期：2026-11

## 目录结构

### 01-系统源代码
论文"系统源代码（前端、后端、算法服务）"对应内容。

- `backend/`——Spring Boot 3 + MyBatis-Plus 后端
  - `pom.xml`：Maven 依赖与构建配置（JDK 17）
  - `src/main/java/com/tongji/uav/`：控制器、服务、实体、配置
  - `src/main/resources/`：application.yml 与映射文件
- `frontend/`——Vue 3 + Element Plus + Vite 前端
  - `package.json` / `package-lock.json`：依赖锁定
  - `vite.config.js`、`index.html`
  - `src/`：视图、路由、store、API 封装
- `algorithm/`——FastAPI 算法服务（Python 3.11）
  - `app/main.py`、`app/routers/plan.py`：规划接口
  - `app/algorithms/`：贪心、最近邻、遗传、蚁群、标准/改进 A*
  - `requirements.txt`

### 02-数据库脚本与演示数据
- `schema.sql`：MySQL 8.0 建表脚本（任务、航点、无人机、执行日志、规划结果、禁飞区、用户 7 张表）
- `gen_data.py`：演示数据生成脚本
- `drones.csv`、`waypoints.csv`、`no_fly_zones.csv`：论文实验用演示数据

### 03-实验数据与复现脚本
论文第 6 章对比实验原始数据、汇总与绘图脚本。

- `run_experiment.py`：5 算法标识（4 类方法）× 4 规模 × 多随机种子对比实验
- `run_astar_compare.py`：标准 A* vs 改进 A* 段级专项实验
- `make_charts.py`：由 CSV 生成论文插图
- `raw.csv`、`summary.csv`、`convergence.json`：原始记录与汇总
- `summary.md`、`astar_compare_summary.md`：汇总说明
- `figures/`：论文用图（distance_compare、runtime_compare、convergence、astar_improvement）

### 04-界面截图与测试记录
论文第 4、6 章引用的系统界面与接口测试记录。

- `01-登录页.png` ~ `07-审计日志.png`：对应论文图 4.1–4.8
- `08-接口测试记录原图.png`、`接口测试记录.html`、`接口测试记录-原始数据.json`：对应论文图 6.1

## 运行方式
- 后端：`backend/` 下 `mvn spring-boot:run`（默认 8080）
- 算法服务：`algorithm/` 下 `pip install -r requirements.txt && uvicorn app.main:app --port 8000`
- 前端：`frontend/` 下 `npm install && npm run dev`（默认 5173，代理至 8080）
- 数据库：MySQL 8.0 执行 `schema.sql` 后导入 CSV 演示数据
