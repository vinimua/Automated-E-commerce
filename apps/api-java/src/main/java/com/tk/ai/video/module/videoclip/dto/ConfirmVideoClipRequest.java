package com.tk.ai.video.module.videoclip.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmVideoClipRequest {

    @NotNull
    private Boolean confirmed;

    private String feedback;
}
