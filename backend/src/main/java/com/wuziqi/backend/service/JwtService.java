package com.wuziqi.backend.service;

import com.wuziqi.backend.config.AppProperties;
import com.wuziqi.backend.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private final SecretKey secretKey;
    private final AppProperties appProperties;

    public JwtService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.secretKey = Keys.hmacShaKeyFor(appProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(Integer userId, String username) {
        Instant now = Instant.now();
        Instant expireAt = now.plus(appProperties.jwtExpireDays(), ChronoUnit.DAYS);

        return Jwts.builder()
                .claims(Map.of(
                        "userId", userId,
                        "username", username
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public UserPrincipal verifyAndGetUser(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number userId = claims.get("userId", Number.class);
            String username = claims.get("username", String.class);
            if (userId == null || username == null) {
                throw new ApiException(401, "Invalid token");
            }
            return new UserPrincipal(userId.intValue(), username);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(401, "Invalid token");
        }
    }
}
