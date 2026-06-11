package com.tk.ai.video.module.storyboard.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class VideoPlanResponse {
    private UUID planId;
    private String type;
    private String title;
    private String hook;
    private String structure;
    private String reason;
    private Integer estimatedDuration;
    private Integer score;
}
