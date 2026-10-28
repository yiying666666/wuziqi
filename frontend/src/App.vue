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
          <p class="tip compact-tip">先手规则：黑白交替（上一局黑先，则下一局白先）</p>

          <div class="difficulty-row">
            <span>人机难度</span>
            <div class="difficulty-options">
              <button
                v-for="item in AI_DIFFICULTY_OPTIONS"
                :key="item.value"
                type="button"
                class="difficulty-btn"
                :class="{ active: aiDifficulty === item.value }"
                @click="aiDifficulty = item.value"
                :title="item.desc"
              >
                {{ item.label }}
              </button>
            </div>
          </div>

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

            <div v-if="room && room.status === 'in_progress'" class="undo-panel">
              <button class="ghost mini" :disabled="!canRequestUndo" @click="requestUndo">请求悔棋</button>

              <template v-if="isUndoPending && !isUndoRequesterMe">
                <button class="primary mini" @click="respondUndo(true)">同意悔棋</button>
                <button class="ghost mini" @click="respondUndo(false)">拒绝悔棋</button>
              </template>

              <span v-if="isUndoPending" class="undo-hint">{{ undoHintText }}</span>
            </div>

            <div v-else-if="aiGame" class="players">
              <span class="me">● 黑 你</span>
              <span>○ 白 AI</span>
              <span class="starter-tag">本局{{ aiGame.starterColor === 1 ? "黑" : "白" }}先手</span>
              <span class="starter-tag">难度：{{ aiDifficultyText }}</span>
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

    <div v-if="resultDialog.visible" class="result-mask" @click.self="closeResultDialog">
      <div class="result-dialog" :class="resultDialog.tone" role="dialog" aria-modal="true" aria-label="对局结果">
        <h3>{{ resultDialog.title }}</h3>
        <p>{{ resultDialog.message }}</p>
        <div class="result-actions">
          <button class="primary" @click="startNextGameFromDialog">开启下一局</button>
          <button class="ghost" @click="closeResultDialog">稍后再说</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import GomokuBoard from "./components/GomokuBoard.vue";
import { useSession } from "./composables/useSession";
import { aiApi, authApi, profileApi } from "./services/api";
import { SocketClient } from "./services/socket";
import type { AIDifficulty, Profile, RoomState, StoneColor } from "./types";

const BOARD_SIZE = 15;
const BLACK_STONE: StoneColor = 1;
const WHITE_STONE: StoneColor = 2;
const HUMAN_COLOR: StoneColor = BLACK_STONE;
const AI_COLOR: StoneColor = WHITE_STONE;
const DIRECTIONS: Array<[number, number]> = [
  [1, 0],
  [0, 1],
  [1, 1],
  [1, -1],
];

interface PveGameState {
  board: number[][];
  starterColor: StoneColor;
  currentTurn: StoneColor;
  status: "in_progress" | "ended";
  winnerColor: StoneColor | null;
  reason: "normal" | "draw";
}

type ResultTone = "win" | "lose" | "draw";
type ResultMode = "online" | "ai";
interface ResultDialogState {
  visible: boolean;
  mode: ResultMode;
  tone: ResultTone;
  title: string;
  message: string;
}

const AI_DIFFICULTY_OPTIONS: Array<{ value: AIDifficulty; label: string; desc: string }> = [
  { value: "easy", label: "简单", desc: "偏随机，适合新手" },
  { value: "medium", label: "普通", desc: "稳健攻防，推荐" },
  { value: "hard", label: "困难", desc: "带搜索，更强" },
];

const AI_THINK_MS_BY_DIFFICULTY: Record<AIDifficulty, number> = {
  easy: 360,
  medium: 900,
  hard: 1800,
};

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

const checkWinAt = (board: number[][], x: number, y: number, color: number): boolean =>
  DIRECTIONS.some(([dx, dy]) => {
    const forward = countDirection(board, x, y, dx, dy, color);
    const backward = countDirection(board, x, y, -dx, -dy, color);
    return 1 + forward + backward >= 5;
  });

const isBoardFull = (board: number[][]): boolean =>
  board.every((row) => row.every((cell) => cell !== 0));

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
const resultDialog = ref<ResultDialogState>({
  visible: false,
  mode: "online",
  tone: "draw",
  title: "",
  message: "",
});
const nextAiStarterColor = ref<StoneColor>(BLACK_STONE);
const aiDifficulty = ref<AIDifficulty>("medium");

const cleanupFns: Array<() => void> = [];
let aiTimer: ReturnType<typeof setTimeout> | null = null;

const stoneText = (color: StoneColor): string => (color === BLACK_STONE ? "黑棋" : "白棋");
const aiDifficultyText = computed(
  () => AI_DIFFICULTY_OPTIONS.find((item) => item.value === aiDifficulty.value)?.label ?? "普通",
);

const clearAiTimer = (): void => {
  if (aiTimer) {
    clearTimeout(aiTimer);
    aiTimer = null;
  }
};

const openResultDialog = (
  mode: ResultMode,
  tone: ResultTone,
  title: string,
  message: string,
): void => {
  resultDialog.value = {
    visible: true,
    mode,
    tone,
    title,
    message,
  };
};

const closeResultDialog = (): void => {
  resultDialog.value.visible = false;
};

const startNextGameFromDialog = (): void => {
  const mode = resultDialog.value.mode;
  closeResultDialog();

  if (mode === "ai") {
    startAiGame();
    return;
  }

  if (room.value?.code) {
    socket.send("room:restart", { roomCode: room.value.code });
    notice.value = "已请求在当前房间开启下一局";
    return;
  }

  createRoom();
};

const isLoggedIn = computed(() => Boolean(session.state.token && session.state.user));
const myUserId = computed(() => session.state.user?.id ?? null);

const myPlayer = computed(() =>
  room.value?.players.find((item) => item.userId === session.state.user?.id),
);

const undoRequester = computed(() => {
  if (!room.value?.pendingUndoRequesterUserId) {
    return null;
  }
  return room.value.players.find((item) => item.userId === room.value?.pendingUndoRequesterUserId) ?? null;
});

const isUndoPending = computed(() => Boolean(room.value?.pendingUndoRequesterUserId));
const isUndoRequesterMe = computed(
  () => Boolean(room.value?.pendingUndoRequesterUserId && room.value.pendingUndoRequesterUserId === myUserId.value),
);
const canRequestUndo = computed(
  () => Boolean(room.value
    && room.value.status === "in_progress"
    && room.value.moves.length > 0
    && !isUndoPending.value),
);
const undoHintText = computed(() => {
  if (!room.value || !isUndoPending.value) {
    return "";
  }

  const requesterName = undoRequester.value?.username ?? "对手";
  if (isUndoRequesterMe.value) {
    return "已发起悔棋请求，等待对手确认";
  }
  return `${requesterName} 请求悔棋，请选择同意或拒绝`;
});

const isMyTurn = computed(
  () => room.value?.status === "in_progress" && myPlayer.value?.color === room.value.currentTurn,
);

const activeBoard = computed<number[][] | null>(() => aiGame.value?.board ?? room.value?.board ?? null);

const boardDisabled = computed(() => {
  if (aiGame.value) {
    return aiGame.value.status !== "in_progress" || aiGame.value.currentTurn !== HUMAN_COLOR;
  }
  return !isMyTurn.value || room.value?.status !== "in_progress" || isUndoPending.value;
});

const gameStatusText = computed(() => {
  if (aiGame.value) {
    if (aiGame.value.status === "ended") {
      if (aiGame.value.reason === "draw") return "人机对战结束：平局";
      return aiGame.value.winnerColor === HUMAN_COLOR ? "人机对战结束：你赢了 🎉" : "人机对战结束：AI 获胜";
    }
    return aiGame.value.currentTurn === HUMAN_COLOR
      ? `你的回合（${stoneText(HUMAN_COLOR)}）｜难度 ${aiDifficultyText.value}`
      : `AI 回合（${stoneText(AI_COLOR)}）｜难度 ${aiDifficultyText.value}`;
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
    return aiGame.value.currentTurn === HUMAN_COLOR
      ? "轮到你落子"
      : `AI 正在思考（本局${stoneText(aiGame.value.starterColor)}先手）...`;
  }

  if (!room.value) return "";
  if (isUndoPending.value) {
    return undoHintText.value;
  }
  if (room.value.status === "in_progress") {
    return isMyTurn.value ? "轮到你落子" : "等待对手落子";
  }
  if (room.value.status === "waiting") {
    return "等待第二位玩家加入…";
  }
  return "本局已结束，可在当前房间点击“开启下一局”。";
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
  closeResultDialog();
};

const createRoom = (): void => {
  resetMessages();
  closeResultDialog();
  stopAiGame();
  socket.send("room:create", {});
};

const joinRoom = (): void => {
  if (!joinCode.value) {
    errorMessage.value = "请输入房间号";
    return;
  }
  resetMessages();
  closeResultDialog();
  stopAiGame();
  socket.send("room:join", { code: joinCode.value.toUpperCase() });
};

const leaveRoom = (): void => {
  socket.send("room:leave", {});
  room.value = null;
  closeResultDialog();
};

const requestUndo = (): void => {
  if (!room.value || !canRequestUndo.value) {
    return;
  }
  errorMessage.value = "";
  socket.send("game:undo_request", { roomCode: room.value.code });
};

const respondUndo = (accept: boolean): void => {
  if (!room.value || !isUndoPending.value || isUndoRequesterMe.value) {
    return;
  }
  errorMessage.value = "";
  socket.send("game:undo_response", { roomCode: room.value.code, accept });
};

const createNewAiGame = (starterColor: StoneColor): PveGameState => ({
  board: createEmptyBoard(),
  starterColor,
  currentTurn: starterColor,
  status: "in_progress",
  winnerColor: null,
  reason: "normal",
});

const startAiGame = (): void => {
  resetMessages();
  closeResultDialog();
  if (room.value) {
    leaveRoom();
  }

  const starterColor = nextAiStarterColor.value;
  nextAiStarterColor.value = starterColor === BLACK_STONE ? WHITE_STONE : BLACK_STONE;
  aiGame.value = createNewAiGame(starterColor);

  if (starterColor === HUMAN_COLOR) {
    notice.value = `已开始人机对战（${aiDifficultyText.value}）：本局黑棋先行（你先手）`;
    return;
  }

  notice.value = `已开始人机对战（${aiDifficultyText.value}）：本局白棋先行（AI 先手）`;
  clearAiTimer();
  aiTimer = setTimeout(() => {
    void makeAiMove();
  }, 420);
};

const finishAiGame = (winnerColor: StoneColor | null, reason: "normal" | "draw"): void => {
  if (!aiGame.value) return;
  aiGame.value.status = "ended";
  aiGame.value.winnerColor = winnerColor;
  aiGame.value.reason = reason;
  clearAiTimer();

  if (reason === "draw" || winnerColor === null) {
    openResultDialog("ai", "draw", "本局平局", "是否开启下一局人机对战？");
    return;
  }

  if (winnerColor === HUMAN_COLOR) {
    openResultDialog("ai", "win", "恭喜你获胜 🎉", "是否立即开启下一局人机对战？");
    return;
  }

  openResultDialog("ai", "lose", "很遗憾你输了", "是否立即开启下一局再挑战 AI？");
};

const makeAiMove = async (): Promise<void> => {
  if (!aiGame.value || aiGame.value.status !== "in_progress" || aiGame.value.currentTurn !== AI_COLOR) {
    return;
  }

  const token = session.state.token;
  if (!token) {
    errorMessage.value = "登录状态已失效，请重新登录后继续人机对战";
    return;
  }

  const boardSnapshot = aiGame.value.board.map((row) => [...row]);

  try {
    const response = await aiApi.nextMove(
      {
        board: boardSnapshot,
        aiColor: AI_COLOR,
        humanColor: HUMAN_COLOR,
        difficulty: aiDifficulty.value,
        maxThinkMs: AI_THINK_MS_BY_DIFFICULTY[aiDifficulty.value],
      },
      token,
    );

    if (!aiGame.value || aiGame.value.status !== "in_progress" || aiGame.value.currentTurn !== AI_COLOR) {
      return;
    }

    if (response.x === null || response.y === null) {
      finishAiGame(null, "draw");
      notice.value = "平局，棋盘已满。";
      return;
    }

    if (!inBounds(response.x, response.y) || aiGame.value.board[response.y][response.x] !== 0) {
      throw new Error("后端返回了非法落子坐标");
    }

    aiGame.value.board[response.y][response.x] = AI_COLOR;

    if (checkWinAt(aiGame.value.board, response.x, response.y, AI_COLOR)) {
      finishAiGame(AI_COLOR, "normal");
      notice.value = "AI 获胜，本局结束。";
      return;
    }

    if (isBoardFull(aiGame.value.board)) {
      finishAiGame(null, "draw");
      notice.value = "平局，棋盘已满。";
      return;
    }

    aiGame.value.currentTurn = HUMAN_COLOR;
    notice.value = "轮到你落子";
  } catch (error) {
    errorMessage.value = `AI 落子失败：${(error as Error).message}`;
    notice.value = "请检查后端 AI 服务是否可用，然后重试。";
  }
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
      void makeAiMove();
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
  closeResultDialog();
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
      if (typed.room.status === "in_progress" && typed.room.moves.length === 0) {
        closeResultDialog();
      }
    }),
  );

  cleanupFns.push(
    socket.on("game:start", (payload) => {
      if (aiGame.value) {
        return;
      }
      const typed = payload as { roomCode?: string; currentTurn?: StoneColor };
      closeResultDialog();
      const starter = typed.currentTurn ? `${stoneText(typed.currentTurn)}先手` : "已开始";
      notice.value = typed.roomCode
        ? `新一局已在房间 ${typed.roomCode} 开始（${starter}）`
        : `新一局${starter}`;
    }),
  );

  cleanupFns.push(
    socket.on("system:notice", (payload) => {
      const typed = payload as { message?: string };
      if (typed.message) {
        notice.value = typed.message;
      }
    }),
  );

  cleanupFns.push(
    socket.on("game:undo_requested", () => {
      notice.value = "已发送悔棋请求，等待对手确认";
    }),
  );

  cleanupFns.push(
    socket.on("game:undo_request_received", (payload) => {
      const typed = payload as { requesterUsername?: string };
      notice.value = typed.requesterUsername
        ? `${typed.requesterUsername} 请求悔棋，请选择同意或拒绝`
        : "对手请求悔棋，请选择同意或拒绝";
    }),
  );

  cleanupFns.push(
    socket.on("game:undo_applied", (payload) => {
      const typed = payload as { requesterUsername?: string };
      notice.value = typed.requesterUsername
        ? `已悔棋，撤销了 ${typed.requesterUsername} 请求撤销的上一步`
        : "已悔棋，棋盘已回退一步";
    }),
  );

  cleanupFns.push(
    socket.on("game:undo_rejected", (payload) => {
      const typed = payload as { responderUsername?: string };
      notice.value = typed.responderUsername
        ? `${typed.responderUsername} 拒绝了悔棋请求`
        : "对手拒绝了悔棋请求";
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
    socket.on("game:ended", async (payload) => {
      if (aiGame.value) {
        return;
      }

      const typed = payload as {
        winnerUserId?: number | null;
        winnerColor?: number | null;
        reason?: string;
      };
      const myUserId = session.state.user?.id;

      if (!typed.winnerUserId || typed.reason === "draw") {
        notice.value = "联机对局结束：平局";
        openResultDialog("online", "draw", "联机对局平局", "是否在当前房间开启下一局？");
      } else if (myUserId && typed.winnerUserId === myUserId) {
        notice.value = "联机对局结束：你获胜了！";
        openResultDialog("online", "win", "你赢了 🎉", "是否在当前房间开启下一局？");
      } else {
        notice.value = "联机对局结束：你输了";
        openResultDialog("online", "lose", "这局惜败", "是否在当前房间再来一局？");
      }

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
  margin-bottom: 10px;
}

.topbar h1 {
  margin: 0;
  font-size: clamp(24px, 4.2vw, 30px);
}

.topbar p {
  margin: 4px 0 0;
  color: #666;
  font-size: 14px;
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
  display: grid;
  gap: 8px;
  margin-bottom: 8px;
}

.mode-actions {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mode-actions button {
  width: 100%;
}

.join-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.join-row input {
  min-width: 0;
}

.difficulty-row {
  display: grid;
  gap: 6px;
  margin-bottom: 10px;
}

.difficulty-row > span {
  color: #667085;
  font-size: 13px;
}

.difficulty-options {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.difficulty-btn {
  min-height: 34px;
  border-radius: 999px;
  background: #f5f7fb;
  border: 1px solid #dbe2eb;
  color: #334155;
  font-size: 13px;
  padding: 0 8px;
}

.difficulty-btn.active {
  background: #e8f0ff;
  border-color: #7ea6ff;
  color: #154ab5;
  font-weight: 600;
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

.players .starter-tag {
  background: #fff7e6;
  border: 1px solid #f4cf83;
}

.players span.me {
  border: 1px solid #1f6feb;
}

.players span.disconnected {
  opacity: 0.6;
}

.undo-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.undo-hint {
  color: #4b5563;
  font-size: 13px;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.tip {
  margin: 0;
  color: #555;
}

.compact-tip {
  margin: 0 0 8px;
  font-size: 13px;
}

.error {
  margin: 0;
  color: #c62828;
}

.result-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  background: rgb(0 0 0 / 40%);
  padding: 16px;
}

.result-dialog {
  width: min(92vw, 360px);
  background: #fff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 16px 40px rgb(0 0 0 / 24%);
  border: 2px solid #dbe2eb;
}

.result-dialog h3 {
  margin: 0 0 8px;
  font-size: 20px;
}

.result-dialog p {
  margin: 0;
  color: #444;
}

.result-dialog.win {
  border-color: #4caf50;
}

.result-dialog.lose {
  border-color: #ef5350;
}

.result-dialog.draw {
  border-color: #90a4ae;
}

.result-actions {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.result-actions button {
  min-width: 106px;
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
  .app-shell {
    padding-top: max(8px, env(safe-area-inset-top));
    padding-bottom: max(8px, env(safe-area-inset-bottom));
    padding-left: max(8px, env(safe-area-inset-left));
    padding-right: max(8px, env(safe-area-inset-right));
  }

  .topbar h1 {
    font-size: 21px;
  }

  .topbar p {
    display: none;
  }

  .card {
    padding: 10px;
  }

  input,
  button {
    min-height: 40px;
    font-size: 14px;
  }

  .layout,
  .left-col,
  .right-col {
    gap: 8px;
  }

  .lobby-card {
    position: sticky;
    top: max(6px, env(safe-area-inset-top));
    z-index: 2;
  }

  .game-head {
    align-items: start;
    flex-direction: column;
  }

  .players span {
    font-size: 12px;
    padding: 4px 8px;
  }

  .undo-hint {
    font-size: 12px;
  }

  .difficulty-options {
    gap: 4px;
  }

  .difficulty-btn {
    min-height: 32px;
    font-size: 12px;
    padding: 0 6px;
  }

  .result-actions {
    flex-direction: column;
  }
}
</style>
