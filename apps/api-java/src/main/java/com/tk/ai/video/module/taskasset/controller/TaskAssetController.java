package com.tk.ai.video.module.taskasset.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.taskasset.dto.*;
import com.tk.ai.video.module.taskasset.service.TaskAssetService;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TaskAssetController {

    private final TaskAssetService taskAssetService;

    @GetMapping("/api/video-tasks/{taskId}/assets")
    public ApiResponse<TaskAssetListResponse> getAssets(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new TaskAssetListResponse(taskId,
                taskAssetService.getAssets(taskId, principal.getUserId())));
    }

    @PostMapping("/api/video-tasks/{taskId}/assets")
    public ApiResponse<TaskAssetListResponse> addAsset(
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateAssetRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new TaskAssetListResponse(taskId,
                taskAssetService.addAsset(taskId, request, principal.getUserId())));
    }

    @PatchMapping("/api/video-tasks/{taskId}/assets/{assetId}/role")
    public ApiResponse<TaskAssetListResponse> updateAssetRole(
            @PathVariable UUID taskId,
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateAssetRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new TaskAssetListResponse(taskId,
                taskAssetService.updateAssetRole(taskId, assetId, request, principal.getUserId())));
    }

    @DeleteMapping("/api/video-tasks/{taskId}/assets/{assetId}")
    public ApiResponse<TaskAssetListResponse> deleteAsset(
            @PathVariable UUID taskId,
            @PathVariable UUID assetId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        taskAssetService.deleteAsset(taskId, assetId, principal.getUserId());
        return ApiResponse.ok(new TaskAssetListResponse(taskId,
                taskAssetService.getAssets(taskId, principal.getUserId())));
    }

    @PostMapping("/api/video-tasks/{taskId}/assets/generate-image")
    public ApiResponse<TaskAssetListResponse> generateAssetImage(
            @PathVariable UUID taskId,
            @Valid @RequestBody GenerateAssetImageRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new TaskAssetListResponse(taskId,
                taskAssetService.generateAssetImage(taskId, request, principal.getUserId())));
    }

    @PostMapping("/api/video-tasks/{taskId}/assets/{assetId}/regenerate-image")
    public ApiResponse<TaskAssetListResponse> regenerateAssetImage(
            @PathVariable UUID taskId,
            @PathVariable UUID assetId,
            @Valid @RequestBody RegenerateAssetImageRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(new TaskAssetListResponse(taskId,
                taskAssetService.regenerateAssetImage(taskId, assetId, request, principal.getUserId())));
    }

    @PostMapping("/api/video-tasks/{taskId}/assets/confirm")
    public ApiResponse<VideoTaskStatusResponse> confirmAssets(
            @PathVariable UUID taskId,
            @RequestBody ConfirmAssetsRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(taskAssetService.confirmAssets(taskId, request, principal.getUserId()));
    }
}
