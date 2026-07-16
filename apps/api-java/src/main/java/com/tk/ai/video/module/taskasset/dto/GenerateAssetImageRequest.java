package com.tk.ai.video.module.taskasset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class GenerateAssetImageRequest {

    @NotBlank
    private String prompt;

    private List<UUID> sourceAssetIds;

    private String assetRole = "image_variant";
}
