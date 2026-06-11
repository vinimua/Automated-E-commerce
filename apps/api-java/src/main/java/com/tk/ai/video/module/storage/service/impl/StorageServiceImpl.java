package com.tk.ai.video.module.storage.service.impl;

import com.tk.ai.video.common.BusinessException;
import com.tk.ai.video.config.CosConfig;
import com.tk.ai.video.module.storage.dto.PresignedUploadRequest;
import com.tk.ai.video.module.storage.dto.PresignedUploadResponse;
import com.tk.ai.video.module.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private static final Set<String> ALLOWED_FOLDERS = Set.of(
            "product-images", "ai-images", "ai-clips", "final-videos", "covers"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "video/mp4", "video/quicktime",
            "application/zip"
    );

    private final CosConfig cosConfig;

    @Override
    public PresignedUploadResponse generatePresignedUploadUrl(PresignedUploadRequest request, UUID userId) {
        // 1. Validate folder
        if (!ALLOWED_FOLDERS.contains(request.getFolder())) {
            throw new BusinessException("Invalid folder: " + request.getFolder());
        }

        // 2. Validate file size
        long maxBytes = (long) cosConfig.getMaxFileSizeMb() * 1024 * 1024;
        if (request.getSizeBytes() > maxBytes) {
            throw new BusinessException(
                    String.format("File too large: %d bytes (max: %d bytes)", request.getSizeBytes(), maxBytes)
            );
        }

        // 3. Validate MIME type
        if (!ALLOWED_MIME_TYPES.contains(request.getMimeType())) {
            throw new BusinessException("Invalid mime type: " + request.getMimeType());
        }

        // 4. Generate server-side object key
        String sanitizedName = request.getFileName().replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String objectKey = String.format("%s%s/%s/%s/%s",
                cosConfig.getAllowedFolderPrefix(),
                request.getFolder(),
                userId,
                UUID.randomUUID(),
                sanitizedName
        );

        // 5. Build presigned PUT URL
        // Phase 5: integrate Tencent COS SDK for real presigned URLs
        String uploadUrl = String.format(
                "https://%s.cos.%s.myqcloud.com/%s?presigned", // placeholder
                cosConfig.getBucket(), cosConfig.getRegion(), objectKey
        );

        // 6. Build file URL (CDN or direct COS)
        String fileUrl;
        if (cosConfig.getCdnDomain() != null && !cosConfig.getCdnDomain().isBlank()) {
            fileUrl = "https://" + cosConfig.getCdnDomain() + "/" + objectKey;
        } else {
            fileUrl = String.format("https://%s.cos.%s.myqcloud.com/%s",
                    cosConfig.getBucket(), cosConfig.getRegion(), objectKey);
        }

        log.info("Generated presigned upload: folder={}, key={}", request.getFolder(), objectKey);

        return new PresignedUploadResponse(uploadUrl, fileUrl);
    }
}
