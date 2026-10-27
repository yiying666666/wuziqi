package com.wuziqi.backend.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuziqi.backend.config.AppProperties;
import com.wuziqi.backend.entity.GameEntity;
import com.wuziqi.backend.entity.GameMoveEntity;
import com.wuziqi.backend.entity.RatingEntity;
import com.wuziqi.backend.exception.ApiException;
import com.wuziqi.backend.game.GomokuEngine;
import com.wuziqi.backend.repository.GameMoveRepository;
import com.wuziqi.backend.repository.GameRepository;
import com.wuziqi.backend.repository.RatingRepository;
import com.wuziqi.backend.service.EloService;
import com.wuziqi.backend.service.JwtService;
import com.wuziqi.backend.service.UserPrincipal;
import com.wuziqi.backend.websocket.RoomModels.MoveState;
import com.wuziqi.backend.websocket.RoomModels.PlayerState;
import com.wuziqi.backend.websocket.RoomModels.RoomState;
import com.wuziqi.backend.websocket.RoomModels.RoomStatus;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final EloService eloService;
    private final RatingRepository ratingRepository;
    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final RoomCacheService roomCacheService;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;

    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, SessionUser> sessionUsers = new ConcurrentHashMap<>();
    private final Map<Integer, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> reconnectTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public GameWebSocketHandler(ObjectMapper objectMapper,
                                JwtService jwtService,
                                EloService eloService,
                                RatingRepository ratingRepository,
                                GameRepository gameRepository,
                                GameMoveRepository gameMoveRepository,
                                RoomCacheService roomCacheService,
                                AppProperties appProperties,
                                PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.eloService = eloService;
        this.ratingRepository = ratingRepository;
        this.gameRepository = gameRepository;
        this.gameMoveRepository = gameMoveRepository;
        this.roomCacheService = roomCacheService;
        this.appProperties = appProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session.getUri());
        if (token == null || token.isBlank()) {
            emit(session, "error", Map.of("message", "Missing token"));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        UserPrincipal principal;
        try {
            principal = jwtService.verifyAndGetUser(token);
        } catch (ApiException ex) {
            emit(session, "error", Map.of("message", ex.getMessage()));
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        WebSocketSession previousSession = userSessions.put(principal.userId(), session);
        if (previousSession != null && previousSession.isOpen() && previousSession != session) {
            previousSession.close(CloseStatus.NORMAL);
        }

        SessionUser sessionUser = new SessionUser(principal.userId(), principal.username());
        sessionUsers.put(session, sessionUser);

        tryRecoverRoom(sessionUser);
        emit(session, "system:connected", Map.of("userId", principal.userId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionUser user = sessionUsers.get(session);
        if (user == null) {
            return;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            String event = jsonNode.path("event").asText();
            JsonNode payload = jsonNode.path("payload");

            switch (event) {
                case "room:create" -> handleCreateRoom(user);
                case "room:join" -> {
                    String code = payload.path("code").asText("").toUpperCase(Locale.ROOT);
                    if (code.length() < 4 || code.length() > 12) {
                        emitError(user.userId, "Invalid room code");
                        return;
                    }
                    handleJoinRoom(user, code);
                }
                case "room:leave" -> handleLeaveRoom(user);
                case "game:move" -> {
                    String roomCode = payload.path("roomCode").asText("").toUpperCase(Locale.ROOT);
                    int x = payload.path("x").asInt(-1);
                    int y = payload.path("y").asInt(-1);
                    handleMove(user, roomCode, x, y);
                }
                default -> emitError(user.userId, "Unknown event: " + event);
            }
        } catch (ApiException ex) {
            emitError(user.userId, ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            emitError(user.userId, "Server error");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionUser user = sessionUsers.remove(session);
        if (user == null) {
            return;
        }

        WebSocketSession mappedSession = userSessions.get(user.userId);
        if (mappedSession == session) {
            userSessions.remove(user.userId);
        }

        if (user.roomCode != null) {
            markDisconnected(user.roomCode, user.userId);
        }
    }

    private synchronized void handleCreateRoom(SessionUser user) {
        if (user.roomCode != null) {
            RoomState existing = getRoom(user.roomCode);
            if (existing != null) {
                emit(user.userId, "room:state", Map.of("room", existing));
                return;
            }
            user.roomCode = null;
        }

        String code = generateRoomCode();
        RoomState room = new RoomState();
        room.setCode(code);

        PlayerState player = new PlayerState();
        player.setUserId(user.userId);
        player.setUsername(user.username);
        player.setColor(1);
        player.setConnected(true);
        room.getPlayers().add(player);

        rooms.put(code, room);
        user.roomCode = code;
        roomCacheService.saveUserRoom(user.userId, code);
        persistRoom(room);

        emit(user.userId, "room:state", Map.of("room", room));
    }

    private synchronized void handleJoinRoom(SessionUser user, String code) {
        RoomState room = getRoom(code);
        if (room == null) {
            emitError(user.userId, "Room not found");
            return;
        }

        PlayerState self = findPlayer(room, user.userId);
        if (self != null) {
            self.setConnected(true);
            self.setDisconnectedAt(null);
            user.roomCode = room.getCode();
            roomCacheService.saveUserRoom(user.userId, room.getCode());
            clearReconnectTimer(room.getCode(), user.userId);
            persistRoom(room);
            broadcastRoomState(room);
            broadcast(room, "player:reconnected", Map.of(
                    "userId", user.userId,
                    "username", user.username
            ));
            return;
        }

        if (room.getPlayers().size() >= 2) {
            emitError(user.userId, "Room is full");
            return;
        }

        PlayerState player = new PlayerState();
        player.setUserId(user.userId);
        player.setUsername(user.username);
        player.setColor(2);
        player.setConnected(true);

        room.getPlayers().add(player);
        room.setStatus(RoomStatus.in_progress);
        room.setStartedAt(Instant.now());

        user.roomCode = room.getCode();
        roomCacheService.saveUserRoom(user.userId, room.getCode());
        persistRoom(room);

        broadcastRoomState(room);
        broadcast(room, "game:start", Map.of(
                "roomCode", room.getCode(),
                "currentTurn", room.getCurrentTurn(),
                "players", room.getPlayers()
        ));
    }

    private synchronized void handleLeaveRoom(SessionUser user) {
        if (user.roomCode == null) {
            return;
        }

        RoomState room = getRoom(user.roomCode);
        roomCacheService.deleteUserRoom(user.userId);
        user.roomCode = null;

        if (room == null) {
            return;
        }

        if (room.getStatus() == RoomStatus.waiting) {
            room.getPlayers().removeIf(player -> Objects.equals(player.getUserId(), user.userId));
            if (room.getPlayers().isEmpty()) {
                destroyRoom(room.getCode());
                return;
            }
            persistRoom(room);
            broadcastRoomState(room);
            return;
        }

        if (room.getStatus() == RoomStatus.in_progress) {
            room.setStatus(RoomStatus.ended);
            room.setEndedAt(Instant.now());

            PlayerState leaver = findPlayer(room, user.userId);
            if (leaver != null) {
                leaver.setConnected(false);
            }

            PlayerState winner = room.getPlayers().stream()
                    .filter(player -> !Objects.equals(player.getUserId(), user.userId))
                    .findFirst()
                    .orElse(null);

            settleGame(room, winner != null ? winner.getColor() : null, "forfeit");
            persistRoom(room);
            broadcast(room, "game:ended", payloadMap(
                    "roomCode", room.getCode(),
                    "winnerUserId", winner != null ? winner.getUserId() : null,
                    "winnerColor", winner != null ? winner.getColor() : null,
                    "reason", "forfeit"
            ));
            broadcastRoomState(room);
        }
    }

    private synchronized void handleMove(SessionUser user, String roomCode, int x, int y) {
        if (roomCode.isBlank()) {
            emit(user.userId, "game:invalid_move", Map.of("message", "Missing room code"));
            return;
        }

        RoomState room = getRoom(roomCode);
        if (room == null || room.getStatus() != RoomStatus.in_progress) {
            emit(user.userId, "game:invalid_move", Map.of("message", "Room not available"));
            return;
        }

        PlayerState player = findPlayer(room, user.userId);
        if (player == null) {
            emit(user.userId, "game:invalid_move", Map.of("message", "Not in room"));
            return;
        }

        if (!Objects.equals(player.getColor(), room.getCurrentTurn())) {
            emit(user.userId, "game:invalid_move", Map.of("message", "Not your turn"));
            return;
        }

        if (!GomokuEngine.isInBounds(x, y)) {
            emit(user.userId, "game:invalid_move", Map.of("message", "Out of board"));
            return;
        }

        if (room.getBoard()[y][x] != 0) {
            emit(user.userId, "game:invalid_move", Map.of("message", "Cell occupied"));
            return;
        }

        room.getBoard()[y][x] = player.getColor();

        MoveState move = new MoveState();
        move.setX(x);
        move.setY(y);
        move.setColor(player.getColor());
        move.setMoveNo(room.getMoves().size() + 1);
        move.setCreatedAt(Instant.now());
        room.getMoves().add(move);

        boolean won = GomokuEngine.checkWin(room.getBoard(), x, y, player.getColor());
        boolean draw = !won && GomokuEngine.isFull(room.getBoard());

        if (won || draw) {
            room.setStatus(RoomStatus.ended);
            room.setEndedAt(Instant.now());
            Integer winnerColor = won ? player.getColor() : null;
            settleGame(room, winnerColor, won ? "normal" : "draw");
            persistRoom(room);

            broadcast(room, "game:moved", Map.of(
                    "roomCode", roomCode,
                    "x", x,
                    "y", y,
                    "color", player.getColor(),
                    "moveNo", move.getMoveNo(),
                    "nextTurn", room.getCurrentTurn()
            ));

            PlayerState winner = winnerColor == null ? null : room.getPlayers().stream()
                    .filter(p -> Objects.equals(p.getColor(), winnerColor))
                    .findFirst().orElse(null);

            broadcast(room, "game:ended", payloadMap(
                    "roomCode", roomCode,
                    "winnerColor", winnerColor,
                    "winnerUserId", winner != null ? winner.getUserId() : null,
                    "reason", won ? "normal" : "draw"
            ));
            broadcastRoomState(room);
            return;
        }

        room.setCurrentTurn(room.getCurrentTurn() == 1 ? 2 : 1);
        persistRoom(room);

        broadcast(room, "game:moved", Map.of(
                "roomCode", roomCode,
                "x", x,
                "y", y,
                "color", player.getColor(),
                "moveNo", move.getMoveNo(),
                "nextTurn", room.getCurrentTurn()
        ));
        broadcastRoomState(room);
    }

    private synchronized void markDisconnected(String roomCode, Integer userId) {
        RoomState room = getRoom(roomCode);
        if (room == null) {
            return;
        }

        PlayerState player = findPlayer(room, userId);
        if (player == null) {
            return;
        }

        player.setConnected(false);
        player.setDisconnectedAt(System.currentTimeMillis());
        persistRoom(room);

        broadcast(room, "player:disconnected", Map.of(
                "userId", player.getUserId(),
                "username", player.getUsername(),
                "graceSeconds", appProperties.reconnectGraceSeconds()
        ));

        scheduleReconnectTimeout(roomCode, userId);
    }

    private synchronized void tryRecoverRoom(SessionUser user) {
        String roomCode = roomCacheService.loadUserRoom(user.userId);
        if (roomCode == null) {
            return;
        }

        RoomState room = getRoom(roomCode);
        if (room == null) {
            return;
        }

        PlayerState player = findPlayer(room, user.userId);
        if (player == null) {
            return;
        }

        player.setConnected(true);
        player.setDisconnectedAt(null);
        user.roomCode = roomCode;
        clearReconnectTimer(roomCode, user.userId);
        persistRoom(room);

        emit(user.userId, "room:state", Map.of("room", room));
        broadcast(room, "player:reconnected", Map.of(
                "userId", user.userId,
                "username", user.username
        ));
    }

    private void scheduleReconnectTimeout(String roomCode, Integer userId) {
        clearReconnectTimer(roomCode, userId);
        String key = roomCode + ":" + userId;

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            synchronized (GameWebSocketHandler.this) {
                RoomState room = getRoom(roomCode);
                if (room == null || room.getStatus() != RoomStatus.in_progress) {
                    return;
                }

                PlayerState player = findPlayer(room, userId);
                if (player == null || player.isConnected()) {
                    return;
                }

                room.setStatus(RoomStatus.ended);
                room.setEndedAt(Instant.now());

                PlayerState winner = room.getPlayers().stream()
                        .filter(p -> !Objects.equals(p.getUserId(), userId))
                        .findFirst()
                        .orElse(null);

                settleGame(room, winner != null ? winner.getColor() : null, "disconnect_timeout");
                persistRoom(room);

                broadcast(room, "game:ended", payloadMap(
                        "roomCode", roomCode,
                        "winnerUserId", winner != null ? winner.getUserId() : null,
                        "winnerColor", winner != null ? winner.getColor() : null,
                        "reason", "disconnect_timeout"
                ));
                broadcastRoomState(room);
            }
        }, appProperties.reconnectGraceSeconds(), TimeUnit.SECONDS);

        reconnectTasks.put(key, future);
    }

    private void clearReconnectTimer(String roomCode, Integer userId) {
        String key = roomCode + ":" + userId;
        ScheduledFuture<?> future = reconnectTasks.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void settleGame(RoomState room, Integer winnerColor, String reason) {
        PlayerState black = room.getPlayers().stream()
                .filter(player -> Objects.equals(player.getColor(), 1))
                .findFirst().orElse(null);
        PlayerState white = room.getPlayers().stream()
                .filter(player -> Objects.equals(player.getColor(), 2))
                .findFirst().orElse(null);

        if (black == null || white == null) {
            return;
        }

        String result = winnerColor == null
                ? "draw"
                : winnerColor == 1 ? "black_win" : "white_win";

        transactionTemplate.executeWithoutResult(status -> {
            Map<Integer, RatingEntity> ratings = ratingRepository.findByUserIdIn(List.of(black.getUserId(), white.getUserId()))
                    .stream()
                    .collect(HashMap::new, (map, value) -> map.put(value.getUserId(), value), HashMap::putAll);

            RatingEntity blackRating = ratings.computeIfAbsent(black.getUserId(), userId -> {
                RatingEntity entity = new RatingEntity();
                entity.setUserId(userId);
                return entity;
            });
            RatingEntity whiteRating = ratings.computeIfAbsent(white.getUserId(), userId -> {
                RatingEntity entity = new RatingEntity();
                entity.setUserId(userId);
                return entity;
            });

            double scoreBlack = winnerColor == null ? 0.5 : (winnerColor == 1 ? 1d : 0d);
            EloService.EloResult eloResult = eloService.calculate(blackRating.getElo(), whiteRating.getElo(), scoreBlack, 32);

            blackRating.setElo(eloResult.nextA());
            whiteRating.setElo(eloResult.nextB());

            if (Objects.equals(winnerColor, 1)) {
                blackRating.setWinCount(blackRating.getWinCount() + 1);
                whiteRating.setLoseCount(whiteRating.getLoseCount() + 1);
            } else if (Objects.equals(winnerColor, 2)) {
                whiteRating.setWinCount(whiteRating.getWinCount() + 1);
                blackRating.setLoseCount(blackRating.getLoseCount() + 1);
            } else {
                blackRating.setDrawCount(blackRating.getDrawCount() + 1);
                whiteRating.setDrawCount(whiteRating.getDrawCount() + 1);
            }

            ratingRepository.save(blackRating);
            ratingRepository.save(whiteRating);

            GameEntity game = new GameEntity();
            game.setRoomCode(room.getCode());
            game.setBlackUserId(black.getUserId());
            game.setWhiteUserId(white.getUserId());
            game.setWinnerUserId(
                    winnerColor == null ? null : (winnerColor == 1 ? black.getUserId() : white.getUserId())
            );
            game.setResult(result);
            game.setStartedAt(room.getStartedAt() != null
                    ? room.getStartedAt()
                    : (room.getMoves().isEmpty() ? Instant.now() : room.getMoves().get(0).getCreatedAt()));
            game.setEndedAt(room.getEndedAt() != null ? room.getEndedAt() : Instant.now());
            try {
                Map<String, Object> snapshot = payloadMap("board", room.getBoard(), "reason", reason);
                game.setBoardSnapshot(objectMapper.writeValueAsString(snapshot));
            } catch (Exception ex) {
                game.setBoardSnapshot("{}");
            }
            gameRepository.save(game);
            room.setGameId(game.getId());

            List<GameMoveEntity> moves = new ArrayList<>();
            for (MoveState move : room.getMoves()) {
                GameMoveEntity moveEntity = new GameMoveEntity();
                moveEntity.setGameId(game.getId());
                moveEntity.setMoveNo(move.getMoveNo());
                moveEntity.setX(move.getX());
                moveEntity.setY(move.getY());
                moveEntity.setColor(move.getColor());
                moveEntity.setCreatedAt(move.getCreatedAt());
                moves.add(moveEntity);
            }
            if (!moves.isEmpty()) {
                gameMoveRepository.saveAll(moves);
            }
        });
    }


    private Map<String, Object> payloadMap(Object... keyValues) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }

    private void broadcastRoomState(RoomState room) {
        broadcast(room, "room:state", Map.of("room", room));
    }

    private void broadcast(RoomState room, String event, Object payload) {
        for (PlayerState player : room.getPlayers()) {
            emit(player.getUserId(), event, payload);
        }
    }

    private void emitError(Integer userId, String message) {
        emit(userId, "error", Map.of("message", message));
    }

    private void emit(Integer userId, String event, Object payload) {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) {
            return;
        }
        emit(session, event, payload);
    }

    private void emit(WebSocketSession session, String event, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String content = objectMapper.writeValueAsString(payloadMap(
                    "event", event,
                    "payload", payload
            ));
            session.sendMessage(new TextMessage(content));
        } catch (IOException ignored) {
        }
    }

    private RoomState getRoom(String code) {
        RoomState room = rooms.get(code);
        if (room != null) {
            return room;
        }
        RoomState fromRedis = roomCacheService.loadRoom(code);
        if (fromRedis != null) {
            rooms.put(code, fromRedis);
        }
        return fromRedis;
    }

    private void persistRoom(RoomState room) {
        rooms.put(room.getCode(), room);
        roomCacheService.saveRoom(room);
    }

    private void destroyRoom(String code) {
        rooms.remove(code);
        roomCacheService.deleteRoom(code);
    }

    private String generateRoomCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String code;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = builder.toString();
        } while (rooms.containsKey(code));
        return code;
    }

    private PlayerState findPlayer(RoomState room, Integer userId) {
        for (PlayerState player : room.getPlayers()) {
            if (Objects.equals(player.getUserId(), userId)) {
                return player;
            }
        }
        return null;
    }

    private String extractToken(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }

        String[] parts = uri.getQuery().split("&");
        for (String part : parts) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("token")) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @PreDestroy
    public void shutdown() {
        for (ScheduledFuture<?> task : reconnectTasks.values()) {
            task.cancel(false);
        }
        reconnectTasks.clear();
        scheduler.shutdownNow();
    }

    private static class SessionUser {
        private final Integer userId;
        private final String username;
        private String roomCode;

        private SessionUser(Integer userId, String username) {
            this.userId = userId;
            this.username = username;
        }
    }
}
