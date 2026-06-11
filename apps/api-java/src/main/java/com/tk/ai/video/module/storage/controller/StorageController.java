package com.tk.ai.video.module.storage.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.storage.dto.PresignedUploadRequest;
import com.tk.ai.video.module.storage.dto.PresignedUploadResponse;
import com.tk.ai.video.module.storage.service.StorageService;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/presigned-upload-url")
    public ApiResponse<PresignedUploadResponse> getPresignedUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(storageService.generatePresignedUploadUrl(request, principal.getUserId()));
    }
}
