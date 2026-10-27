package com.wuziqi.backend;

import com.wuziqi.backend.service.EloService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EloServiceTest {
    private final EloService eloService = new EloService();

    @Test
    void winnerShouldGainRating() {
        EloService.EloResult result = eloService.calculate(1200, 1200, 1, 32);
        Assertions.assertTrue(result.nextA() > 1200);
        Assertions.assertTrue(result.nextB() < 1200);
    }

    @Test
    void drawEqualRatingShouldStay() {
        EloService.EloResult result = eloService.calculate(1400, 1400, 0.5, 32);
        Assertions.assertEquals(1400, result.nextA());
        Assertions.assertEquals(1400, result.nextB());
    }
}
