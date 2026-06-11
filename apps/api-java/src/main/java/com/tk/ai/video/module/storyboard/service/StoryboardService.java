package com.tk.ai.video.module.storyboard.service;

import com.tk.ai.video.module.storyboard.dto.*;

import java.util.List;
import java.util.UUID;

public interface StoryboardService {
    List<VideoPlanResponse> getPlans(UUID taskId, UUID userId);
    StoryboardResponse getStoryboard(UUID taskId, UUID userId);
    StoryboardResponse updateStoryboard(UUID storyboardId, UpdateStoryboardRequest request, UUID userId);
}
