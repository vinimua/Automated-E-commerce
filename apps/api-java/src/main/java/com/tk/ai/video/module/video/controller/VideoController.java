package com.tk.ai.video.module.video.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.video.dto.*;
import com.tk.ai.video.module.video.service.VideoService;
import com.tk.ai.video.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @GetMapping
    public ApiResponse<PageResult<VideoResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoService.list(status, productId, page, pageSize, principal.getUserId()));
    }

    @GetMapping("/{videoId}")
    public ApiResponse<VideoResponse> getById(
            @PathVariable UUID videoId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoService.getById(videoId, principal.getUserId()));
    }

    @PostMapping("/{videoId}/export")
    public ApiResponse<VideoExportResponse> export(
            @PathVariable UUID videoId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(videoService.export(videoId, principal.getUserId()));
    }
}
