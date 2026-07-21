package com.tk.ai.video.module.storage.service;

import com.tk.ai.video.module.storage.dto.PresignedUploadRequest;
import com.tk.ai.video.module.storage.dto.PresignedUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface StorageService {

    PresignedUploadResponse generatePresignedUploadUrl(PresignedUploadRequest request, UUID userId);

    PresignedUploadResponse uploadFile(MultipartFile file, String folder, UUID userId);

    PresignedUploadResponse proxyDownload(String externalUrl, UUID userId);
}
