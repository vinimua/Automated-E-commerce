package com.tk.ai.video.module.taskasset.service;

import com.tk.ai.video.module.taskasset.dto.*;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;

import java.util.List;
import java.util.UUID;

public interface TaskAssetService {

    List<TaskAssetResponse> getAssets(UUID taskId, UUID userId);

    List<TaskAssetResponse> addAsset(UUID taskId, CreateAssetRequest request, UUID userId);

    List<TaskAssetResponse> updateAssetRole(UUID taskId, UUID assetId, UpdateAssetRoleRequest request, UUID userId);

    VideoTaskStatusResponse confirmAssets(UUID taskId, ConfirmAssetsRequest request, UUID userId);
}
