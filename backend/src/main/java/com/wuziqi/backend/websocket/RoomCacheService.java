package com.wuziqi.backend.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuziqi.backend.config.AppProperties;
import com.wuziqi.backend.websocket.RoomModels.RoomState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class RoomCacheService {
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public RoomCacheService(Optional<StringRedisTemplate> redisTemplate,
                            AppProperties appProperties,
                            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate.orElse(null);
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public void saveRoom(RoomState room) {
        if (!isAvailable()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(roomKey(room.getCode()), objectMapper.writeValueAsString(room), TTL);
        } catch (Exception ignored) {
        }
    }

    public RoomState loadRoom(String code) {
        if (!isAvailable()) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue().get(roomKey(code));
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, RoomState.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void deleteRoom(String code) {
        if (!isAvailable()) {
            return;
        }
        try {
            redisTemplate.delete(roomKey(code));
        } catch (Exception ignored) {
        }
    }

    public void saveUserRoom(Integer userId, String roomCode) {
        if (!isAvailable()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(userRoomKey(userId), roomCode, TTL);
        } catch (Exception ignored) {
        }
    }

    public String loadUserRoom(Integer userId) {
        if (!isAvailable()) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(userRoomKey(userId));
        } catch (Exception ignored) {
            return null;
        }
    }

    public void deleteUserRoom(Integer userId) {
        if (!isAvailable()) {
            return;
        }
        try {
            redisTemplate.delete(userRoomKey(userId));
        } catch (Exception ignored) {
        }
    }

    private boolean isAvailable() {
        return appProperties.redisEnabled() && redisTemplate != null;
    }

    private String roomKey(String code) {
        return "room:" + code;
    }

    private String userRoomKey(Integer userId) {
        return "user_room:" + userId;
    }
}
