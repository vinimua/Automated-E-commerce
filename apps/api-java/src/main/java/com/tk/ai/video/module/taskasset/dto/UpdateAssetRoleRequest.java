package com.tk.ai.video.module.taskasset.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateAssetRoleRequest {

    @NotBlank
    @JsonAlias("role")
    private String assetRole;

    private UUID shotId;
}
