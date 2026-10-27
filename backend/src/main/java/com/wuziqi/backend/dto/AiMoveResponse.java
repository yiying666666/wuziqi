package com.wuziqi.backend.dto;

public record AiMoveResponse(
        Integer x,
        Integer y,
        Integer color,
        String difficulty,
        Integer searchedDepth,
        Integer candidateCount,
        Long elapsedMs,
        String reason
) {
}
