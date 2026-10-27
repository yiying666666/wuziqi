package com.wuziqi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String corsOrigin,
        String jwtSecret,
        int jwtExpireDays,
        int reconnectGraceSeconds,
        boolean redisEnabled
) {
}
