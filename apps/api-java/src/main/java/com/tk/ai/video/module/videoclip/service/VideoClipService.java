package com.tk.ai.video.module.videoclip.service;

import com.tk.ai.video.module.videoclip.dto.*;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;

import java.util.List;
import java.util.UUID;

public interface VideoClipService {

    List<VideoClipResponse> getClips(UUID taskId, UUID userId);

    VideoTaskStatusResponse confirmClip(UUID taskId, UUID clipId, ConfirmVideoClipRequest request, UUID userId);

    VideoTaskStatusResponse rejectClip(UUID taskId, UUID clipId, ConfirmVideoClipRequest request, UUID userId);
}
