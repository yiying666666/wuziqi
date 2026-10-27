# 五子棋 Online（V1）

一个支持 **H5（手机优先）+ PC 浏览器** 的在线五子棋项目：
- 账号密码登录（JWT）
- 房间号创建/加入
- WebSocket 实时对局
- 断线重连（30 秒宽限）
- PostgreSQL 持久化战绩
- Elo 积分排行榜

## 项目结构

- `/Users/jzq/Desktop/wuziqi/backend`：**Java 17 + Spring Boot 3** 后端
- `/Users/jzq/Desktop/wuziqi/frontend`：Vue3 + TypeScript + Vite 前端
- `/Users/jzq/Desktop/wuziqi/backend/sql/init.sql`：数据库初始化脚本
- `/Users/jzq/Desktop/wuziqi/docker-compose.yml`：本地 PostgreSQL + Redis

## 启动数据库与 Redis

```bash
cd /Users/jzq/Desktop/wuziqi
docker compose up -d
```

## 后端启动（Spring Boot）

```bash
cp /Users/jzq/Desktop/wuziqi/backend/.env.example /Users/jzq/Desktop/wuziqi/backend/.env
cd /Users/jzq/Desktop/wuziqi/backend
set -a; source .env; set +a
mvn spring-boot:run
```

默认端口：`3000`

> 如果你不想 source `.env`，也可以直接设置系统环境变量后再运行 `mvn spring-boot:run`。

## 前端启动

```bash
cp /Users/jzq/Desktop/wuziqi/frontend/.env.example /Users/jzq/Desktop/wuziqi/frontend/.env
npm --prefix /Users/jzq/Desktop/wuziqi/frontend install
npm --prefix /Users/jzq/Desktop/wuziqi/frontend run dev
```

默认地址：`http://localhost:5173`


## 一键启动（严格 PostgreSQL + Redis + 手机可测）

```bash
cd /Users/jzq/Desktop/wuziqi
./scripts/devctl.sh up
```

脚本会自动：
- 启动 PostgreSQL + Redis（Docker Compose）
- 自动识别本机局域网 IP
- 启动后端（CORS 自动设置为 `http://<LAN_IP>:5173`）
- 启动前端（API/WS 自动指向 `http://<LAN_IP>:3000`）

常用：
```bash
./scripts/devctl.sh status   # 查看状态
./scripts/devctl.sh logs     # 查看日志
./scripts/devctl.sh down     # 停止全部
```

## 严格 PostgreSQL + Redis 运行（推荐）

1) 启动数据库与缓存（必须）
```bash
cd /Users/jzq/Desktop/wuziqi
docker compose up -d
```

2) 查本机局域网 IP（示例为 macOS）
```bash
ipconfig getifaddr en0
```
假设输出 `192.168.1.23`。

3) 启动后端（使用 PostgreSQL + Redis）
```bash
cp /Users/jzq/Desktop/wuziqi/backend/.env.example /Users/jzq/Desktop/wuziqi/backend/.env
cd /Users/jzq/Desktop/wuziqi/backend
# 关键：CORS 允许手机访问前端地址
export CORS_ORIGIN=http://192.168.1.23:5173
set -a; source .env; set +a
mvn spring-boot:run
```

4) 启动前端（把 API/WS 指向局域网 IP）
```bash
cd /Users/jzq/Desktop/wuziqi/frontend
VITE_API_BASE=http://192.168.1.23:3000 VITE_WS_BASE=ws://192.168.1.23:3000/ws npm run dev -- --host 0.0.0.0 --port 5173
```

5) 手机测试
- 手机与电脑连接同一 Wi-Fi。
- 手机浏览器打开：`http://192.168.1.23:5173`
- **不要使用 `localhost`**：手机里的 localhost 是手机自己，不是你的电脑。

排查：若手机打不开，请检查电脑防火墙是否拦截 5173/3000 端口，或更换到同一网段 Wi-Fi。



## 后端 AI Engine 迁移方案（Spring Boot）

### 1) 接口设计

- `POST /ai/move`（需 `Authorization: Bearer <JWT>`）

请求体（示例）：
```json
{
  "board": [[0,0,0], "... 15x15 ..."],
  "aiColor": 2,
  "humanColor": 1,
  "difficulty": "hard",
  "maxThinkMs": 1800
}
```

响应体（示例）：
```json
{
  "x": 7,
  "y": 8,
  "color": 2,
  "difficulty": "hard",
  "searchedDepth": 3,
  "candidateCount": 9,
  "elapsedMs": 642,
  "reason": "search_hard_nodes_512"
}
```

### 2) 代码骨架

- `backend/src/main/java/com/wuziqi/backend/controller/AiController.java`
  - 负责鉴权与 `POST /ai/move` 路由
- `backend/src/main/java/com/wuziqi/backend/service/AiEngineService.java`
  - AI 核心：候选点、启发式评分、难度分支、alpha-beta 搜索、超时回退
- `backend/src/main/java/com/wuziqi/backend/ai/AiDifficulty.java`
  - 难度参数集中配置
- `backend/src/main/java/com/wuziqi/backend/config/AiExecutorConfig.java`
  - AI 专用线程池
- `backend/src/main/java/com/wuziqi/backend/dto/AiMoveRequest.java`
- `backend/src/main/java/com/wuziqi/backend/dto/AiMoveResponse.java`

### 3) 线程模型

- AI 计算走独立线程池（`aiExecutor`），避免阻塞 Web 请求线程。
- 控制器线程仅等待结果，不执行重计算。
- 采用 `future.get(thinkMs)` 超时控制：
  - 超时后中断当前搜索
  - 自动回退到中等难度启发式落子，保证可用性

### 4) 难度参数表（V1）

| 难度 | depth | branching | randomTopN | randomChance | mustBlockChance | defaultThinkMs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| easy | 1 | 4 | 6 | 0.35 | 0.75 | 300 |
| medium | 2 | 6 | 3 | 0.10 | 1.00 | 800 |
| hard | 3 | 8 | 1 | 0.00 | 1.00 | 1500 |

### 5) 前端对接要点

- 人机模式改为调用 `aiApi.nextMove()`，不再在前端做搜索决策。
- 请求时附带当前棋盘快照与难度参数。
- 保留前端规则校验（越界/重复落子/胜负显示）与结果弹窗体验。

## 已实现接口（REST）

- `POST /auth/register`
- `POST /auth/login`
- `GET /me/profile`（需 JWT）
- `GET /leaderboard?limit=50`
- `GET /rooms/{code}`（最近一局快照）

## 已实现 WebSocket 事件

客户端 -> 服务端：
- `room:create`
- `room:join`
- `game:move`
- `room:leave`

服务端 -> 客户端：
- `room:state`
- `game:start`
- `game:moved`
- `game:invalid_move`
- `game:ended`
- `player:disconnected`
- `player:reconnected`

## 移动端适配点（前端）

- `viewport-fit=cover` + safe-area 内边距
- 按钮最小点击尺寸 44px
- 棋盘触控落子（触屏可用）
- 弱网自动重连，回前台自动恢复连接

## 测试与构建

后端测试：
```bash
cd /Users/jzq/Desktop/wuziqi/backend
mvn test
```

后端打包：
```bash
cd /Users/jzq/Desktop/wuziqi/backend
mvn package
```

前端构建：
```bash
npm --prefix /Users/jzq/Desktop/wuziqi/frontend run build
```

联调自测脚本（需后端、PostgreSQL、Redis 都已启动）：
```bash
cd /Users/jzq/Desktop/wuziqi
./scripts/e2e_smoke.py
```

## 说明

- Redis 用于房间快照与用户房间映射缓存（推荐开启）。
- 如果 Redis 不可用，服务会自动降级为仅内存房间态。
