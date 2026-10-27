package com.wuziqi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank
        @Size(min = 3, max = 20)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$")
        String username,

        @NotBlank
        @Size(min = 6, max = 64)
        String password
) {
}
