package com.wuziqi.backend.dto;

public record ProfileDto(
        Integer id,
        String username,
        Integer elo,
        Integer wins,
        Integer losses,
        Integer draws
) {
}
