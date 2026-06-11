package com.tk.ai.video.module.storyboard.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.storyboard.dto.*;
import com.tk.ai.video.module.storyboard.service.StoryboardService;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StoryboardController {

    private final StoryboardService storyboardService;

    @GetMapping("/api/video-tasks/{taskId}/plans")
    public ApiResponse<VideoPlanListResponse> getPlans(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<VideoPlanResponse> plans = storyboardService.getPlans(taskId, principal.getUserId());
        return ApiResponse.ok(new VideoPlanListResponse(taskId, plans));
    }

    @GetMapping("/api/video-tasks/{taskId}/storyboard")
    public ApiResponse<StoryboardResponse> getStoryboard(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(storyboardService.getStoryboard(taskId, principal.getUserId()));
    }

    @PatchMapping("/api/storyboards/{storyboardId}")
    public ApiResponse<StoryboardResponse> updateStoryboard(
            @PathVariable UUID storyboardId,
            @Valid @RequestBody UpdateStoryboardRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(storyboardService.updateStoryboard(storyboardId, request, principal.getUserId()));
    }
}
