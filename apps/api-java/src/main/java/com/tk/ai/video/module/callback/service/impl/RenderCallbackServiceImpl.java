package com.tk.ai.video.module.callback.service.impl;
import java.util.UUID;

import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.callback.dto.RenderCallbackRequest;
import com.tk.ai.video.module.callback.service.RenderCallbackService;
import com.tk.ai.video.module.log.entity.RenderLogEntity;
import com.tk.ai.video.module.log.mapper.RenderLogMapper;
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
    private final RenderLogMapper renderLogMapper;

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
        if (isRenderAlreadyCompleted(request, task)) {
            log.debug("Render callback already processed: taskId={}, renderTaskId={}", task.getId(), request.getRenderTaskId());
            return;
        }

        if (!"rendering".equals(task.getStatus()) && !"checking".equals(task.getStatus())) {
            VideoTaskStateMachine.validateTransition(task.getStatus(), "checking");
        }

        VideoEntity video = new VideoEntity();
        video.setId(request.getVideoId() != null ? request.getVideoId() : UUID.randomUUID());
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

        writeRenderLog(request, task, video);

        if ("rendering".equals(task.getStatus())) {
            advanceTask(task, "checking");
        }
        advanceTask(task, "completed");
        task.setProgress(100);
        task.setRenderTaskId(request.getRenderTaskId());
        if (request.getManifestVersion() != null) {
            task.setManifestVersion(request.getManifestVersion());
        }
        task.setCompletedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        log.info("Render completed: taskId={}, videoId={}", task.getId(), video.getId());
    }

    private void handleRenderFailed(RenderCallbackRequest request, VideoTaskEntity task) {
        if ("failed".equals(task.getStatus())
                && request.getRenderTaskId() != null
                && request.getRenderTaskId().equals(task.getRenderTaskId())) {
            log.debug("Render failure callback already processed: taskId={}, renderTaskId={}", task.getId(), request.getRenderTaskId());
            return;
        }

        if (request.getError() != null) {
            task.setFailedStage("rendering");
            task.setErrorCode((String) request.getError().get("errorCode"));
            task.setErrorMessage((String) request.getError().get("errorMessage"));
            task.setErrorRetryable(Boolean.TRUE.equals(request.getError().get("retryable")));
        }
        if (!"failed".equals(task.getStatus())) {
            VideoTaskStateMachine.validateTransition(task.getStatus(), "failed");
        }
        task.setRenderTaskId(request.getRenderTaskId());
        task.setStatus("failed");
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);
        writeRenderLog(request, task, null);

        log.info("Render failed: taskId={}, error={}", task.getId(), task.getErrorCode());
    }

    private boolean isRenderAlreadyCompleted(RenderCallbackRequest request, VideoTaskEntity task) {
        if (request.getRenderTaskId() != null && request.getRenderTaskId().equals(task.getRenderTaskId())
                && ("completed".equals(task.getStatus()) || "exported".equals(task.getStatus()))) {
            return true;
        }
        return videoMapper.findLatestByTaskId(task.getId()).isPresent()
                && ("completed".equals(task.getStatus()) || "exported".equals(task.getStatus()));
    }

    private void advanceTask(VideoTaskEntity task, String targetStatus) {
        try {
            VideoTaskStateMachine.validateTransition(task.getStatus(), targetStatus);
        } catch (Exception e) {
            log.warn("Task transition skipped: taskId={}, {} -> {}", task.getId(), task.getStatus(), targetStatus);
            return;
        }
        task.setStatus(targetStatus);
    }

    private void writeRenderLog(RenderCallbackRequest request, VideoTaskEntity task, VideoEntity video) {
        RenderLogEntity renderLog = new RenderLogEntity();
        renderLog.setId(UUID.randomUUID());
        renderLog.setTaskId(task.getId());
        renderLog.setVideoId(video != null ? video.getId() : null);
        renderLog.setRenderTaskId(request.getRenderTaskId());
        renderLog.setTemplate(readTemplate(request.getRenderLog()));
        renderLog.setStatus("completed".equals(request.getStatus()) ? "success" : "failed");
        renderLog.setDurationSeconds(request.getDuration());
        renderLog.setOutputUrl(request.getVideoUrl());
        renderLog.setCoverUrl(request.getCoverUrl());
        renderLog.setMetadata(request.getRenderLog());
        if (request.getError() != null) {
            renderLog.setErrorMessage((String) request.getError().get("errorMessage"));
        }
        renderLogMapper.insert(renderLog);
    }

    private String readTemplate(Map<String, Object> renderLog) {
        if (renderLog != null && renderLog.get("template") instanceof String template && !template.isBlank()) {
            return template;
        }
        return "unknown";
    }
}
