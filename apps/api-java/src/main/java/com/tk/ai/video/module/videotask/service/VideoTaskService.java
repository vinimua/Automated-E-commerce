package com.tk.ai.video.module.videotask.service;

import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.videotask.dto.*;

import java.util.UUID;

public interface VideoTaskService {
    CreateVideoTaskResponse create(CreateVideoTaskRequest request, UUID userId);
    VideoTaskResponse getById(UUID taskId, UUID userId);
    PageResult<VideoTaskResponse> list(String status, UUID productId, int page, int pageSize, UUID userId);
    VideoTaskStatusResponse selectPlan(UUID taskId, SelectPlanRequest request, UUID userId);
    VideoTaskStatusResponse retry(UUID taskId, UUID userId);
}
