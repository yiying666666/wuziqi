package com.wuziqi.backend.repository;

import com.wuziqi.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
