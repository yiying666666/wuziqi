package com.wuziqi.backend.game;

public final class GomokuEngine {
    public static final int BOARD_SIZE = 15;

    private static final int[][] DIRECTIONS = {
            {1, 0},
            {0, 1},
            {1, 1},
            {1, -1}
    };

    private GomokuEngine() {
    }

    public static int[][] createBoard() {
        return new int[BOARD_SIZE][BOARD_SIZE];
    }

    public static boolean isInBounds(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    public static boolean isFull(int[][] board) {
        for (int[] row : board) {
            for (int cell : row) {
                if (cell == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean checkWin(int[][] board, int x, int y, int color) {
        for (int[] direction : DIRECTIONS) {
            int total = 1
                    + countDirection(board, x, y, color, direction[0], direction[1])
                    + countDirection(board, x, y, color, -direction[0], -direction[1]);
            if (total >= 5) {
                return true;
            }
        }
        return false;
    }

    private static int countDirection(int[][] board, int x, int y, int color, int dx, int dy) {
        int count = 0;
        int cx = x + dx;
        int cy = y + dy;

        while (isInBounds(cx, cy) && board[cy][cx] == color) {
            count++;
            cx += dx;
            cy += dy;
        }

        return count;
    }
}
