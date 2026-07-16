package com.tk.ai.video.module.callback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class RenderCallbackRequest {
    @NotNull
    private UUID taskId;
    private UUID videoId;
    private String renderTaskId;
    private String manifestVersion;
    @NotBlank
    @Pattern(regexp = "completed|failed")
    private String status;
    private String videoUrl;
    private String coverUrl;
    private Integer duration;
    private String resolution;
    private Map<String, Object> renderLog;
    private Map<String, Object> error;
    private String errorCode;
    private String errorMessage;
}
