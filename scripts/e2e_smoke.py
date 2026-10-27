#!/usr/bin/env python3
"""Wuziqi E2E smoke test for REST + WebSocket.

Prerequisites:
  1) PostgreSQL and Redis are running
  2) backend is running at API_BASE / WS_BASE
  3) frontend is optional for this script (it tests APIs directly)
"""

import argparse
import asyncio
import json
import random
import string
import sys
import time
from dataclasses import dataclass
from typing import Any, Callable, Optional

import requests
import websockets


class SmokeError(RuntimeError):
    pass


@dataclass
class UserSession:
    username: str
    password: str
    user_id: int
    token: str


class WsClient:
    def __init__(self, ws_base: str, token: str, name: str):
        self.ws_base = ws_base
        self.token = token
        self.name = name
        self.ws: Optional[websockets.WebSocketClientProtocol] = None
        self.queue: asyncio.Queue[dict[str, Any]] = asyncio.Queue()
        self.reader_task: Optional[asyncio.Task] = None

    async def connect(self) -> None:
        url = f"{self.ws_base}?token={self.token}"
        self.ws = await websockets.connect(url)
        self.reader_task = asyncio.create_task(self._reader())

    async def _reader(self) -> None:
        assert self.ws is not None
        try:
            async for raw in self.ws:
                packet = json.loads(raw)
                await self.queue.put(packet)
        except Exception:
            # normal for close/reconnect in smoke tests
            pass

    async def send(self, event: str, payload: dict[str, Any]) -> None:
        if not self.ws:
            raise SmokeError(f"[{self.name}] websocket not connected")
        await self.ws.send(json.dumps({"event": event, "payload": payload}))

    async def expect(
        self,
        event: str,
        timeout: float = 8.0,
        predicate: Optional[Callable[[dict[str, Any]], bool]] = None,
    ) -> dict[str, Any]:
        deadline = time.monotonic() + timeout
        while True:
            remain = deadline - time.monotonic()
            if remain <= 0:
                raise SmokeError(f"[{self.name}] timeout waiting event={event}")
            packet = await asyncio.wait_for(self.queue.get(), timeout=remain)
            got = packet.get("event")
            payload = packet.get("payload", {})
            if got != event:
                continue
            if predicate and not predicate(payload):
                continue
            return payload

    async def close(self) -> None:
        if self.ws:
            await self.ws.close()
            self.ws = None
        if self.reader_task:
            self.reader_task.cancel()
            self.reader_task = None


def rand_suffix(n: int = 6) -> str:
    return "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(n))


def check_health(api_base: str) -> None:
    r = requests.get(f"{api_base}/health", timeout=5)
    r.raise_for_status()


def register_or_login(api_base: str, username: str, password: str) -> UserSession:
    payload = {"username": username, "password": password}
    r = requests.post(f"{api_base}/auth/register", json=payload, timeout=8)
    if r.status_code >= 400:
        # maybe already exists; login fallback
        r2 = requests.post(f"{api_base}/auth/login", json=payload, timeout=8)
        r2.raise_for_status()
        body = r2.json()
    else:
        body = r.json()

    token = body["token"]
    user = body["user"]
    return UserSession(username=username, password=password, user_id=user["id"], token=token)


def check_profile(api_base: str, user: UserSession) -> None:
    r = requests.get(
        f"{api_base}/me/profile",
        headers={"Authorization": f"Bearer {user.token}"},
        timeout=8,
    )
    r.raise_for_status()
    data = r.json()
    if data.get("id") != user.user_id:
        raise SmokeError("profile user id mismatch")


async def ws_flow(api_base: str, ws_base: str, u1: UserSession, u2: UserSession) -> str:
    c1 = WsClient(ws_base, u1.token, "u1")
    c2 = WsClient(ws_base, u2.token, "u2")

    await c1.connect()
    await c2.connect()

    await c1.expect("system:connected")
    await c2.expect("system:connected")

    # create room by user1
    await c1.send("room:create", {})
    room_state = await c1.expect("room:state", predicate=lambda p: "room" in p)
    room_code = room_state["room"]["code"]

    # join by user2
    await c2.send("room:join", {"code": room_code})
    await c1.expect("game:start", predicate=lambda p: p.get("roomCode") == room_code)
    await c2.expect("game:start", predicate=lambda p: p.get("roomCode") == room_code)

    # disconnect/reconnect smoke (mobile background-like)
    await c2.close()
    await c1.expect("player:disconnected", predicate=lambda p: p.get("userId") == u2.user_id)

    c2 = WsClient(ws_base, u2.token, "u2-reconnect")
    await c2.connect()
    await c2.expect("system:connected")

    # With Redis enabled, server can auto-recover room by user mapping.
    # Without Redis, actively rejoin to simulate foreground resume.
    await c2.send("room:join", {"code": room_code})
    await c2.expect("room:state", predicate=lambda p: p.get("room", {}).get("code") == room_code)
    await c1.expect("player:reconnected", predicate=lambda p: p.get("userId") == u2.user_id)

    # black(u1) wins in 5
    moves = [
        (c1, u1.user_id, 7, 7, 1),
        (c2, u2.user_id, 0, 0, 2),
        (c1, u1.user_id, 8, 7, 1),
        (c2, u2.user_id, 0, 1, 2),
        (c1, u1.user_id, 9, 7, 1),
        (c2, u2.user_id, 0, 2, 2),
        (c1, u1.user_id, 10, 7, 1),
        (c2, u2.user_id, 0, 3, 2),
        (c1, u1.user_id, 11, 7, 1),
    ]

    move_no = 0
    for client, _uid, x, y, color in moves:
        move_no += 1
        await client.send("game:move", {"roomCode": room_code, "x": x, "y": y})
        pred = lambda p, mx=x, my=y, mc=color, mn=move_no: (
            p.get("x") == mx and p.get("y") == my and p.get("color") == mc and p.get("moveNo") == mn
        )
        await c1.expect("game:moved", predicate=pred)
        await c2.expect("game:moved", predicate=pred)

    end1 = await c1.expect("game:ended", predicate=lambda p: p.get("roomCode") == room_code)
    end2 = await c2.expect("game:ended", predicate=lambda p: p.get("roomCode") == room_code)

    if end1.get("winnerUserId") != u1.user_id or end2.get("winnerUserId") != u1.user_id:
        raise SmokeError("winner user id mismatch in game:ended")

    await c1.close()
    await c2.close()
    return room_code


def verify_post_game(api_base: str, room_code: str, u1: UserSession, u2: UserSession) -> None:
    # snapshot
    snap = requests.get(f"{api_base}/rooms/{room_code}", timeout=8)
    snap.raise_for_status()
    snap_j = snap.json()
    if snap_j.get("winner_user_id") != u1.user_id:
        raise SmokeError("snapshot winner mismatch")

    # leaderboard
    lb = requests.get(f"{api_base}/leaderboard", params={"limit": 100}, timeout=8)
    lb.raise_for_status()
    items = lb.json()
    ids = {item.get("userId") for item in items}
    if u1.user_id not in ids or u2.user_id not in ids:
        raise SmokeError("leaderboard missing test users")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run Wuziqi backend e2e smoke test")
    parser.add_argument("--api", default="http://localhost:3000", help="API base URL")
    parser.add_argument("--ws", default="ws://localhost:3000/ws", help="WebSocket base URL")
    args = parser.parse_args()

    api_base = args.api.rstrip("/")
    ws_base = args.ws

    print("[1/6] health check...")
    check_health(api_base)
    print("  ✓ backend reachable")

    suffix = rand_suffix()
    p1 = "Passw0rd!" + rand_suffix(4)
    p2 = "Passw0rd!" + rand_suffix(4)

    print("[2/6] register/login users...")
    u1 = register_or_login(api_base, f"e2e_a_{suffix}", p1)
    u2 = register_or_login(api_base, f"e2e_b_{suffix}", p2)
    print(f"  ✓ users: {u1.username}#{u1.user_id}, {u2.username}#{u2.user_id}")

    print("[3/6] profile check...")
    check_profile(api_base, u1)
    check_profile(api_base, u2)
    print("  ✓ /me/profile passed")

    print("[4/6] websocket room/move/reconnect flow...")
    room_code = asyncio.run(ws_flow(api_base, ws_base, u1, u2))
    print(f"  ✓ room {room_code} finished")

    print("[5/6] post-game snapshot + leaderboard...")
    verify_post_game(api_base, room_code, u1, u2)
    print("  ✓ snapshot & leaderboard passed")

    print("[6/6] done")
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"SMOKE TEST FAILED: {exc}", file=sys.stderr)
        raise
