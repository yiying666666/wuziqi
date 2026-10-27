package com.wuziqi.backend.repository;

import com.wuziqi.backend.entity.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameRepository extends JpaRepository<GameEntity, Long> {
    Optional<GameEntity> findTopByRoomCodeOrderByEndedAtDesc(String roomCode);
}
