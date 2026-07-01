package com.tk.ai.video.module.keyframe.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class KeyframeResponse {
    private UUID keyframeId;
    private UUID taskId;
    private UUID shotId;
    private int shotNo;
    private String source;
    private UUID assetId;
    private String imagePurpose;
    private String url;
    private String prompt;
    private String userInstruction;
    private String provider;
    private String modelName;
    private String status;
    private int version;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
