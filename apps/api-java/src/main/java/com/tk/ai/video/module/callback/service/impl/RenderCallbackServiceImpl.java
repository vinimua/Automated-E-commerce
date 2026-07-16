package com.tk.ai.video.module.callback.service.impl;

import com.tk.ai.video.common.BusinessException;
import com.tk.ai.video.module.callback.dto.RenderCallbackRequest;
import com.tk.ai.video.module.callback.service.RenderCallbackService;
import com.tk.ai.video.module.log.entity.RenderLogEntity;
import com.tk.ai.video.module.log.mapper.RenderLogMapper;
import com.tk.ai.video.module.repairevent.mapper.RepairEventMapper;
import com.tk.ai.video.module.video.entity.VideoEntity;
import com.tk.ai.video.module.video.mapper.VideoMapper;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import com.tk.ai.video.module.videotask.state.VideoTaskStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RenderCallbackServiceImpl implements RenderCallbackService {

    private final VideoTaskMapper videoTaskMapper;
    private final VideoMapper videoMapper;
    private final RenderLogMapper renderLogMapper;
    private final RepairEventMapper repairEventMapper;

    @Override
    @Transactional
    public void handleCallback(RenderCallbackRequest request) {
        UUID taskId = request.getTaskId();
        VideoTaskEntity task = videoTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Task not found: " + taskId);
        }

        if (StringUtils.hasText(task.getRenderTaskId())
                && StringUtils.hasText(request.getRenderTaskId())
                && !task.getRenderTaskId().equals(request.getRenderTaskId())) {
            log.warn("Render callback ignored because renderTaskId does not match: taskId={}, expected={}, actual={}",
                    taskId, task.getRenderTaskId(), request.getRenderTaskId());
            return;
        }

        if (!"rendering".equals(task.getStatus())) {
            log.warn("Render callback ignored because task is not rendering: taskId={}, status={}",
                    taskId, task.getStatus());
            return;
        }

        if ("completed".equals(request.getStatus())) {
            handleCompleted(request, task);
        } else {
            handleFailed(request, task);
        }
    }

    private void handleCompleted(RenderCallbackRequest request, VideoTaskEntity task) {
        if (!StringUtils.hasText(request.getVideoUrl())) {
            throw new BusinessException("videoUrl is required when render callback status is completed");
        }

        UUID videoId = request.getVideoId() != null ? request.getVideoId() : extractVideoId(task);
        final boolean[] createdNewVideo = {false};
        VideoEntity video = videoMapper.findLatestByTaskId(task.getId()).orElseGet(() -> {
            createdNewVideo[0] = true;
            VideoEntity created = new VideoEntity();
            created.setId(videoId != null ? videoId : UUID.randomUUID());
            created.setTaskId(task.getId());
            created.setProductId(task.getProductId());
            created.setUserId(task.getUserId());
            return created;
        });

        video.setTitle(extractTitle(task));
        video.setVideoUrl(request.getVideoUrl());
        video.setCoverUrl(request.getCoverUrl());
        video.setDuration(request.getDuration() != null ? request.getDuration() : task.getDuration());
        video.setResolution(StringUtils.hasText(request.getResolution()) ? request.getResolution() : "1080x1920");
        video.setFps(30);
        video.setStatus("completed");
        if (createdNewVideo[0]) {
            videoMapper.insert(video);
        } else {
            videoMapper.updateById(video);
        }

        insertRenderLog(request, task, video.getId(), "completed", null);

        VideoTaskStateMachine.validateTransition(task.getStatus(), "waiting_final_review");
        task.setStatus("waiting_final_review");
        task.setProgress(95);
        task.setDuration(video.getDuration());
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);
        completeActiveRenderRepairEvent(task);

        log.info("Render completed: taskId={}, videoId={}, videoUrl={}, duration={}s",
                task.getId(), video.getId(), request.getVideoUrl(), video.getDuration());
    }

    private void handleFailed(RenderCallbackRequest request, VideoTaskEntity task) {
        String errorCode = extractErrorString(request, "errorCode", "RENDER_FAILED");
        String errorMessage = extractErrorString(request, "errorMessage", "Render failed");
        boolean retryable = extractRetryable(request);

        insertRenderLog(request, task, null, "failed", errorMessage);

        VideoTaskStateMachine.validateTransition(task.getStatus(), "failed");
        task.setStatus("failed");
        task.setFailedStage("rendering");
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setErrorRetryable(retryable);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);
        failActiveRenderRepairEvent(task, errorMessage);

        log.warn("Render failed: taskId={}, error={}, message={}",
                task.getId(), errorCode, errorMessage);
    }

    private void insertRenderLog(RenderCallbackRequest request, VideoTaskEntity task, UUID videoId, String status, String errorMessage) {
        RenderLogEntity logEntity = new RenderLogEntity();
        logEntity.setId(UUID.randomUUID());
        logEntity.setTaskId(task.getId());
        logEntity.setVideoId(videoId);
        logEntity.setRenderTaskId(request.getRenderTaskId());
        logEntity.setTemplate(extractTemplate(task));
        logEntity.setStatus(status);
        logEntity.setDurationSeconds(request.getDuration());
        logEntity.setOutputUrl(request.getVideoUrl());
        logEntity.setCoverUrl(request.getCoverUrl());
        logEntity.setErrorMessage(errorMessage);

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.getRenderLog() != null) {
            metadata.putAll(request.getRenderLog());
        }
        metadata.put("manifestVersion", request.getManifestVersion());
        metadata.put("callbackStatus", request.getStatus());
        metadata.put("error", request.getError());
        logEntity.setMetadata(metadata);
        renderLogMapper.insert(logEntity);
    }

    private UUID extractVideoId(VideoTaskEntity task) {
        Map<String, Object> manifest = task.getRenderManifest();
        if (manifest == null || manifest.get("videoId") == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(manifest.get("videoId")));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String extractTitle(VideoTaskEntity task) {
        Map<String, Object> manifest = task.getRenderManifest();
        if (manifest != null && manifest.get("cover") instanceof Map<?, ?> cover && cover.get("text") != null) {
            return String.valueOf(cover.get("text"));
        }
        return "Generated video";
    }

    private String extractTemplate(VideoTaskEntity task) {
        Map<String, Object> manifest = task.getRenderManifest();
        if (manifest != null && manifest.get("template") != null) {
            return String.valueOf(manifest.get("template"));
        }
        return null;
    }

    private String extractErrorString(RenderCallbackRequest request, String key, String fallback) {
        if ("errorCode".equals(key) && StringUtils.hasText(request.getErrorCode())) {
            return request.getErrorCode();
        }
        if ("errorMessage".equals(key) && StringUtils.hasText(request.getErrorMessage())) {
            return request.getErrorMessage();
        }
        if (request.getError() != null && request.getError().get(key) != null) {
            return String.valueOf(request.getError().get(key));
        }
        return fallback;
    }

    private boolean extractRetryable(RenderCallbackRequest request) {
        if (request.getError() != null && request.getError().get("retryable") instanceof Boolean retryable) {
            return retryable;
        }
        return false;
    }

    private void completeActiveRenderRepairEvent(VideoTaskEntity task) {
        repairEventMapper.findByTaskId(task.getId()).stream()
                .filter(event -> "in_progress".equals(event.getStatus()))
                .filter(event -> "render_manifest".equals(event.getTargetType()) || "final_video".equals(event.getTargetType()))
                .findFirst()
                .ifPresent(event -> {
                    event.setStatus("completed");
                    event.setAfterVersion(task.getCurrentVersion());
                    event.setUpdatedAt(OffsetDateTime.now());
                    repairEventMapper.updateById(event);
                });
    }

    private void failActiveRenderRepairEvent(VideoTaskEntity task, String errorMessage) {
        repairEventMapper.findByTaskId(task.getId()).stream()
                .filter(event -> "in_progress".equals(event.getStatus()))
                .filter(event -> "render_manifest".equals(event.getTargetType()) || "final_video".equals(event.getTargetType()))
                .findFirst()
                .ifPresent(event -> {
                    event.setStatus("failed");
                    Map<String, Object> plan = event.getRepairPlan() != null
                            ? new LinkedHashMap<>(event.getRepairPlan())
                            : new LinkedHashMap<>();
                    plan.put("errorMessage", errorMessage);
                    event.setRepairPlan(plan);
                    event.setUpdatedAt(OffsetDateTime.now());
                    repairEventMapper.updateById(event);
                });
    }
}
