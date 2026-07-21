package com.tk.ai.video.module.videotask.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.repairevent.dto.FeedbackRequest;
import com.tk.ai.video.module.repairevent.dto.RepairEventListResponse;
import com.tk.ai.video.module.repairevent.dto.RepairEventResponse;
import com.tk.ai.video.module.videotask.dto.*;
import com.tk.ai.video.module.videotask.service.VideoTaskService;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    // ── Fashion Creative Loop V1 endpoints ──

    @PostMapping("/{taskId}/confirm-plan")
    public ApiResponse<VideoTaskStatusResponse> confirmPlan(
            @PathVariable UUID taskId,
            @Valid @RequestBody ConfirmPlanRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.confirmPlan(taskId, request, principal.getUserId()));
    }

    @PostMapping("/{taskId}/confirm-storyboard")
    public ApiResponse<VideoTaskStatusResponse> confirmStoryboard(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.confirmStoryboard(taskId, principal.getUserId()));
    }

    @PostMapping("/{taskId}/regenerate-storyboard")
    public ApiResponse<VideoTaskStatusResponse> regenerateStoryboard(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.regenerateStoryboard(taskId, principal.getUserId()));
    }

    @PostMapping("/{taskId}/render")
    public ApiResponse<VideoTaskStatusResponse> requestRender(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.requestRender(taskId, principal.getUserId()));
    }

    @PostMapping("/{taskId}/approve")
    public ApiResponse<VideoTaskStatusResponse> approveFinalReview(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.approveFinalReview(taskId, principal.getUserId()));
    }

    @PostMapping("/{taskId}/feedback")
    public ApiResponse<RepairEventListResponse> submitFeedback(
            @PathVariable UUID taskId,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        videoTaskService.submitFeedback(taskId, request, principal.getUserId());
        List<RepairEventResponse> events = videoTaskService.getRepairEvents(taskId, principal.getUserId());
        return ApiResponse.ok(new RepairEventListResponse(taskId, events));
    }

    @GetMapping("/{taskId}/repair-events")
    public ApiResponse<RepairEventListResponse> getRepairEvents(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<RepairEventResponse> events = videoTaskService.getRepairEvents(taskId, principal.getUserId());
        return ApiResponse.ok(new RepairEventListResponse(taskId, events));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<VideoTaskStatusResponse> cancel(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoTaskService.cancelTask(taskId, principal.getUserId()));
    }

}
