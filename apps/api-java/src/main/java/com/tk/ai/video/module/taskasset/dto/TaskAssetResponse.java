package com.tk.ai.video.module.taskasset.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TaskAssetResponse {
    private UUID assetId;
    private UUID taskId;
    private UUID productId;
    private String assetKind;
    private String assetRole;
    private String source;
    private String url;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private String description;
    private Boolean confirmed;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
