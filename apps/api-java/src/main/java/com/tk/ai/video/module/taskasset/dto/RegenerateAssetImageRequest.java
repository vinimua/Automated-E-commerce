package com.tk.ai.video.module.taskasset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegenerateAssetImageRequest {

    @NotBlank
    private String feedback;
}
