package com.tk.ai.video.module.videoclip.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.videoclip.dto.*;
import com.tk.ai.video.module.videoclip.service.VideoClipService;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VideoClipController {

    private final VideoClipService videoClipService;

    @GetMapping("/api/video-tasks/{taskId}/video-clips")
    public ApiResponse<VideoClipListResponse> getClips(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new VideoClipListResponse(taskId,
                videoClipService.getClips(taskId, principal.getUserId())));
    }

    @PostMapping("/api/video-tasks/{taskId}/video-clips/{clipId}/confirm")
    public ApiResponse<VideoTaskStatusResponse> confirmClip(
            @PathVariable UUID taskId,
            @PathVariable UUID clipId,
            @Valid @RequestBody ConfirmVideoClipRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoClipService.confirmClip(taskId, clipId, request, principal.getUserId()));
    }

    @PostMapping("/api/video-tasks/{taskId}/video-clips/generate")
    public ApiResponse<VideoTaskStatusResponse> generateClips(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoClipService.generateClips(taskId, principal.getUserId()));
    }

    @PostMapping("/api/video-tasks/{taskId}/video-clips/{clipId}/regenerate")
    public ApiResponse<VideoTaskStatusResponse> regenerateClip(
            @PathVariable UUID taskId,
            @PathVariable UUID clipId,
            @RequestBody(required = false) ConfirmVideoClipRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String prompt = request != null ? request.getFeedback() : null;
        return ApiResponse.ok(videoClipService.regenerateClip(taskId, clipId, prompt, principal.getUserId()));
    }

    @PostMapping("/api/video-tasks/{taskId}/video-clips/{clipId}/reject")
    public ApiResponse<VideoTaskStatusResponse> rejectClip(
            @PathVariable UUID taskId,
            @PathVariable UUID clipId,
            @Valid @RequestBody ConfirmVideoClipRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoClipService.rejectClip(taskId, clipId, request, principal.getUserId()));
    }

    @PostMapping("/api/video-tasks/{taskId}/video-clips/{clipId}/unconfirm")
    public ApiResponse<VideoTaskStatusResponse> unconfirmClip(
            @PathVariable UUID taskId,
            @PathVariable UUID clipId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoClipService.unconfirmClip(taskId, clipId, principal.getUserId()));
    }
}
