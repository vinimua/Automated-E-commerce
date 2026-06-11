package com.tk.ai.video.module.videotask.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.videotask.dto.*;
import com.tk.ai.video.module.videotask.service.VideoTaskService;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/video-tasks")
@RequiredArgsConstructor
public class VideoTaskController {

    private final VideoTaskService videoTaskService;

    @PostMapping
    public ApiResponse<CreateVideoTaskResponse> create(
            @Valid @RequestBody CreateVideoTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.create(request, principal.getUserId()));
    }

    @GetMapping
    public ApiResponse<PageResult<VideoTaskResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.list(status, productId, page, pageSize, principal.getUserId()));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<VideoTaskResponse> getById(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.getById(taskId, principal.getUserId()));
    }

    @PostMapping("/{taskId}/select-plan")
    public ApiResponse<VideoTaskStatusResponse> selectPlan(
            @PathVariable UUID taskId,
            @Valid @RequestBody SelectPlanRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.selectPlan(taskId, request, principal.getUserId()));
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<VideoTaskStatusResponse> retry(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.retry(taskId, principal.getUserId()));
    }
}
