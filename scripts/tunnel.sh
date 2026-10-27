#!/usr/bin/env bash
set -euo pipefail

PORT="${PORT:-5173}"
PTY_LOG="${PTY_LOG:-/tmp/wuziqi_tunnel_pty.log}"
RUN_LOG="${RUN_LOG:-/tmp/wuziqi_tunnel_runner.log}"
PATTERN='script -q /tmp/wuziqi_tunnel_pty.log ssh -tt -o StrictHostKeyChecking=no -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes -R 80:localhost:5173 nokey@localhost.run'

start() {
  pkill -f "ssh -tt -o StrictHostKeyChecking=no -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes -R 80:localhost:${PORT} nokey@localhost.run" || true
  pkill -f "script -q ${PTY_LOG} ssh -tt" || true

  : > "$PTY_LOG"
  : > "$RUN_LOG"
  nohup script -q "$PTY_LOG" ssh -tt -o StrictHostKeyChecking=no -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes -R 80:localhost:${PORT} nokey@localhost.run > "$RUN_LOG" 2>&1 &

  local url=""
  for _ in $(seq 1 60); do
    url=$(grep -Eo 'https://[a-z0-9.-]+\.lhr\.life' "$PTY_LOG" | tail -n1 || true)
    [[ -n "$url" ]] && break
    sleep 0.5
  done

  if [[ -z "$url" ]]; then
    echo "Tunnel started, but URL not found yet. Logs: $PTY_LOG"
    exit 1
  fi

  echo "$url"
}

stop() {
  pkill -f "ssh -tt -o StrictHostKeyChecking=no -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes -R 80:localhost:${PORT} nokey@localhost.run" || true
  pkill -f "script -q ${PTY_LOG} ssh -tt" || true
  echo "stopped"
}

status() {
  local p
  p=$(pgrep -f "ssh -tt -o StrictHostKeyChecking=no -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes -R 80:localhost:${PORT} nokey@localhost.run" || true)
  if [[ -z "$p" ]]; then
    echo "down"
    exit 1
  fi
  local url
  url=$(grep -Eo 'https://[a-z0-9.-]+\.lhr\.life' "$PTY_LOG" | tail -n1 || true)
  echo "up pid=$p url=${url:-unknown}"
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  status) status ;;
  *)
    echo "Usage: $0 {start|stop|status}" >&2
    exit 2
    ;;
esac
