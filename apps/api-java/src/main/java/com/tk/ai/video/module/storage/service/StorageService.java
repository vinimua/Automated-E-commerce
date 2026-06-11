package com.tk.ai.video.module.storage.service;

import com.tk.ai.video.module.storage.dto.PresignedUploadRequest;
import com.tk.ai.video.module.storage.dto.PresignedUploadResponse;

import java.util.UUID;

public interface StorageService {

    PresignedUploadResponse generatePresignedUploadUrl(PresignedUploadRequest request, UUID userId);
}
