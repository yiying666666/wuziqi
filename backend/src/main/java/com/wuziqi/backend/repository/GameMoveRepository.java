package com.wuziqi.backend.repository;

import com.wuziqi.backend.entity.GameMoveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameMoveRepository extends JpaRepository<GameMoveEntity, Long> {
}
