package com.wuziqi.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "game_moves", uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_move_no", columnNames = {"game_id", "move_no"})
})
public class GameMoveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "move_no", nullable = false)
    private Integer moveNo;

    @Column(nullable = false)
    private Integer x;

    @Column(nullable = false)
    private Integer y;

    @Column(nullable = false)
    private Integer color;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public void setMoveNo(Integer moveNo) {
        this.moveNo = moveNo;
    }

    public void setX(Integer x) {
        this.x = x;
    }

    public void setY(Integer y) {
        this.y = y;
    }

    public void setColor(Integer color) {
        this.color = color;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
