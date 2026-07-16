package com.tk.ai.video.module.videotask.service;

import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.repairevent.dto.FeedbackRequest;
import com.tk.ai.video.module.repairevent.dto.RepairEventResponse;
import com.tk.ai.video.module.videotask.dto.*;

import java.util.List;
import java.util.UUID;

public interface VideoTaskService {
    CreateVideoTaskResponse create(CreateVideoTaskRequest request, UUID userId);
    FashionTaskCreateResponse createFashionTask(CreateFashionTaskRequest request, UUID userId);
    VideoTaskResponse getById(UUID taskId, UUID userId);
    PageResult<VideoTaskResponse> list(String status, UUID productId, int page, int pageSize, UUID userId);
    VideoTaskStatusResponse selectPlan(UUID taskId, SelectPlanRequest request, UUID userId);
    VideoTaskStatusResponse retry(UUID taskId, UUID userId);

    // Fashion Creative Loop V1 methods
    VideoTaskStatusResponse confirmPlan(UUID taskId, ConfirmPlanRequest request, UUID userId);
    VideoTaskStatusResponse confirmStoryboard(UUID taskId, UUID userId);
    VideoTaskStatusResponse requestRender(UUID taskId, UUID userId);
    VideoTaskStatusResponse approveFinalReview(UUID taskId, UUID userId);
    VideoTaskStatusResponse submitFeedback(UUID taskId, FeedbackRequest request, UUID userId);
    List<RepairEventResponse> getRepairEvents(UUID taskId, UUID userId);
    VideoTaskStatusResponse cancelTask(UUID taskId, UUID userId);
}
