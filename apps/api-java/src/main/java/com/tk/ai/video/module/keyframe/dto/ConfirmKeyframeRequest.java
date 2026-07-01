package com.tk.ai.video.module.keyframe.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmKeyframeRequest {

    @NotNull
    private Boolean confirmed;

    private String feedback;
}
