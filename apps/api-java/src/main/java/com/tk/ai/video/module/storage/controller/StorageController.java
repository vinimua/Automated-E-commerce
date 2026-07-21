package com.tk.ai.video.module.storage.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.storage.dto.PresignedUploadRequest;
import com.tk.ai.video.module.storage.dto.PresignedUploadResponse;
import com.tk.ai.video.module.storage.service.StorageService;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/upload")
    public ApiResponse<PresignedUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(storageService.uploadFile(file, folder, principal.getUserId()));
    }

    @PostMapping("/proxy-download")
    public ApiResponse<PresignedUploadResponse> proxyDownload(
            @RequestBody java.util.Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String url = request.get("url");
        if (url == null || url.isBlank()) {
            throw new com.tk.ai.video.common.BusinessException("url is required");
        }
        return ApiResponse.ok(storageService.proxyDownload(url, principal.getUserId()));
    }
}
