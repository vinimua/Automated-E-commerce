package com.tk.ai.video.module.taskasset.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ConfirmAssetsRequest {
    private List<UUID> assetIds;
}
