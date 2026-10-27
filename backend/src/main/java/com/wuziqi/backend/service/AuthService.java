package com.wuziqi.backend.service;

import com.wuziqi.backend.dto.AuthRequest;
import com.wuziqi.backend.dto.AuthResponse;
import com.wuziqi.backend.dto.UserDto;
import com.wuziqi.backend.entity.RatingEntity;
import com.wuziqi.backend.entity.UserEntity;
import com.wuziqi.backend.exception.ApiException;
import com.wuziqi.backend.repository.RatingRepository;
import com.wuziqi.backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository,
                       RatingRepository ratingRepository,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.ratingRepository = ratingRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(409, "Username already exists");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        RatingEntity rating = new RatingEntity();
        rating.setUserId(user.getId());
        ratingRepository.save(rating);

        String token = jwtService.createToken(user.getId(), user.getUsername());
        return new AuthResponse(token, new UserDto(user.getId(), user.getUsername()));
    }

    public AuthResponse login(AuthRequest request) {
        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ApiException(401, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(401, "Invalid credentials");
        }

        String token = jwtService.createToken(user.getId(), user.getUsername());
        return new AuthResponse(token, new UserDto(user.getId(), user.getUsername()));
    }
}
