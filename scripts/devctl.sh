#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
RUN_DIR="$ROOT_DIR/.run"
mkdir -p "$RUN_DIR"

BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"
BACKEND_LOG="$RUN_DIR/backend.log"
FRONTEND_LOG="$RUN_DIR/frontend.log"
BACKEND_JAR="$BACKEND_DIR/target/backend-1.0.0.jar"

COMPOSE_CMD=""
LAN_IP_VALUE=""

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}

fail() {
  printf "\n❌ %s\n" "$*" >&2
  exit 1
}

resolve_compose() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
    return 0
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
    return 0
  fi
  COMPOSE_CMD=""
  return 1
}

compose() {
  # shellcheck disable=SC2086
  $COMPOSE_CMD "$@"
}

is_pid_running() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] || return 1
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  [[ -n "$pid" ]] || return 1
  ps -p "$pid" >/dev/null 2>&1
}

port_open() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

kill_port() {
  local port="$1"
  if port_open "$port"; then
    lsof -tiTCP:"$port" -sTCP:LISTEN | xargs -I{} kill {} >/dev/null 2>&1 || true
    sleep 1
    if port_open "$port"; then
      lsof -tiTCP:"$port" -sTCP:LISTEN | xargs -I{} kill -9 {} >/dev/null 2>&1 || true
    fi
  fi
}

kill_pid_file() {
  local pid_file="$1"
  local name="$2"
  if is_pid_running "$pid_file"; then
    local pid
    pid="$(cat "$pid_file")"
    log "停止 ${name} (PID: $pid)"
    kill "$pid" >/dev/null 2>&1 || true

    for _ in {1..20}; do
      if ! ps -p "$pid" >/dev/null 2>&1; then
        break
      fi
      sleep 0.3
    done

    if ps -p "$pid" >/dev/null 2>&1; then
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  fi
  rm -f "$pid_file"
}

pick_private_ip() {
  ifconfig | awk '/inet /{print $2}' | while read -r ip; do
    [[ "$ip" == 127.* ]] && continue
    [[ "$ip" == 169.254.* ]] && continue
    if [[ "$ip" =~ ^10\. ]] || [[ "$ip" =~ ^192\.168\. ]] || [[ "$ip" =~ ^172\.(1[6-9]|2[0-9]|3[0-1])\. ]]; then
      echo "$ip"
      return 0
    fi
  done
}

detect_lan_ip() {
  if [[ -n "${LAN_IP:-}" ]]; then
    echo "$LAN_IP"
    return 0
  fi

  local ip=""

  # macOS: use default route interface first (usually Wi-Fi)
  if command -v route >/dev/null 2>&1; then
    local iface
    iface="$(route -n get default 2>/dev/null | awk '/interface:/{print $2}' || true)"
    if [[ -n "$iface" ]] && command -v ipconfig >/dev/null 2>&1; then
      ip="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    fi
  fi

  # fallback common macOS interfaces
  if [[ -z "$ip" ]] && command -v ipconfig >/dev/null 2>&1; then
    ip="$(ipconfig getifaddr en0 2>/dev/null || true)"
    [[ -z "$ip" ]] && ip="$(ipconfig getifaddr en1 2>/dev/null || true)"
  fi

  # prefer a private address if current result is empty/non-private
  if [[ -z "$ip" ]] || ! ([[ "$ip" =~ ^10\. ]] || [[ "$ip" =~ ^192\.168\. ]] || [[ "$ip" =~ ^172\.(1[6-9]|2[0-9]|3[0-1])\. ]]); then
    ip="$(pick_private_ip | head -n 1 || true)"
  fi

  [[ -n "$ip" ]] || fail "无法自动识别局域网 IP。请手动执行：LAN_IP=192.168.x.x ./scripts/devctl.sh up"
  echo "$ip"
}

wait_http_ready() {
  local url="$1"
  local max_sec="$2"
  local name="$3"

  for ((i = 1; i <= max_sec; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "${name} 就绪: $url"
      return 0
    fi
    sleep 1
  done

  return 1
}

wait_redis_ready() {
  for _ in {1..30}; do
    if compose exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; then
      log "Redis 已就绪"
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_postgres_ready() {
  for _ in {1..45}; do
    if compose exec -T postgres pg_isready -U wuziqi -d wuziqi >/dev/null 2>&1; then
      log "PostgreSQL 已就绪"
      return 0
    fi
    sleep 1
  done
  return 1
}

seed_images_with_mirror() {
  log "尝试镜像加速拉取（docker.m.daocloud.io）..."
  docker pull docker.m.daocloud.io/library/postgres:16
  docker pull docker.m.daocloud.io/library/redis:7-alpine
  docker tag docker.m.daocloud.io/library/postgres:16 postgres:16
  docker tag docker.m.daocloud.io/library/redis:7-alpine redis:7-alpine
}

start_deps() {
  resolve_compose || fail "未检测到 Docker Compose。请先安装 Docker Desktop 并确保可执行 docker compose。"

  log "启动 PostgreSQL + Redis..."
  if ! compose up -d; then
    log "首次拉取失败，可能是 Docker Hub 网络超时。"
    seed_images_with_mirror || fail "镜像加速拉取也失败，请检查网络/代理后重试。"
    compose up -d
  fi

  wait_postgres_ready || fail "PostgreSQL 启动超时，请检查: ${COMPOSE_CMD} logs postgres"
  wait_redis_ready || fail "Redis 启动超时，请检查: ${COMPOSE_CMD} logs redis"
}

start_backend() {
  if port_open 3000; then
    log "检测到 Backend 已在运行，先重启以应用最新代码..."
    kill_pid_file "$BACKEND_PID_FILE" "Backend"
    kill_port 3000
  fi

  [[ -f "$BACKEND_DIR/.env" ]] || cp "$BACKEND_DIR/.env.example" "$BACKEND_DIR/.env"

  log "构建后端 JAR..."
  (cd "$BACKEND_DIR" && mvn -q -DskipTests package)

  log "启动后端 (Java -jar)..."
  (
    cd "$BACKEND_DIR"
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
    export CORS_ORIGIN="http://${LAN_IP_VALUE}:5173"
    export REDIS_ENABLED=true
    nohup java -jar "$BACKEND_JAR" >"$BACKEND_LOG" 2>&1 &
    echo $! >"$BACKEND_PID_FILE"
  )

  if ! wait_http_ready "http://127.0.0.1:3000/health" 90 "Backend"; then
    tail -n 120 "$BACKEND_LOG" || true
    fail "Backend 未能启动成功。"
  fi
}

start_frontend() {
  if port_open 5173; then
    log "检测到 Frontend 已在运行，先重启以应用最新页面代码..."
    kill_pid_file "$FRONTEND_PID_FILE" "Frontend"
    kill_port 5173
  fi

  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    log "安装前端依赖（首次）..."
    (cd "$FRONTEND_DIR" && npm install)
  fi

  log "启动前端 Vite..."
  (
    cd "$FRONTEND_DIR"
    nohup env \
      VITE_API_BASE="http://${LAN_IP_VALUE}:3000" \
      VITE_WS_BASE="ws://${LAN_IP_VALUE}:3000/ws" \
      ./node_modules/.bin/vite --host 0.0.0.0 --port 5173 >"$FRONTEND_LOG" 2>&1 &
    echo $! >"$FRONTEND_PID_FILE"
  )

  if ! wait_http_ready "http://127.0.0.1:5173" 45 "Frontend"; then
    tail -n 120 "$FRONTEND_LOG" || true
    fail "Frontend 未能启动成功。"
  fi
}

print_summary() {
  cat <<EOF

✅ 一键启动完成

- 后端健康检查: http://127.0.0.1:3000/health
- 电脑访问前端: http://127.0.0.1:5173
- 手机访问前端: http://${LAN_IP_VALUE}:5173

日志文件：
- Backend:  $BACKEND_LOG
- Frontend: $FRONTEND_LOG

常用命令：
- 停止全部: ./scripts/devctl.sh down
- 查看状态: ./scripts/devctl.sh status
- 查看日志: ./scripts/devctl.sh logs
EOF
}

status_all() {
  echo "== 服务状态 =="
  if port_open 3000; then
    echo "Backend:  RUNNING (port 3000)"
  else
    echo "Backend:  STOPPED"
  fi

  if port_open 5173; then
    echo "Frontend: RUNNING (port 5173)"
  else
    echo "Frontend: STOPPED"
  fi

  echo
  echo "== 健康检查 =="
  if curl -fsS http://127.0.0.1:3000/health >/dev/null 2>&1; then
    echo "Backend health: OK"
  else
    echo "Backend health: FAIL"
  fi

  if curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; then
    echo "Frontend health: OK"
  else
    echo "Frontend health: FAIL"
  fi

  if resolve_compose; then
    echo
    echo "== Docker 依赖 =="
    compose ps
  fi
}

logs_all() {
  echo "== Backend 日志 (tail 100) =="
  tail -n 100 "$BACKEND_LOG" 2>/dev/null || echo "(暂无 backend 日志)"
  echo
  echo "== Frontend 日志 (tail 100) =="
  tail -n 100 "$FRONTEND_LOG" 2>/dev/null || echo "(暂无 frontend 日志)"
}

up() {
  LAN_IP_VALUE="$(detect_lan_ip)"
  log "使用局域网 IP: ${LAN_IP_VALUE}"

  start_deps
  start_backend
  start_frontend
  print_summary
}

down() {
  kill_pid_file "$FRONTEND_PID_FILE" "Frontend"
  kill_pid_file "$BACKEND_PID_FILE" "Backend"

  kill_port 5173
  kill_port 3000

  if resolve_compose; then
    log "停止 PostgreSQL + Redis..."
    compose down
  fi

  log "全部服务已停止"
}

usage() {
  cat <<'EOF'
用法: ./scripts/devctl.sh [up|down|status|logs]

命令：
  up      一键启动（默认）
  down    停止前后端 + PostgreSQL + Redis
  status  查看服务状态
  logs    查看 backend/frontend 日志尾部

可选环境变量：
  LAN_IP=192.168.1.23   手动指定手机访问 IP（自动识别失败时可用）
EOF
}

main() {
  local cmd="${1:-up}"
  case "$cmd" in
    up)
      up
      ;;
    down)
      down
      ;;
    status)
      status_all
      ;;
    logs)
      logs_all
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
