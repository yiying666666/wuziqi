package com.wuziqi.backend.dto;

public record AiMoveRequest(
        int[][] board,
        Integer aiColor,
        Integer humanColor,
        String difficulty,
        Integer maxThinkMs
) {
}
