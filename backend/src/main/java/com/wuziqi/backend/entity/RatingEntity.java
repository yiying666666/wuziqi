package com.wuziqi.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ratings")
public class RatingEntity {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(nullable = false)
    private Integer elo = 1200;

    @Column(name = "win_count", nullable = false)
    private Integer winCount = 0;

    @Column(name = "lose_count", nullable = false)
    private Integer loseCount = 0;

    @Column(name = "draw_count", nullable = false)
    private Integer drawCount = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getElo() {
        return elo;
    }

    public void setElo(Integer elo) {
        this.elo = elo;
    }

    public Integer getWinCount() {
        return winCount;
    }

    public void setWinCount(Integer winCount) {
        this.winCount = winCount;
    }

    public Integer getLoseCount() {
        return loseCount;
    }

    public void setLoseCount(Integer loseCount) {
        this.loseCount = loseCount;
    }

    public Integer getDrawCount() {
        return drawCount;
    }

    public void setDrawCount(Integer drawCount) {
        this.drawCount = drawCount;
    }
}
