package com.tk.ai.video.module.keyframe.service;

import com.tk.ai.video.module.keyframe.dto.*;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;

import java.util.List;
import java.util.UUID;

public interface KeyframeService {

    List<KeyframeResponse> getKeyframes(UUID taskId, UUID userId);

    List<KeyframeResponse> addKeyframe(UUID taskId, CreateKeyframeRequest request, UUID userId);

    VideoTaskStatusResponse confirmKeyframe(UUID taskId, UUID keyframeId, ConfirmKeyframeRequest request, UUID userId);

    VideoTaskStatusResponse rejectKeyframe(UUID taskId, UUID keyframeId, ConfirmKeyframeRequest request, UUID userId);

    List<KeyframeResponse> generateKeyframes(UUID taskId, UUID userId);

    List<KeyframeResponse> regenerateKeyframe(UUID taskId, UUID keyframeId, String prompt, UUID userId);

    List<KeyframeResponse> unconfirmKeyframe(UUID taskId, UUID keyframeId, UUID userId);
}
