package com.athlefit.backend.controller;

import com.athlefit.backend.dto.request.UpdateUserRequest;
import com.athlefit.backend.dto.response.UserResponse;
import com.athlefit.backend.security.AuthenticatedUser;
import com.athlefit.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.findCurrent(AuthenticatedUser.from(jwt));
    }

    @PatchMapping
    public UserResponse updateCurrentUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.update(AuthenticatedUser.from(jwt), request);
    }
}
