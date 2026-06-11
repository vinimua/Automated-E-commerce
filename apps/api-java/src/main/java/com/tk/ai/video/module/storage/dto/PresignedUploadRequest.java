package com.tk.ai.video.module.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PresignedUploadRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String mimeType;

    @NotNull
    @Min(1)
    private Long sizeBytes;

    private String contentMd5;

    @NotBlank
    private String folder;

    private UUID productId;
    private UUID taskId;
}
