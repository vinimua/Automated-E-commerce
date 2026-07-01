package com.tk.ai.video.module.videoclip.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class VideoClipResponse {
    private UUID clipId;
    private UUID taskId;
    private UUID shotId;
    private UUID keyframeId;
    private int shotNo;
    private String source;
    private String url;
    private String prompt;
    private String provider;
    private String modelName;
    private String status;
    private int duration;
    private int version;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
