package com.wuziqi.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuziqi.backend.entity.GameEntity;
import com.wuziqi.backend.exception.ApiException;
import com.wuziqi.backend.repository.GameRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/rooms")
public class RoomController {
    private final GameRepository gameRepository;
    private final ObjectMapper objectMapper;

    public RoomController(GameRepository gameRepository, ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{code}")
    public Map<String, Object> snapshot(@PathVariable String code) {
        GameEntity game = gameRepository.findTopByRoomCodeOrderByEndedAtDesc(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(404, "Room snapshot not found"));

        Map<String, Object> result = new HashMap<>();
        result.put("room_code", game.getRoomCode());
        result.put("ended_at", game.getEndedAt());
        result.put("winner_user_id", game.getWinnerUserId());
        result.put("result", game.getResult());

        try {
            result.put("board_snapshot", objectMapper.readValue(game.getBoardSnapshot(), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ex) {
            result.put("board_snapshot", Map.of());
        }

        return result;
    }
}
