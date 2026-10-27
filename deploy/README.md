# 生产部署（Docker Compose）

一套命令起完整环境：PostgreSQL + Redis + Spring Boot 后端 + Nginx（静态前端 + 反向代理）。

## 0. 前提

- 一台 Linux 服务器（建议 Ubuntu 22.04 / Debian 12，2 核 4G 起）
- 已安装 Docker 与 Docker Compose v2（`docker compose version` 能输出）
- 安全组/防火墙放行 80（有域名做 HTTPS 时再放行 443）

前端与后端**同域**部署：浏览器访问 `http://你的IP/`，API 走 `/auth`、`/me`、
`/leaderboard`、`/rooms`、`/ai`、`/health`，WebSocket 走 `/ws`，全部由 Nginx
转发到后端容器。因此不需要配置 `VITE_API_BASE`，也不会有跨域问题。

## 1. 上传代码

```bash
git clone https://github.com/yiying666666/wuziqi.git
cd wuziqi
```

## 2. 生成生产环境变量

```bash
cp .env.prod.example .env.prod
# 生成 JWT 密钥
openssl rand -base64 48
# 编辑 .env.prod，填入强密码、JWT_SECRET、CORS_ORIGIN
vi .env.prod
```

要点：

- `JWT_SECRET` 至少 32 字节，泄漏等于所有账号可被伪造登录
- `POSTGRES_PASSWORD` 用强密码（数据库不对外暴露端口，仍建议强密码）
- `CORS_ORIGIN` 填最终访问地址，如 `https://wuziqi.example.com` 或 `http://1.2.3.4`
- `HTTP_PORT` 默认 80；若服务器上已有 Nginx 占用 80，可改成 `8080` 再由系统 Nginx 反代

## 3. 构建并启动

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

首次构建需要下载 Maven/npm 依赖，约 3~10 分钟。查看状态与日志：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f web
```

健康检查：

```bash
curl http://127.0.0.1/health
# {"ok":true}
```

浏览器打开 `http://服务器IP/` 即可。

## 4. 常用运维命令

```bash
# 停止（保留数据卷）
docker compose --env-file .env.prod -f docker-compose.prod.yml down

# 更新代码后重新部署
git pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build

# 数据库备份
docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U wuziqi wuziqi > backup_$(date +%F).sql

# 恢复
cat backup_2026-10-27.sql | docker compose --env-file .env.prod -f docker-compose.prod.yml \
  exec -T postgres psql -U wuziqi -d wuziqi
```

> 数据保存在 Docker 卷 `pg_data`、`redis_data` 中，`down` 不会删除；
> 只有 `down -v` 才会清空，生产环境务必谨慎。

## 5. 配置 HTTPS（有域名时）

最简单的做法：用 Caddy 作为宿主机反向代理，自动申请 Let's Encrypt 证书。

1) `.env.prod` 中把 `HTTP_PORT` 改为 `8080`，`CORS_ORIGIN=https://wuziqi.example.com`，
   然后 `up -d` 让站点监听 8080：

```bash
# 安装 Caddy（Ubuntu/Debian）
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

2) 编辑 `/etc/caddy/Caddyfile`：

```caddyfile
wuziqi.example.com {
    encode gzip
    reverse_proxy 127.0.0.1:8080
}
```

3) 重新加载：

```bash
sudo systemctl reload caddy
```

Caddy 会自动处理证书续期，并透传 WebSocket Upgrade 头。
前端 `socket.ts` 在 HTTPS 页面下会自动使用 `wss://`，无需改代码。

## 6. 如果不使用 Docker

- 后端：`mvn package` 得到 `backend/target/backend-1.0.0.jar`，
  用 systemd 托管 `java -jar`，环境变量同 `.env.prod`
- 前端：`npm run build` 得到 `frontend/dist`，交给 Nginx 托管
- Nginx 配置可参考 `deploy/nginx.conf`，把 `backend:3000` 改成 `127.0.0.1:3000`

## 7. 排错清单

| 现象 | 排查 |
| --- | --- |
| 502 Bad Gateway | `logs backend` 看后端是否启动成功、数据库是否连上 |
| 页面能开但接口 404 | Nginx 的 API location 未匹配，检查 `deploy/nginx.conf` |
| WebSocket 连不上 | 反代未透传 `Upgrade`/`Connection`，检查 `/ws` 段配置 |
| 登录后立刻 401 | `JWT_SECRET` 每次重启变化或长度不足 |
| 构建卡住/OOM | 2G 内存机器加 swap，或在本地构建好镜像再推送到服务器 |
