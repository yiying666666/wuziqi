package com.wuziqi.backend.controller;

import com.wuziqi.backend.dto.AiMoveRequest;
import com.wuziqi.backend.dto.AiMoveResponse;
import com.wuziqi.backend.exception.ApiException;
import com.wuziqi.backend.service.AiEngineService;
import com.wuziqi.backend.service.JwtService;
import com.wuziqi.backend.service.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {
    private final AiEngineService aiEngineService;
    private final JwtService jwtService;

    public AiController(AiEngineService aiEngineService, JwtService jwtService) {
        this.aiEngineService = aiEngineService;
        this.jwtService = jwtService;
    }

    @PostMapping("/move")
    public AiMoveResponse nextMove(@RequestBody @Valid AiMoveRequest request,
                                   @RequestHeader(name = "Authorization", required = false) String authorization) {
        parseBearer(authorization);
        return aiEngineService.nextMove(request);
    }

    private UserPrincipal parseBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "Missing token");
        }
        return jwtService.verifyAndGetUser(authorization.substring("Bearer ".length()));
    }
}
