package com.tk.ai.video.module.taskasset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class CreateAssetRequest {

    @NotBlank
    private String assetKind;

    @NotBlank
    private String assetRole;

    @NotBlank
    private String source;

    @NotBlank
    private String url;

    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private String description;
    private Map<String, Object> metadata;
}
