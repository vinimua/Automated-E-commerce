package com.tk.ai.video.module.taskasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TaskAssetListResponse {
    private UUID taskId;
    private List<TaskAssetResponse> assets;
}
