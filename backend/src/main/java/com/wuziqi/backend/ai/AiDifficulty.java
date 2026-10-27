package com.wuziqi.backend.ai;

import java.util.Locale;

public enum AiDifficulty {
    EASY("easy", 1, 4, 6, 0.35d, 0.75d, 300),
    MEDIUM("medium", 2, 6, 3, 0.10d, 1.0d, 800),
    HARD("hard", 3, 8, 1, 0.0d, 1.0d, 1500);

    private final String code;
    private final int searchDepth;
    private final int branching;
    private final int randomPickTopN;
    private final double randomPickChance;
    private final double mustBlockChance;
    private final int defaultThinkMs;

    AiDifficulty(String code,
                 int searchDepth,
                 int branching,
                 int randomPickTopN,
                 double randomPickChance,
                 double mustBlockChance,
                 int defaultThinkMs) {
        this.code = code;
        this.searchDepth = searchDepth;
        this.branching = branching;
        this.randomPickTopN = randomPickTopN;
        this.randomPickChance = randomPickChance;
        this.mustBlockChance = mustBlockChance;
        this.defaultThinkMs = defaultThinkMs;
    }

    public String code() {
        return code;
    }

    public int searchDepth() {
        return searchDepth;
    }

    public int branching() {
        return branching;
    }

    public int randomPickTopN() {
        return randomPickTopN;
    }

    public double randomPickChance() {
        return randomPickChance;
    }

    public double mustBlockChance() {
        return mustBlockChance;
    }

    public int defaultThinkMs() {
        return defaultThinkMs;
    }

    public static AiDifficulty from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (AiDifficulty difficulty : values()) {
            if (difficulty.code.equals(value)) {
                return difficulty;
            }
        }
        return MEDIUM;
    }
}
