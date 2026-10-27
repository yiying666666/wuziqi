package com.wuziqi.backend.service;

import com.wuziqi.backend.ai.AiDifficulty;
import com.wuziqi.backend.dto.AiMoveRequest;
import com.wuziqi.backend.dto.AiMoveResponse;
import com.wuziqi.backend.exception.ApiException;
import com.wuziqi.backend.game.GomokuEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiEngineService {
    private static final int BOARD_SIZE = GomokuEngine.BOARD_SIZE;
    private static final int[][] DIRECTIONS = {
            {1, 0},
            {0, 1},
            {1, 1},
            {1, -1}
    };

    private final ExecutorService aiExecutor;

    public AiEngineService(@Qualifier("aiExecutor") ExecutorService aiExecutor) {
        this.aiExecutor = aiExecutor;
    }

    public AiMoveResponse nextMove(AiMoveRequest request) {
        ParsedRequest parsed = validateRequest(request);
        int[][] board = cloneBoard(request.board());
        AiDifficulty difficulty = AiDifficulty.from(request.difficulty());

        if (GomokuEngine.isFull(board)) {
            return new AiMoveResponse(
                    null,
                    null,
                    parsed.aiColor(),
                    difficulty.code(),
                    0,
                    0,
                    0L,
                    "board_full"
            );
        }

        int thinkMs = resolveThinkMs(request.maxThinkMs(), difficulty);
        long startedAt = System.nanoTime();

        MoveChoice choice = computeWithTimeout(board, parsed.aiColor(), parsed.humanColor(), difficulty, thinkMs);

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return new AiMoveResponse(
                choice.x(),
                choice.y(),
                parsed.aiColor(),
                difficulty.code(),
                choice.searchedDepth(),
                choice.candidateCount(),
                elapsedMs,
                choice.reason()
        );
    }

    private MoveChoice computeWithTimeout(int[][] board,
                                          int aiColor,
                                          int humanColor,
                                          AiDifficulty difficulty,
                                          int thinkMs) {
        CompletableFuture<MoveChoice> future = CompletableFuture.supplyAsync(
                () -> pickMove(board, aiColor, humanColor, difficulty),
                aiExecutor
        );

        try {
            return future.get(thinkMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return pickMediumMove(cloneBoard(board), aiColor, humanColor);
        } catch (ExecutionException ex) {
            throw new ApiException(500, "AI engine failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "AI engine interrupted");
        }
    }

    private MoveChoice pickMove(int[][] board, int aiColor, int humanColor, AiDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> pickEasyMove(board, aiColor, humanColor, difficulty);
            case MEDIUM -> pickMediumMove(board, aiColor, humanColor);
            case HARD -> pickHardMove(board, aiColor, humanColor, difficulty);
        };
    }

    private MoveChoice pickEasyMove(int[][] board, int aiColor, int humanColor, AiDifficulty difficulty) {
        MoveCandidate winNow = findImmediateWin(board, aiColor);
        if (winNow != null) {
            return new MoveChoice(winNow.x(), winNow.y(), 1, 1, "win_now");
        }

        MoveCandidate mustBlock = findImmediateWin(board, humanColor);
        if (mustBlock != null && ThreadLocalRandom.current().nextDouble() <= difficulty.mustBlockChance()) {
            return new MoveChoice(mustBlock.x(), mustBlock.y(), 1, 1, "block_urgent");
        }

        List<MoveCandidate> ranked = rankCandidateMoves(board, aiColor, humanColor);
        if (ranked.isEmpty()) {
            return new MoveChoice(null, null, 1, 0, "no_move");
        }

        int poolSize = Math.min(difficulty.randomPickTopN(), ranked.size());
        int index = ThreadLocalRandom.current().nextInt(poolSize);
        MoveCandidate pick = ranked.get(index);
        return new MoveChoice(pick.x(), pick.y(), 1, ranked.size(), "heuristic_easy");
    }

    private MoveChoice pickMediumMove(int[][] board, int aiColor, int humanColor) {
        MoveCandidate winNow = findImmediateWin(board, aiColor);
        if (winNow != null) {
            return new MoveChoice(winNow.x(), winNow.y(), 2, 1, "win_now");
        }

        MoveCandidate mustBlock = findImmediateWin(board, humanColor);
        if (mustBlock != null) {
            return new MoveChoice(mustBlock.x(), mustBlock.y(), 2, 1, "block_urgent");
        }

        List<MoveCandidate> ranked = rankCandidateMoves(board, aiColor, humanColor);
        if (ranked.isEmpty()) {
            return new MoveChoice(null, null, 2, 0, "no_move");
        }

        double topScore = ranked.get(0).score();
        List<MoveCandidate> bestPoints = ranked.stream()
                .filter(candidate -> Double.compare(candidate.score(), topScore) == 0)
                .toList();

        MoveCandidate pick = bestPoints.get(ThreadLocalRandom.current().nextInt(bestPoints.size()));
        return new MoveChoice(pick.x(), pick.y(), 2, ranked.size(), "heuristic_medium");
    }

    private MoveChoice pickHardMove(int[][] board,
                                    int aiColor,
                                    int humanColor,
                                    AiDifficulty difficulty) {
        MoveCandidate winNow = findImmediateWin(board, aiColor);
        if (winNow != null) {
            return new MoveChoice(winNow.x(), winNow.y(), difficulty.searchDepth(), 1, "win_now");
        }

        MoveCandidate mustBlock = findImmediateWin(board, humanColor);
        if (mustBlock != null) {
            return new MoveChoice(mustBlock.x(), mustBlock.y(), difficulty.searchDepth(), 1, "block_urgent");
        }

        List<MoveCandidate> rootCandidates = rankCandidateMoves(board, aiColor, humanColor)
                .stream()
                .limit(difficulty.branching() + 1L)
                .toList();

        if (rootCandidates.isEmpty()) {
            return new MoveChoice(null, null, difficulty.searchDepth(), 0, "no_move");
        }

        MoveCandidate bestMove = rootCandidates.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        AtomicInteger nodeCounter = new AtomicInteger(0);

        for (MoveCandidate candidate : rootCandidates) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            board[candidate.y()][candidate.x()] = aiColor;
            double score = GomokuEngine.checkWin(board, candidate.x(), candidate.y(), aiColor)
                    ? 1_000_000d
                    : -negamax(
                    board,
                    difficulty.searchDepth() - 1,
                    humanColor,
                    aiColor,
                    Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    difficulty.branching(),
                    nodeCounter
            );
            board[candidate.y()][candidate.x()] = 0;

            if (score > bestScore) {
                bestScore = score;
                bestMove = candidate;
            }
        }

        return new MoveChoice(
                bestMove.x(),
                bestMove.y(),
                difficulty.searchDepth(),
                rootCandidates.size(),
                "search_hard_nodes_" + nodeCounter.get()
        );
    }

    private double negamax(int[][] board,
                           int depth,
                           int currentColor,
                           int aiColor,
                           double alpha,
                           double beta,
                           int branching,
                           AtomicInteger nodeCounter) {
        if (Thread.currentThread().isInterrupted()) {
            return evaluateBoardState(board, aiColor, oppositeColor(aiColor));
        }

        int humanColor = oppositeColor(aiColor);
        if (depth <= 0 || GomokuEngine.isFull(board)) {
            nodeCounter.incrementAndGet();
            double score = evaluateBoardState(board, aiColor, humanColor);
            return currentColor == aiColor ? score : -score;
        }

        List<MoveCandidate> rankedMoves = rankCandidateMoves(board, currentColor, oppositeColor(currentColor))
                .stream()
                .limit(branching)
                .toList();

        if (rankedMoves.isEmpty()) {
            nodeCounter.incrementAndGet();
            double score = evaluateBoardState(board, aiColor, humanColor);
            return currentColor == aiColor ? score : -score;
        }

        double best = Double.NEGATIVE_INFINITY;
        double localAlpha = alpha;

        for (MoveCandidate move : rankedMoves) {
            board[move.y()][move.x()] = currentColor;

            double score = GomokuEngine.checkWin(board, move.x(), move.y(), currentColor)
                    ? 900_000d + depth * 1_000d
                    : -negamax(
                    board,
                    depth - 1,
                    oppositeColor(currentColor),
                    aiColor,
                    -beta,
                    -localAlpha,
                    branching,
                    nodeCounter
            );

            board[move.y()][move.x()] = 0;

            if (score > best) {
                best = score;
            }
            if (best > localAlpha) {
                localAlpha = best;
            }
            if (localAlpha >= beta) {
                break;
            }
        }

        return best;
    }

    private double evaluateBoardState(int[][] board, int aiColor, int humanColor) {
        List<MoveCandidate> aiRanked = rankCandidateMoves(board, aiColor, humanColor);
        List<MoveCandidate> humanRanked = rankCandidateMoves(board, humanColor, aiColor);

        double aiBest = aiRanked.isEmpty() ? 0d : aiRanked.get(0).score();
        double aiSecond = aiRanked.size() >= 2 ? aiRanked.get(1).score() : 0d;
        double humanBest = humanRanked.isEmpty() ? 0d : humanRanked.get(0).score();
        double humanSecond = humanRanked.size() >= 2 ? humanRanked.get(1).score() : 0d;

        return aiBest * 1.1d + aiSecond * 0.45d - (humanBest * 1.2d + humanSecond * 0.55d);
    }

    private List<MoveCandidate> rankCandidateMoves(int[][] board, int selfColor, int otherColor) {
        List<MoveCandidate> ranked = new ArrayList<>();
        for (MoveCandidate point : collectCandidates(board)) {
            board[point.y()][point.x()] = selfColor;
            double attack = evaluatePoint(board, point.x(), point.y(), selfColor);
            board[point.y()][point.x()] = otherColor;
            double defend = evaluatePoint(board, point.x(), point.y(), otherColor);
            board[point.y()][point.x()] = 0;

            ranked.add(new MoveCandidate(
                    point.x(),
                    point.y(),
                    attack * 1.15d + defend
            ));
        }

        ranked.sort(Comparator.comparingDouble(MoveCandidate::score).reversed());
        return ranked;
    }

    private MoveCandidate findImmediateWin(int[][] board, int color) {
        for (MoveCandidate point : collectCandidates(board)) {
            if (board[point.y()][point.x()] != 0) {
                continue;
            }
            board[point.y()][point.x()] = color;
            boolean won = GomokuEngine.checkWin(board, point.x(), point.y(), color);
            board[point.y()][point.x()] = 0;
            if (won) {
                return point;
            }
        }
        return null;
    }

    private List<MoveCandidate> collectCandidates(int[][] board) {
        Set<String> candidates = new java.util.HashSet<>();
        boolean hasStone = false;

        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] == 0) {
                    continue;
                }

                hasStone = true;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (!GomokuEngine.isInBounds(nx, ny) || board[ny][nx] != 0) {
                            continue;
                        }
                        candidates.add(nx + "," + ny);
                    }
                }
            }
        }

        if (!hasStone) {
            int center = BOARD_SIZE / 2;
            return List.of(new MoveCandidate(center, center, 0));
        }

        List<MoveCandidate> result = new ArrayList<>(candidates.size());
        for (String item : candidates) {
            String[] parts = item.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            result.add(new MoveCandidate(x, y, 0));
        }
        return result;
    }

    private double evaluatePoint(int[][] board, int x, int y, int color) {
        double score = 0;

        for (int[] direction : DIRECTIONS) {
            int dx = direction[0];
            int dy = direction[1];

            int left = countDirection(board, x, y, -dx, -dy, color);
            int right = countDirection(board, x, y, dx, dy, color);
            int total = 1 + left + right;
            int openEnds = boolToInt(isOpenEnd(board, x, y, -dx, -dy, left))
                    + boolToInt(isOpenEnd(board, x, y, dx, dy, right));

            if (total >= 5) {
                score += 120_000;
            } else if (total == 4 && openEnds == 2) {
                score += 28_000;
            } else if (total == 4 && openEnds == 1) {
                score += 9_000;
            } else if (total == 3 && openEnds == 2) {
                score += 2_600;
            } else if (total == 3 && openEnds == 1) {
                score += 900;
            } else if (total == 2 && openEnds == 2) {
                score += 350;
            } else if (total == 2 && openEnds == 1) {
                score += 110;
            } else if (total == 1 && openEnds == 2) {
                score += 22;
            }
        }

        int center = BOARD_SIZE / 2;
        int distance = Math.abs(x - center) + Math.abs(y - center);
        score += (BOARD_SIZE - distance) * 4d;

        return score;
    }

    private int countDirection(int[][] board, int x, int y, int dx, int dy, int color) {
        int count = 0;
        int nx = x + dx;
        int ny = y + dy;
        while (GomokuEngine.isInBounds(nx, ny) && board[ny][nx] == color) {
            count += 1;
            nx += dx;
            ny += dy;
        }
        return count;
    }

    private boolean isOpenEnd(int[][] board, int x, int y, int dx, int dy, int length) {
        int nx = x + dx * (length + 1);
        int ny = y + dy * (length + 1);
        return GomokuEngine.isInBounds(nx, ny) && board[ny][nx] == 0;
    }

    private int oppositeColor(int color) {
        return color == 1 ? 2 : 1;
    }

    private int boolToInt(boolean value) {
        return value ? 1 : 0;
    }

    private int[][] cloneBoard(int[][] board) {
        int[][] cloned = new int[BOARD_SIZE][BOARD_SIZE];
        for (int y = 0; y < BOARD_SIZE; y++) {
            System.arraycopy(board[y], 0, cloned[y], 0, BOARD_SIZE);
        }
        return cloned;
    }

    private ParsedRequest validateRequest(AiMoveRequest request) {
        if (request == null) {
            throw new ApiException(400, "Missing request body");
        }

        int[][] board = request.board();
        if (board == null || board.length != BOARD_SIZE) {
            throw new ApiException(400, "Invalid board size");
        }
        for (int y = 0; y < BOARD_SIZE; y++) {
            if (board[y] == null || board[y].length != BOARD_SIZE) {
                throw new ApiException(400, "Invalid board size");
            }
            for (int x = 0; x < BOARD_SIZE; x++) {
                int value = board[y][x];
                if (value != 0 && value != 1 && value != 2) {
                    throw new ApiException(400, "Invalid board cell value");
                }
            }
        }

        int aiColor = request.aiColor() == null ? 2 : request.aiColor();
        int humanColor = request.humanColor() == null ? (aiColor == 1 ? 2 : 1) : request.humanColor();

        if ((aiColor != 1 && aiColor != 2) || (humanColor != 1 && humanColor != 2) || aiColor == humanColor) {
            throw new ApiException(400, "Invalid player colors");
        }

        return new ParsedRequest(aiColor, humanColor);
    }

    private int resolveThinkMs(Integer requestedThinkMs, AiDifficulty difficulty) {
        int value = requestedThinkMs == null ? difficulty.defaultThinkMs() : requestedThinkMs;
        return Math.max(120, Math.min(value, 3000));
    }

    private record ParsedRequest(int aiColor, int humanColor) {
    }

    private record MoveCandidate(int x, int y, double score) {
    }

    private record MoveChoice(Integer x,
                              Integer y,
                              Integer searchedDepth,
                              Integer candidateCount,
                              String reason) {
    }
}
