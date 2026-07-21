package com.tk.ai.video.module.storage.service.impl;

import com.tk.ai.video.common.BusinessException;
import com.tk.ai.video.config.CosConfig;
import com.tk.ai.video.module.storage.dto.PresignedUploadRequest;
import com.tk.ai.video.module.storage.dto.PresignedUploadResponse;
import com.tk.ai.video.module.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private static final Set<String> ALLOWED_FOLDERS = Set.of(
            "product-images", "ai-images", "ai-clips", "final-videos", "covers",
            "reference-videos"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "video/mp4", "video/quicktime",
            "application/zip"
    );

    private static final long MAX_UPLOAD_BYTES = 500L * 1024 * 1024; // 500 MB

    private final CosConfig cosConfig;

    @Value("${app.upload.base-dir:uploads}")
    private String uploadBaseDir;

    @Value("${app.upload.base-url:http://localhost:8099/uploads}")
    private String uploadBaseUrl;

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

    @Override
    public PresignedUploadResponse uploadFile(MultipartFile file, String folder, UUID userId) {
        // 1. Validate folder
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new BusinessException("Invalid folder: " + folder);
        }

        // 2. Validate file size
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(
                    String.format("File too large: %d bytes (max: %d bytes)", file.getSize(), MAX_UPLOAD_BYTES)
            );
        }

        // 3. Validate MIME type
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException("Invalid mime type: " + mimeType);
        }

        // 4. Build local path: uploads/<folder>/<userId>/<uuid>/<filename>
        String originalName = file.getOriginalFilename();
        String sanitizedName = originalName != null
                ? originalName.replaceAll("[^a-zA-Z0-9.\\-_]", "_")
                : "file";
        String uniqueDir = UUID.randomUUID().toString();
        Path targetDir = Paths.get(uploadBaseDir, folder, userId.toString(), uniqueDir);
        Path targetFile = targetDir.resolve(sanitizedName);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetFile);
            log.info("File saved: {} ({} bytes)", targetFile, file.getSize());
        } catch (IOException e) {
            throw new BusinessException("Failed to save file: " + e.getMessage());
        }

        // 5. Build file URL
        String relativePath = String.format("%s/%s/%s/%s", folder, userId, uniqueDir, sanitizedName);
        String fileUrl = uploadBaseUrl + "/" + relativePath;

        return new PresignedUploadResponse(null, fileUrl);
    }

    @Override
    public PresignedUploadResponse proxyDownload(String externalUrl, UUID userId) {
        // Already local — return as-is
        if (externalUrl.contains("localhost") || externalUrl.contains("127.0.0.1")) {
            return new PresignedUploadResponse(null, externalUrl);
        }

        try {
            java.net.URL url = new java.net.URL(externalUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; TK-AI-Video/1.0)");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new BusinessException("Download failed: HTTP " + status + " from " + externalUrl);
            }

            String contentType = conn.getContentType();
            String ext = ".png";
            if (contentType != null) {
                if (contentType.contains("jpeg") || contentType.contains("jpg")) ext = ".jpg";
                else if (contentType.contains("webp")) ext = ".webp";
            }

            byte[] md5Bytes = java.security.MessageDigest.getInstance("MD5")
                    .digest(externalUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : md5Bytes) {
                sb.append(String.format("%02x", b));
            }
            String urlHash = sb.toString().substring(0, 12);
            String filename = urlHash + ext;
            Path targetDir = Paths.get(uploadBaseDir, "proxied");
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(filename);

            byte[] content = conn.getInputStream().readAllBytes();
            Files.write(targetPath, content);

            String fileUrl = uploadBaseUrl + "/proxied/" + filename;
            log.info("Proxied: {} -> {} ({} bytes)", externalUrl.substring(0, Math.min(80, externalUrl.length())), fileUrl, content.length);
            return new PresignedUploadResponse(null, fileUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Download failed: " + e.getMessage());
        }
    }
}
