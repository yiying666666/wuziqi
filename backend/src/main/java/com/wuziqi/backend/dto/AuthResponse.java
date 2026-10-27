package com.wuziqi.backend.dto;

public record AuthResponse(String token, UserDto user) {
}
