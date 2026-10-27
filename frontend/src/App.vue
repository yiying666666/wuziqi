<template>
  <div class="app-shell">
    <header class="topbar">
      <h1>五子棋 Online</h1>
      <p>H5（手机优先）｜房间对战｜人机对战</p>
    </header>

    <section v-if="connectionState !== 'connected' && isLoggedIn" class="network-banner">
      {{ reconnectText }}
    </section>

    <main v-if="!isLoggedIn" class="auth-wrap">
      <form class="card auth-card" @submit.prevent="submitAuth">
        <h2>{{ authMode === 'login' ? '登录' : '注册' }}</h2>
        <label>
          用户名
          <input v-model.trim="username" minlength="3" maxlength="20" required />
        </label>
        <label>
          密码
          <input v-model="password" minlength="6" maxlength="64" type="password" required />
        </label>

        <button class="primary" type="submit">{{ authMode === 'login' ? '登录' : '注册' }}</button>

        <button class="ghost" type="button" @click="toggleAuthMode">
          {{ authMode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
        </button>

        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
      </form>
    </main>

    <main v-else class="layout">
      <section class="left-col">
        <div class="card profile-card">
          <div class="row between">
            <h3>你好，{{ session.state.user?.username }}</h3>
            <button class="ghost" @click="logout">退出</button>
          </div>
          <p v-if="profile">Elo {{ profile.elo }} ｜ {{ profile.wins }}胜 {{ profile.losses }}负 {{ profile.draws }}平</p>
        </div>

        <div class="card lobby-card">
          <h3>房间大厅</h3>
          <div class="actions mode-actions">
            <button class="primary" @click="createRoom">创建联机房间</button>
            <button class="secondary" @click="startAiGame">开始人机对战</button>
          </div>

          <div class="join-row">
            <input
              v-model.trim="joinCode"
              maxlength="12"
              placeholder="输入房间号"
              style="text-transform: uppercase"
            />
            <button class="primary" @click="joinRoom">加入</button>
          </div>

          <p v-if="room" class="tip">
            当前联机房间：<strong>{{ room.code }}</strong>
            <button class="ghost mini" @click="copyRoomCode">复制</button>
          </p>
          <p v-else-if="aiGame" class="tip">当前模式：人机对战（本地模式，不影响 Elo）</p>
          <p v-if="notice" class="tip">{{ notice }}</p>
          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        </div>
      </section>

      <section class="right-col">
        <div class="card game-card">
          <template v-if="room || aiGame">
            <div class="row between game-head">
              <div>
                <h3 v-if="room">房间 {{ room.code }}</h3>
                <h3 v-else>人机对战</h3>
                <p>{{ gameStatusText }}</p>
              </div>
              <button class="ghost" @click="room ? leaveRoom() : stopAiGame()">
                {{ room ? '离开房间' : '结束人机对战' }}
              </button>
            </div>

            <div v-if="room" class="players">
              <span
                v-for="player in room.players"
                :key="player.userId"
                :class="{
                  me: player.userId === session.state.user?.id,
                  disconnected: !player.connected,
                }"
              >
                {{ player.color === 1 ? '● 黑' : '○ 白' }} {{ player.username }}
                <small>{{ player.connected ? '在线' : '离线' }}</small>
              </span>
            </div>

            <div v-else-if="aiGame" class="players">
              <span class="me">● 黑 你</span>
              <span>○ 白 AI</span>
            </div>

            <GomokuBoard
              v-if="activeBoard"
              :board="activeBoard"
              :disabled="boardDisabled"
              @place="(x, y) => placeStone(x, y)"
            />

            <p class="tip" v-if="turnTip">{{ turnTip }}</p>
          </template>

          <template v-else>
            <h3>开始一局对战</h3>
            <p>你可以创建联机房间与好友对战，也可以直接开启人机对战（手机/PC 通用 H5）。</p>
            <ul>
              <li>支持触屏落子与移动端安全区适配</li>
              <li>网络抖动自动重连并恢复联机棋局</li>
              <li>联机模式结束后自动更新 Elo 战绩</li>
            </ul>
          </template>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import GomokuBoard from "./components/GomokuBoard.vue";
import { useSession } from "./composables/useSession";
import { authApi, profileApi } from "./services/api";
import { SocketClient } from "./services/socket";
import type { Profile, RoomState, StoneColor } from "./types";

const BOARD_SIZE = 15;
const HUMAN_COLOR: StoneColor = 1;
const AI_COLOR: StoneColor = 2;
const DIRECTIONS: Array<[number, number]> = [
  [1, 0],
  [0, 1],
  [1, 1],
  [1, -1],
];

interface PveGameState {
  board: number[][];
  currentTurn: StoneColor;
  status: "in_progress" | "ended";
  winnerColor: StoneColor | null;
  reason: "normal" | "draw";
}

const createEmptyBoard = (): number[][] =>
  Array.from({ length: BOARD_SIZE }, () => Array.from({ length: BOARD_SIZE }, () => 0));

const inBounds = (x: number, y: number): boolean =>
  x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;

const countDirection = (
  board: number[][],
  x: number,
  y: number,
  dx: number,
  dy: number,
  color: number,
): number => {
  let count = 0;
  let nx = x + dx;
  let ny = y + dy;
  while (inBounds(nx, ny) && board[ny][nx] === color) {
    count += 1;
    nx += dx;
    ny += dy;
  }
  return count;
};

const isOpenEnd = (
  board: number[][],
  x: number,
  y: number,
  dx: number,
  dy: number,
  length: number,
): boolean => {
  const nx = x + dx * (length + 1);
  const ny = y + dy * (length + 1);
  return inBounds(nx, ny) && board[ny][nx] === 0;
};

const checkWinAt = (board: number[][], x: number, y: number, color: number): boolean =>
  DIRECTIONS.some(([dx, dy]) => {
    const forward = countDirection(board, x, y, dx, dy, color);
    const backward = countDirection(board, x, y, -dx, -dy, color);
    return 1 + forward + backward >= 5;
  });

const isBoardFull = (board: number[][]): boolean =>
  board.every((row) => row.every((cell) => cell !== 0));

const wouldWin = (board: number[][], x: number, y: number, color: number): boolean => {
  if (board[y][x] !== 0) return false;
  board[y][x] = color;
  const won = checkWinAt(board, x, y, color);
  board[y][x] = 0;
  return won;
};

const collectCandidates = (board: number[][]): Array<{ x: number; y: number }> => {
  const candidates = new Set<string>();
  let hasStone = false;

  for (let y = 0; y < BOARD_SIZE; y += 1) {
    for (let x = 0; x < BOARD_SIZE; x += 1) {
      if (board[y][x] === 0) continue;
      hasStone = true;
      for (let dy = -2; dy <= 2; dy += 1) {
        for (let dx = -2; dx <= 2; dx += 1) {
          const nx = x + dx;
          const ny = y + dy;
          if (!inBounds(nx, ny) || board[ny][nx] !== 0) continue;
          candidates.add(`${nx},${ny}`);
        }
      }
    }
  }

  if (!hasStone) {
    const center = Math.floor(BOARD_SIZE / 2);
    return [{ x: center, y: center }];
  }

  return [...candidates].map((item) => {
    const [x, y] = item.split(",").map(Number);
    return { x, y };
  });
};

const evaluatePoint = (board: number[][], x: number, y: number, color: number): number => {
  let score = 0;

  for (const [dx, dy] of DIRECTIONS) {
    const left = countDirection(board, x, y, -dx, -dy, color);
    const right = countDirection(board, x, y, dx, dy, color);
    const total = 1 + left + right;
    const openEnds = Number(isOpenEnd(board, x, y, -dx, -dy, left)) + Number(isOpenEnd(board, x, y, dx, dy, right));

    if (total >= 5) {
      score += 120000;
    } else if (total === 4 && openEnds === 2) {
      score += 28000;
    } else if (total === 4 && openEnds === 1) {
      score += 9000;
    } else if (total === 3 && openEnds === 2) {
      score += 2600;
    } else if (total === 3 && openEnds === 1) {
      score += 900;
    } else if (total === 2 && openEnds === 2) {
      score += 350;
    } else if (total === 2 && openEnds === 1) {
      score += 110;
    } else if (total === 1 && openEnds === 2) {
      score += 22;
    }
  }

  const center = Math.floor(BOARD_SIZE / 2);
  const distance = Math.abs(x - center) + Math.abs(y - center);
  score += (BOARD_SIZE - distance) * 4;

  return score;
};

const pickBestAIMove = (board: number[][]): { x: number; y: number } | null => {
  const candidates = collectCandidates(board);
  if (!candidates.length) return null;

  for (const point of candidates) {
    if (wouldWin(board, point.x, point.y, AI_COLOR)) {
      return point;
    }
  }

  for (const point of candidates) {
    if (wouldWin(board, point.x, point.y, HUMAN_COLOR)) {
      return point;
    }
  }

  let bestPoints: Array<{ x: number; y: number }> = [];
  let bestScore = Number.NEGATIVE_INFINITY;

  for (const point of candidates) {
    board[point.y][point.x] = AI_COLOR;
    const attack = evaluatePoint(board, point.x, point.y, AI_COLOR);
    board[point.y][point.x] = HUMAN_COLOR;
    const defend = evaluatePoint(board, point.x, point.y, HUMAN_COLOR);
    board[point.y][point.x] = 0;

    const score = attack * 1.15 + defend;
    if (score > bestScore) {
      bestScore = score;
      bestPoints = [point];
    } else if (score === bestScore) {
      bestPoints.push(point);
    }
  }

  if (!bestPoints.length) {
    return candidates[0];
  }

  return bestPoints[Math.floor(Math.random() * bestPoints.length)];
};

const session = useSession();
const socket = new SocketClient();

const authMode = ref<"login" | "register">("login");
const username = ref("");
const password = ref("");
const joinCode = ref("");

const room = ref<RoomState | null>(null);
const aiGame = ref<PveGameState | null>(null);
const profile = ref<Profile | null>(null);
const errorMessage = ref("");
const notice = ref("");
const reconnectText = ref("网络连接中...");
const connectionState = ref<"connected" | "reconnecting" | "closed">("closed");

const cleanupFns: Array<() => void> = [];
let aiTimer: ReturnType<typeof setTimeout> | null = null;

const clearAiTimer = (): void => {
  if (aiTimer) {
    clearTimeout(aiTimer);
    aiTimer = null;
  }
};

const isLoggedIn = computed(() => Boolean(session.state.token && session.state.user));

const myPlayer = computed(() =>
  room.value?.players.find((item) => item.userId === session.state.user?.id),
);

const isMyTurn = computed(
  () => room.value?.status === "in_progress" && myPlayer.value?.color === room.value.currentTurn,
);

const activeBoard = computed<number[][] | null>(() => aiGame.value?.board ?? room.value?.board ?? null);

const boardDisabled = computed(() => {
  if (aiGame.value) {
    return aiGame.value.status !== "in_progress" || aiGame.value.currentTurn !== HUMAN_COLOR;
  }
  return !isMyTurn.value || room.value?.status !== "in_progress";
});

const gameStatusText = computed(() => {
  if (aiGame.value) {
    if (aiGame.value.status === "ended") {
      if (aiGame.value.reason === "draw") return "人机对战结束：平局";
      return aiGame.value.winnerColor === HUMAN_COLOR ? "人机对战结束：你赢了 🎉" : "人机对战结束：AI 获胜";
    }
    return aiGame.value.currentTurn === HUMAN_COLOR ? "你的回合（黑棋）" : "AI 思考中（白棋）";
  }

  if (!room.value) return "";
  if (room.value.status === "waiting") return "等待玩家加入";
  if (room.value.status === "ended") return "联机对局结束";
  return `当前回合：${room.value.currentTurn === 1 ? "黑棋" : "白棋"}`;
});

const turnTip = computed(() => {
  if (aiGame.value) {
    if (aiGame.value.status === "ended") {
      return "可点击“开始人机对战”再来一局，或回到联机模式。";
    }
    return aiGame.value.currentTurn === HUMAN_COLOR ? "轮到你落子" : "AI 正在思考...";
  }

  if (!room.value) return "";
  if (room.value.status === "in_progress") {
    return isMyTurn.value ? "轮到你落子" : "等待对手落子";
  }
  if (room.value.status === "waiting") {
    return "等待第二位玩家加入…";
  }
  return "本局已结束，可创建/加入新房间继续。";
});

const resetMessages = (): void => {
  errorMessage.value = "";
  notice.value = "";
};

const submitAuth = async (): Promise<void> => {
  resetMessages();
  try {
    const action = authMode.value === "login" ? authApi.login : authApi.register;
    const data = await action(username.value, password.value);
    session.setSession(data.token, data.user);
    username.value = "";
    password.value = "";
  } catch (error) {
    errorMessage.value = (error as Error).message;
  }
};

const toggleAuthMode = (): void => {
  authMode.value = authMode.value === "login" ? "register" : "login";
  errorMessage.value = "";
};

const stopAiGame = (): void => {
  clearAiTimer();
  aiGame.value = null;
};

const createRoom = (): void => {
  resetMessages();
  stopAiGame();
  socket.send("room:create", {});
};

const joinRoom = (): void => {
  if (!joinCode.value) {
    errorMessage.value = "请输入房间号";
    return;
  }
  resetMessages();
  stopAiGame();
  socket.send("room:join", { code: joinCode.value.toUpperCase() });
};

const leaveRoom = (): void => {
  socket.send("room:leave", {});
  room.value = null;
};

const createNewAiGame = (): PveGameState => ({
  board: createEmptyBoard(),
  currentTurn: HUMAN_COLOR,
  status: "in_progress",
  winnerColor: null,
  reason: "normal",
});

const startAiGame = (): void => {
  resetMessages();
  if (room.value) {
    leaveRoom();
  }
  aiGame.value = createNewAiGame();
  notice.value = "已开始人机对战：你执黑先行";
};

const finishAiGame = (winnerColor: StoneColor | null, reason: "normal" | "draw"): void => {
  if (!aiGame.value) return;
  aiGame.value.status = "ended";
  aiGame.value.winnerColor = winnerColor;
  aiGame.value.reason = reason;
  clearAiTimer();
};

const makeAiMove = (): void => {
  if (!aiGame.value || aiGame.value.status !== "in_progress" || aiGame.value.currentTurn !== AI_COLOR) {
    return;
  }

  const board = aiGame.value.board;
  const point = pickBestAIMove(board);
  if (!point) {
    finishAiGame(null, "draw");
    return;
  }

  board[point.y][point.x] = AI_COLOR;

  if (checkWinAt(board, point.x, point.y, AI_COLOR)) {
    finishAiGame(AI_COLOR, "normal");
    notice.value = "AI 获胜，本局结束。";
    return;
  }

  if (isBoardFull(board)) {
    finishAiGame(null, "draw");
    notice.value = "平局，棋盘已满。";
    return;
  }

  aiGame.value.currentTurn = HUMAN_COLOR;
  notice.value = "轮到你落子";
};

const placeStone = (x: number, y: number): void => {
  if (aiGame.value) {
    if (aiGame.value.status !== "in_progress") return;
    if (aiGame.value.currentTurn !== HUMAN_COLOR) return;

    const board = aiGame.value.board;
    if (!inBounds(x, y)) {
      errorMessage.value = "坐标越界";
      return;
    }
    if (board[y][x] !== 0) {
      errorMessage.value = "该位置已有棋子";
      return;
    }

    resetMessages();
    board[y][x] = HUMAN_COLOR;

    if (checkWinAt(board, x, y, HUMAN_COLOR)) {
      finishAiGame(HUMAN_COLOR, "normal");
      notice.value = "恭喜你获胜！";
      return;
    }

    if (isBoardFull(board)) {
      finishAiGame(null, "draw");
      notice.value = "平局，棋盘已满。";
      return;
    }

    aiGame.value.currentTurn = AI_COLOR;
    notice.value = "AI 思考中...";
    clearAiTimer();
    aiTimer = setTimeout(() => {
      makeAiMove();
    }, 420);
    return;
  }

  if (!room.value) return;
  socket.send("game:move", { roomCode: room.value.code, x, y });
};

const copyRoomCode = async (): Promise<void> => {
  if (!room.value) return;
  await navigator.clipboard.writeText(room.value.code);
  notice.value = `已复制房间号：${room.value.code}`;
};

const loadProfile = async (): Promise<void> => {
  if (!session.state.token) return;
  profile.value = await profileApi.me(session.state.token);
};

const logout = (): void => {
  session.clearSession();
  socket.disconnect();
  room.value = null;
  stopAiGame();
  profile.value = null;
  connectionState.value = "closed";
};

const bindSocketEvents = (): void => {
  cleanupFns.push(
    socket.on("room:state", (payload) => {
      if (aiGame.value) {
        return;
      }
      const typed = payload as { room: RoomState };
      room.value = typed.room;
      if (typed.room.code) {
        joinCode.value = typed.room.code;
      }
    }),
  );

  cleanupFns.push(
    socket.on("game:invalid_move", (payload) => {
      const typed = payload as { message: string };
      errorMessage.value = typed.message;
    }),
  );

  cleanupFns.push(
    socket.on("error", (payload) => {
      const typed = payload as { message: string };
      errorMessage.value = typed.message;
    }),
  );

  cleanupFns.push(
    socket.on("player:disconnected", (payload) => {
      const typed = payload as { username: string; graceSeconds: number };
      notice.value = `${typed.username} 暂时离线，等待 ${typed.graceSeconds} 秒重连`;
    }),
  );

  cleanupFns.push(
    socket.on("player:reconnected", (payload) => {
      const typed = payload as { username: string };
      notice.value = `${typed.username} 已重连`;
    }),
  );

  cleanupFns.push(
    socket.on("game:ended", async () => {
      if (aiGame.value) {
        return;
      }
      notice.value = "联机对局已结束，战绩和 Elo 已更新";
      try {
        await loadProfile();
      } catch (error) {
        console.error(error);
      }
    }),
  );

  cleanupFns.push(
    socket.onOpen(() => {
      connectionState.value = "connected";
      reconnectText.value = "已连接";
    }),
  );

  cleanupFns.push(
    socket.onClose(() => {
      if (isLoggedIn.value) {
        connectionState.value = "reconnecting";
        reconnectText.value = "网络波动，正在自动重连...";
      }
    }),
  );

  cleanupFns.push(
    socket.onReconnecting((attempt) => {
      connectionState.value = "reconnecting";
      reconnectText.value = `重连中（第 ${attempt} 次）`;
    }),
  );
};

watch(
  () => session.state.token,
  async (token) => {
    if (!token) {
      return;
    }

    socket.connect(token);
    try {
      await loadProfile();
    } catch (error) {
      errorMessage.value = (error as Error).message;
    }
  },
  { immediate: true },
);

onMounted(() => {
  bindSocketEvents();
  document.addEventListener("visibilitychange", onVisibilityChange);
});

onBeforeUnmount(() => {
  cleanupFns.forEach((fn) => fn());
  clearAiTimer();
  socket.disconnect();
  document.removeEventListener("visibilitychange", onVisibilityChange);
});

const onVisibilityChange = (): void => {
  if (document.visibilityState === "visible" && isLoggedIn.value && !socket.isConnected()) {
    connectionState.value = "reconnecting";
    reconnectText.value = "已返回前台，正在恢复连接...";
    socket.connect(session.state.token!);
  }
};
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  padding-top: max(12px, env(safe-area-inset-top));
  padding-bottom: max(12px, env(safe-area-inset-bottom));
  padding-left: max(12px, env(safe-area-inset-left));
  padding-right: max(12px, env(safe-area-inset-right));
}

.topbar {
  text-align: center;
  margin-bottom: 12px;
}

.topbar h1 {
  margin: 0;
  font-size: clamp(24px, 4.2vw, 30px);
}

.topbar p {
  margin: 4px 0 0;
  color: #666;
}

.network-banner {
  max-width: 1080px;
  margin: 0 auto 12px;
  background: #fff7cc;
  color: #7a5b00;
  border: 1px solid #f0d77a;
  border-radius: 10px;
  padding: 8px 12px;
  text-align: center;
}

.layout {
  max-width: 1080px;
  margin: 0 auto;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(280px, 360px) 1fr;
}

.auth-wrap {
  display: grid;
  place-items: center;
  min-height: calc(100vh - 120px);
}

.card {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  box-shadow: 0 8px 20px rgb(0 0 0 / 8%);
}

.auth-card {
  width: min(92vw, 380px);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.auth-card label {
  display: grid;
  gap: 6px;
  color: #666;
}

input,
button {
  min-height: 44px;
  border-radius: 10px;
  border: 1px solid #d0d7de;
  padding: 0 12px;
}

button {
  cursor: pointer;
}

button.primary {
  background: #1f6feb;
  color: #fff;
  border-color: transparent;
}

button.secondary {
  background: #eff6ff;
  color: #174ea6;
  border-color: #bfd6ff;
}

button.ghost {
  background: #fff;
}

button.mini {
  min-height: 34px;
  padding-inline: 10px;
}

.left-col,
.right-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.row.between {
  justify-content: space-between;
}

.actions,
.join-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.mode-actions {
  flex-wrap: wrap;
}

.mode-actions button {
  flex: 1;
}

.join-row input {
  flex: 1;
}

.game-card {
  min-height: 560px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.players {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.players span {
  background: #f4f6f8;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 14px;
}

.players span.me {
  border: 1px solid #1f6feb;
}

.players span.disconnected {
  opacity: 0.6;
}

.tip {
  margin: 0;
  color: #555;
}

.error {
  margin: 0;
  color: #c62828;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .game-card {
    min-height: auto;
  }
}

@media (max-width: 430px) {
  .card {
    padding: 12px;
  }

  .actions,
  .join-row {
    flex-direction: column;
  }

  .game-head {
    align-items: start;
    flex-direction: column;
  }
}
</style>
