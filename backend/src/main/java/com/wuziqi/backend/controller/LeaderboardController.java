package com.wuziqi.backend.controller;

import com.wuziqi.backend.dto.LeaderboardItemDto;
import com.wuziqi.backend.service.ProfileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/leaderboard")
@Validated
public class LeaderboardController {
    private final ProfileService profileService;

    public LeaderboardController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public List<LeaderboardItemDto> leaderboard(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return profileService.leaderboard(limit);
    }
}
