package com.tk.ai.video.module.callback.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class AiCallbackRequest {
    private UUID taskId;
    private String schemaVersion;
    private String stage;
    private String status;
    private String nextTaskStatus;
    private Map<String, Object> productAnalysis;
    private List<Map<String, Object>> plans;
    private Map<String, Object> storyboard;
    private List<Map<String, Object>> materials;
    private Map<String, Object> renderManifest;
    private Map<String, Object> qualityCheck;
    private Map<String, Object> error;
}
