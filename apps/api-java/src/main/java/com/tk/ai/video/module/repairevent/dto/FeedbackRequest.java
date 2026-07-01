package com.tk.ai.video.module.repairevent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackRequest {

    @NotBlank
    private String feedbackText;

    private String category;

    private String targetType;

    private String targetId;
}
