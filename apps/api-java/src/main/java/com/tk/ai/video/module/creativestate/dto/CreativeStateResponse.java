package com.tk.ai.video.module.creativestate.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class CreativeStateResponse {
    private UUID creativeStateId;
    private UUID taskId;
    private Map<String, Object> product;
    private Map<String, Object> model;
    private Map<String, Object> scene;
    private Map<String, Object> outfit;
    private Map<String, Object> referenceVideo;
    private Map<String, Object> constraints;
    private Map<String, Object> userRequirements;
    private int version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
