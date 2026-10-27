package com.wuziqi.backend.repository;

import com.wuziqi.backend.entity.RatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RatingRepository extends JpaRepository<RatingEntity, Integer> {
    List<RatingEntity> findByUserIdIn(List<Integer> userIds);
}
