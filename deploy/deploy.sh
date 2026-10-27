#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

usage() {
  cat <<'USAGE'
Usage: deploy/deploy.sh <command>

Commands:
  up        构建并后台启动全部服务
  ps        查看容器状态
  logs      跟踪后端 + Nginx 日志
  restart   重启后端与 Nginx
  down      停止容器（保留数据卷）
  backup    备份数据库到当前目录
  shell     进入后端容器
USAGE
}

[[ -f "$ENV_FILE" ]] || { echo "缺少 $ENV_FILE，请先 cp .env.prod.example $ENV_FILE 并填写"; exit 1; }

case "${1:-}" in
  up)
    "${COMPOSE[@]}" up -d --build
    "${COMPOSE[@]}" ps
    ;;
  ps) "${COMPOSE[@]}" ps ;;
  logs) "${COMPOSE[@]}" logs -f --tail=200 backend web ;;
  restart) "${COMPOSE[@]}" restart backend web ;;
  down) "${COMPOSE[@]}" down ;;
  backup)
    out="backup_$(date +%F_%H%M%S).sql"
    "${COMPOSE[@]}" exec -T postgres pg_dump -U "${POSTGRES_USER:-wuziqi}" "${POSTGRES_DB:-wuziqi}" > "$out"
    echo "已备份到 $out"
    ;;
  shell) "${COMPOSE[@]}" exec backend sh ;;
  *) usage; exit 2 ;;
esac
