package com.wuziqi.backend.service;

import org.springframework.stereotype.Service;

@Service
public class EloService {
    public EloResult calculate(int playerA, int playerB, double scoreA, int kFactor) {
        double expectedA = 1d / (1d + Math.pow(10d, (playerB - playerA) / 400d));
        double expectedB = 1d - expectedA;
        double scoreB = 1d - scoreA;

        int nextA = (int) Math.round(playerA + kFactor * (scoreA - expectedA));
        int nextB = (int) Math.round(playerB + kFactor * (scoreB - expectedB));

        return new EloResult(nextA, nextB);
    }

    public record EloResult(int nextA, int nextB) {
    }
}
