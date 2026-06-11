package com.tk.ai.video.module.video.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class VideoResponse {
    private UUID videoId;
    private UUID taskId;
    private UUID productId;
    private String title;
    private String videoUrl;
    private String coverUrl;
    private int duration;
    private String resolution;
    private Integer qualityScore;
    private Integer riskScore;
    private String caption;
    private List<String> hashtags;
    private String status;
}
