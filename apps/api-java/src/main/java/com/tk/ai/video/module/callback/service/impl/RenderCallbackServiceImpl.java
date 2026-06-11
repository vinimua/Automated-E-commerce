package com.tk.ai.video.module.callback.service.impl;

import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.callback.dto.RenderCallbackRequest;
import com.tk.ai.video.module.callback.service.RenderCallbackService;
import com.tk.ai.video.module.video.entity.VideoEntity;
import com.tk.ai.video.module.video.mapper.VideoMapper;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import com.tk.ai.video.module.videotask.state.VideoTaskStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RenderCallbackServiceImpl implements RenderCallbackService {

    private final VideoTaskMapper videoTaskMapper;
    private final VideoMapper videoMapper;

    @Override
    @Transactional
    public void handleCallback(RenderCallbackRequest request) {
        VideoTaskEntity task = videoTaskMapper.selectById(request.getTaskId());
        if (task == null) {
            throw new ResourceNotFoundException("VideoTask", request.getTaskId());
        }

        if ("completed".equals(request.getStatus())) {
            handleRenderSuccess(request, task);
        } else {
            handleRenderFailed(request, task);
        }
    }

    private void handleRenderSuccess(RenderCallbackRequest request, VideoTaskEntity task) {
        // Create video record
        VideoEntity video = new VideoEntity();
        video.setTaskId(task.getId());
        video.setProductId(task.getProductId());
        video.setUserId(task.getUserId());
        video.setTitle(request.getRenderLog() != null
                ? (String) request.getRenderLog().getOrDefault("template", "video")
                : "Generated Video");
        video.setVideoUrl(request.getVideoUrl());
        video.setCoverUrl(request.getCoverUrl());
        video.setDuration(request.getDuration() != null ? request.getDuration() : task.getDuration());
        video.setResolution(request.getResolution() != null ? request.getResolution() : "1080x1920");
        video.setFps(30);
        video.setStatus("completed");
        videoMapper.insert(video);

        // Advance task to checking
        try {
            VideoTaskStateMachine.validateTransition(task.getStatus(), "checking");
        } catch (Exception e) {
            log.warn("Task already advanced: taskId={}, status={}", task.getId(), task.getStatus());
            return;
        }
        task.setStatus("completed");
        task.setProgress(100);
        task.setCompletedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        log.info("Render completed: taskId={}, videoId={}", task.getId(), video.getId());
    }

    private void handleRenderFailed(RenderCallbackRequest request, VideoTaskEntity task) {
        if (request.getError() != null) {
            task.setFailedStage("rendering");
            task.setErrorCode((String) request.getError().get("errorCode"));
            task.setErrorMessage((String) request.getError().get("errorMessage"));
            task.setErrorRetryable(Boolean.TRUE.equals(request.getError().get("retryable")));
        }
        task.setStatus("failed");
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        log.info("Render failed: taskId={}, error={}", task.getId(), task.getErrorCode());
    }
}
