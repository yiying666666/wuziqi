package com.wuziqi.backend.websocket;

import com.wuziqi.backend.game.GomokuEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RoomModels {
    private RoomModels() {
    }

    public enum RoomStatus {
        waiting,
        in_progress,
        ended
    }

    public static class PlayerState {
        private Integer userId;
        private String username;
        private Integer color;
        private boolean connected;
        private Long disconnectedAt;

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Integer getColor() {
            return color;
        }

        public void setColor(Integer color) {
            this.color = color;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        public Long getDisconnectedAt() {
            return disconnectedAt;
        }

        public void setDisconnectedAt(Long disconnectedAt) {
            this.disconnectedAt = disconnectedAt;
        }
    }

    public static class MoveState {
        private Integer x;
        private Integer y;
        private Integer color;
        private Integer moveNo;
        private Instant createdAt;

        public Integer getX() {
            return x;
        }

        public void setX(Integer x) {
            this.x = x;
        }

        public Integer getY() {
            return y;
        }

        public void setY(Integer y) {
            this.y = y;
        }

        public Integer getColor() {
            return color;
        }

        public void setColor(Integer color) {
            this.color = color;
        }

        public Integer getMoveNo() {
            return moveNo;
        }

        public void setMoveNo(Integer moveNo) {
            this.moveNo = moveNo;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class RoomState {
        private String code;
        private int[][] board = GomokuEngine.createBoard();
        private Integer currentTurn = 1;
        private RoomStatus status = RoomStatus.waiting;
        private List<PlayerState> players = new ArrayList<>();
        private List<MoveState> moves = new ArrayList<>();
        private Long gameId;
        private Instant startedAt;
        private Instant endedAt;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public int[][] getBoard() {
            return board;
        }

        public void setBoard(int[][] board) {
            this.board = board;
        }

        public Integer getCurrentTurn() {
            return currentTurn;
        }

        public void setCurrentTurn(Integer currentTurn) {
            this.currentTurn = currentTurn;
        }

        public RoomStatus getStatus() {
            return status;
        }

        public void setStatus(RoomStatus status) {
            this.status = status;
        }

        public List<PlayerState> getPlayers() {
            return players;
        }

        public void setPlayers(List<PlayerState> players) {
            this.players = players;
        }

        public List<MoveState> getMoves() {
            return moves;
        }

        public void setMoves(List<MoveState> moves) {
            this.moves = moves;
        }

        public Long getGameId() {
            return gameId;
        }

        public void setGameId(Long gameId) {
            this.gameId = gameId;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(Instant startedAt) {
            this.startedAt = startedAt;
        }

        public Instant getEndedAt() {
            return endedAt;
        }

        public void setEndedAt(Instant endedAt) {
            this.endedAt = endedAt;
        }
    }
}
