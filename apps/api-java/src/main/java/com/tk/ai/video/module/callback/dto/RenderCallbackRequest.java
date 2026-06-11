package com.tk.ai.video.module.callback.dto;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class RenderCallbackRequest {
    private UUID taskId;
    private UUID videoId;
    private String renderTaskId;
    private String manifestVersion;
    private String status;
    private String videoUrl;
    private String coverUrl;
    private Integer duration;
    private String resolution;
    private Map<String, Object> renderLog;
    private Map<String, Object> error;
}
