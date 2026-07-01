package com.tk.ai.video.module.creativestate.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.creativestate.dto.CreativeStateResponse;
import com.tk.ai.video.module.creativestate.dto.UpdateCreativeStateRequest;
import com.tk.ai.video.module.creativestate.service.CreativeStateService;
import com.tk.ai.video.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CreativeStateController {

    private final CreativeStateService creativeStateService;

    @GetMapping("/api/video-tasks/{taskId}/creative-state")
    public ApiResponse<CreativeStateResponse> getCreativeState(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(creativeStateService.getCreativeState(taskId, principal.getUserId()));
    }

    @PatchMapping("/api/video-tasks/{taskId}/creative-state")
    public ApiResponse<CreativeStateResponse> updateCreativeState(
            @PathVariable UUID taskId,
            @RequestBody UpdateCreativeStateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(creativeStateService.updateCreativeState(taskId, request, principal.getUserId()));
    }
}
