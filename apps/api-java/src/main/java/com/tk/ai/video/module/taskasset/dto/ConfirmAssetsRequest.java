package com.tk.ai.video.module.taskasset.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ConfirmAssetsRequest {
    private List<UUID> assetIds;
    /** User's creative direction — optional. Injected into AI analysis as rawPrompt. */
    private String creativePrompt;
    /** Skip asset analysis and go directly to reference analysis (REFERENCE_STORYBOARD only). */
    private boolean skipAssetAnalysis;
}
