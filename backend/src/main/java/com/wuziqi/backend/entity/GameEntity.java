package com.wuziqi.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "games")
public class GameEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, length = 12)
    private String roomCode;

    @Column(name = "black_user_id", nullable = false)
    private Integer blackUserId;

    @Column(name = "white_user_id", nullable = false)
    private Integer whiteUserId;

    @Column(name = "winner_user_id")
    private Integer winnerUserId;

    @Column(nullable = false, length = 10)
    private String result;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "board_snapshot", nullable = false, columnDefinition = "text")
    private String boardSnapshot;

    public Long getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public Integer getBlackUserId() {
        return blackUserId;
    }

    public void setBlackUserId(Integer blackUserId) {
        this.blackUserId = blackUserId;
    }

    public Integer getWhiteUserId() {
        return whiteUserId;
    }

    public void setWhiteUserId(Integer whiteUserId) {
        this.whiteUserId = whiteUserId;
    }

    public Integer getWinnerUserId() {
        return winnerUserId;
    }

    public void setWinnerUserId(Integer winnerUserId) {
        this.winnerUserId = winnerUserId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
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

    public String getBoardSnapshot() {
        return boardSnapshot;
    }

    public void setBoardSnapshot(String boardSnapshot) {
        this.boardSnapshot = boardSnapshot;
    }
}
