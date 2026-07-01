package com.tk.ai.video.module.taskasset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateAssetRoleRequest {

    @NotBlank
    private String assetRole;

    private UUID shotId;
}
