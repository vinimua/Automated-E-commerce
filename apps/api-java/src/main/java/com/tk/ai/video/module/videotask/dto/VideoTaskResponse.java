package com.tk.ai.video.module.videotask.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class VideoTaskResponse {
    private UUID taskId;
    private UUID productId;
    private String status;
    private int progress;
    private int duration;
    private String videoType;
    private Boolean needSubtitles;
    private Boolean needVoiceover;
    private UUID selectedPlanId;
    private String failedStage;
    private String errorCode;
    private String errorMessage;
    private Boolean errorRetryable;
    private int retryCount;
    private String manifestVersion;
    private String schemaVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Fashion Creative Loop V1 fields
    private String taskMode;
    private String productCategory;
    private Integer shotCount;
    private int currentVersion;
}
