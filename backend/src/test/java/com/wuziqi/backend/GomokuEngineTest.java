package com.wuziqi.backend;

import com.wuziqi.backend.game.GomokuEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GomokuEngineTest {
    @Test
    void horizontalFiveShouldWin() {
        int[][] board = GomokuEngine.createBoard();
        for (int x = 2; x <= 6; x++) {
            board[7][x] = 1;
        }

        Assertions.assertTrue(GomokuEngine.checkWin(board, 6, 7, 1));
    }

    @Test
    void fourShouldNotWin() {
        int[][] board = GomokuEngine.createBoard();
        for (int x = 2; x <= 5; x++) {
            board[7][x] = 2;
        }

        Assertions.assertFalse(GomokuEngine.checkWin(board, 5, 7, 2));
    }
}
