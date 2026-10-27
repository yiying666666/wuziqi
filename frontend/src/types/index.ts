export type StoneColor = 1 | 2;

export interface User {
  id: number;
  username: string;
}

export interface Profile {
  id: number;
  username: string;
  elo: number;
  wins: number;
  losses: number;
  draws: number;
}

export interface LeaderboardItem {
  rank: number;
  userId: number;
  username: string;
  elo: number;
  wins: number;
  losses: number;
  draws: number;
}

export interface RoomPlayer {
  userId: number;
  username: string;
  color: StoneColor;
  connected: boolean;
  disconnectedAt?: number;
}

export interface Move {
  x: number;
  y: number;
  color: StoneColor;
  moveNo: number;
  createdAt: string;
}

export interface RoomState {
  code: string;
  board: number[][];
  currentTurn: StoneColor;
  status: "waiting" | "in_progress" | "ended";
  players: RoomPlayer[];
  moves: Move[];
  gameId?: number;
  startedAt?: string;
  endedAt?: string;
}

export interface WsPacket<T = unknown> {
  event: string;
  payload: T;
}
