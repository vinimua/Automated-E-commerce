package com.tk.ai.video.module.videotask.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.videotask.dto.CreateFashionTaskRequest;
import com.tk.ai.video.module.videotask.dto.FashionTaskCreateResponse;
import com.tk.ai.video.module.videotask.service.VideoTaskService;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregate task creation — creates product + task + assets + creative state atomically.
 * Separate from VideoTaskController to avoid the /api/video-tasks class-level prefix.
 */
@RestController
@RequiredArgsConstructor
public class FashionTaskController {

    private final VideoTaskService videoTaskService;

    @PostMapping("/api/fashion-video-tasks")
    public ApiResponse<FashionTaskCreateResponse> createFashionTask(
            @Valid @RequestBody CreateFashionTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.createFashionTask(request, principal.getUserId()));
    }
}
