package com.wuziqi.backend.service;

import com.wuziqi.backend.dto.LeaderboardItemDto;
import com.wuziqi.backend.dto.ProfileDto;
import com.wuziqi.backend.entity.RatingEntity;
import com.wuziqi.backend.entity.UserEntity;
import com.wuziqi.backend.exception.ApiException;
import com.wuziqi.backend.repository.RatingRepository;
import com.wuziqi.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProfileService {
    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;

    public ProfileService(UserRepository userRepository, RatingRepository ratingRepository) {
        this.userRepository = userRepository;
        this.ratingRepository = ratingRepository;
    }

    public ProfileDto myProfile(Integer userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "User not found"));
        RatingEntity rating = ratingRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "Rating not found"));

        return new ProfileDto(
                user.getId(),
                user.getUsername(),
                rating.getElo(),
                rating.getWinCount(),
                rating.getLoseCount(),
                rating.getDrawCount()
        );
    }

    public List<LeaderboardItemDto> leaderboard(int limit) {
        List<RatingEntity> ratings = ratingRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "elo").and(Sort.by("updatedAt")))
        ).getContent();

        List<Integer> userIds = ratings.stream().map(RatingEntity::getUserId).toList();
        Map<Integer, String> usernames = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));

        List<LeaderboardItemDto> result = new ArrayList<>();
        for (int i = 0; i < ratings.size(); i++) {
            RatingEntity rating = ratings.get(i);
            result.add(new LeaderboardItemDto(
                    i + 1,
                    rating.getUserId(),
                    usernames.getOrDefault(rating.getUserId(), "unknown"),
                    rating.getElo(),
                    rating.getWinCount(),
                    rating.getLoseCount(),
                    rating.getDrawCount()
            ));
        }

        return result;
    }
}
