package com.wuziqi.backend.dto;

public record LeaderboardItemDto(
        Integer rank,
        Integer userId,
        String username,
        Integer elo,
        Integer wins,
        Integer losses,
        Integer draws
) {
}
