package com.tk.ai.video.module.user.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.user.dto.UserInfoResponse;
import com.tk.ai.video.module.user.service.UserService;
import com.tk.ai.video.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/users/me")
    public ApiResponse<UserInfoResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(userService.getCurrentUser(principal.getUserId()));
    }
}
