package com.tk.ai.video.module.keyframe.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateKeyframeRequest {

    @NotNull
    @Min(1)
    private Integer shotNo;

    private String source = "user_upload";
    private UUID assetId;
    private String imagePurpose = "first_frame";
    private String url;
    private String prompt;
    private String userInstruction;
}
