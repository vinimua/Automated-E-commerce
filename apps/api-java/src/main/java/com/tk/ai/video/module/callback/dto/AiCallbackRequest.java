package com.tk.ai.video.module.callback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class AiCallbackRequest {
    @NotNull
    private UUID taskId;
    @NotBlank
    @Pattern(regexp = "1\\.0\\.0")
    private String schemaVersion;
    @NotBlank
    @Pattern(regexp = "asset_analysis|reference_analysis|creative_plan|product_analysis|video_plan|storyboard|material|quality_check|render_manifest|keyframe|video_clip|qa|repair")
    private String stage;
    @NotBlank
    @Pattern(regexp = "success|failed")
    private String status;
    private String nextTaskStatus;
    private Map<String, Object> productAnalysis;
    private List<Map<String, Object>> plans;
    private Map<String, Object> storyboard;
    private List<Map<String, Object>> materials;
    private Map<String, Object> renderManifest;
    private Map<String, Object> qualityCheck;

    // Fashion Creative Loop V1 payload fields
    private Map<String, Object> fashionAssetAnalysis;
    private Map<String, Object> referenceAnalysis;
    private List<Map<String, Object>> keyframes;
    private List<Map<String, Object>> clips;
    private Map<String, Object> qaResult;
    private Map<String, Object> repairResult;

    private Map<String, Object> error;
}
