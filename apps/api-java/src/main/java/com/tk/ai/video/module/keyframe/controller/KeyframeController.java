package com.tk.ai.video.module.keyframe.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.keyframe.dto.*;
import com.tk.ai.video.module.keyframe.service.KeyframeService;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class KeyframeController {

    private final KeyframeService keyframeService;

    @GetMapping("/api/video-tasks/{taskId}/keyframes")
    public ApiResponse<KeyframeListResponse> getKeyframes(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new KeyframeListResponse(taskId,
                keyframeService.getKeyframes(taskId, principal.getUserId())));
    }

    @PostMapping("/api/video-tasks/{taskId}/keyframes")
    public ApiResponse<KeyframeListResponse> addKeyframe(
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateKeyframeRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new KeyframeListResponse(taskId,
                keyframeService.addKeyframe(taskId, request, principal.getUserId())));
    }

    @PostMapping("/api/video-tasks/{taskId}/keyframes/{keyframeId}/confirm")
    public ApiResponse<VideoTaskStatusResponse> confirmKeyframe(
            @PathVariable UUID taskId,
            @PathVariable UUID keyframeId,
            @Valid @RequestBody ConfirmKeyframeRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(keyframeService.confirmKeyframe(taskId, keyframeId, request, principal.getUserId()));
    }

    @PostMapping("/api/video-tasks/{taskId}/keyframes/{keyframeId}/reject")
    public ApiResponse<VideoTaskStatusResponse> rejectKeyframe(
            @PathVariable UUID taskId,
            @PathVariable UUID keyframeId,
            @Valid @RequestBody ConfirmKeyframeRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(keyframeService.rejectKeyframe(taskId, keyframeId, request, principal.getUserId()));
    }

    @PostMapping("/api/video-tasks/{taskId}/keyframes/generate")
    public ApiResponse<KeyframeListResponse> generateKeyframes(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new KeyframeListResponse(taskId,
                keyframeService.generateKeyframes(taskId, principal.getUserId())));
    }

    @PostMapping("/api/video-tasks/{taskId}/keyframes/{keyframeId}/regenerate")
    public ApiResponse<KeyframeListResponse> regenerateKeyframe(
            @PathVariable UUID taskId,
            @PathVariable UUID keyframeId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new KeyframeListResponse(taskId,
                keyframeService.regenerateKeyframe(taskId, keyframeId, principal.getUserId())));
    }
}
