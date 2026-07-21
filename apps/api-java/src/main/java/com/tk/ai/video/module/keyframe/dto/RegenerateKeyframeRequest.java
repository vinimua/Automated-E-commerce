package com.tk.ai.video.module.keyframe.dto;

import lombok.Data;

@Data
public class RegenerateKeyframeRequest {
    /** Optional: user-provided prompt to override the original. */
    private String prompt;
}
