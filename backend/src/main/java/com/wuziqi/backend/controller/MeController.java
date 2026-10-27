package com.wuziqi.backend.controller;

import com.wuziqi.backend.dto.ProfileDto;
import com.wuziqi.backend.exception.ApiException;
import com.wuziqi.backend.service.JwtService;
import com.wuziqi.backend.service.ProfileService;
import com.wuziqi.backend.service.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class MeController {
    private final JwtService jwtService;
    private final ProfileService profileService;

    public MeController(JwtService jwtService, ProfileService profileService) {
        this.jwtService = jwtService;
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public ProfileDto profile(@RequestHeader(name = "Authorization", required = false) String authorization) {
        UserPrincipal user = parseBearer(authorization);
        return profileService.myProfile(user.userId());
    }

    private UserPrincipal parseBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "Missing token");
        }
        return jwtService.verifyAndGetUser(authorization.substring("Bearer ".length()));
    }
}
