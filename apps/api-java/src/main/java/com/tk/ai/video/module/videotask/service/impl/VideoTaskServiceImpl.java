package com.tk.ai.video.module.videotask.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tk.ai.video.common.*;
import com.tk.ai.video.module.product.entity.ProductEntity;
import com.tk.ai.video.module.product.mapper.ProductImageMapper;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.repairevent.dto.FeedbackRequest;
import com.tk.ai.video.module.repairevent.dto.RepairEventResponse;
import com.tk.ai.video.module.repairevent.entity.RepairEventEntity;
import com.tk.ai.video.module.repairevent.mapper.RepairEventMapper;
import com.tk.ai.video.module.storyboard.entity.VideoPlanEntity;
import com.tk.ai.video.module.storyboard.mapper.StoryboardMapper;
import com.tk.ai.video.module.storyboard.mapper.VideoPlanMapper;
import com.tk.ai.video.module.videoclip.entity.VideoClipEntity;
import com.tk.ai.video.module.videoclip.mapper.VideoClipMapper;
import com.tk.ai.video.module.videotask.dto.*;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.enums.VideoType;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import com.tk.ai.video.module.videotask.service.VideoTaskService;
import com.tk.ai.video.module.videotask.state.VideoTaskStateMachine;
import com.tk.ai.video.module.keyframe.entity.KeyframeEntity;
import com.tk.ai.video.module.keyframe.mapper.KeyframeMapper;
import com.tk.ai.video.module.quota.service.QuotaService;
import com.tk.ai.video.module.callback.service.impl.RenderMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTaskServiceImpl implements VideoTaskService {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(15, 20, 25, 30);
    private static final int MAX_CONCURRENT_TASKS = 2;
    private static final int MAX_DAILY_TASKS = 10;

    private final VideoTaskMapper videoTaskMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final VideoPlanMapper videoPlanMapper;
    private final QuotaService quotaService;
    private final AiServiceClient aiServiceClient;

    // Fashion Creative Loop V1 mappers
    private final KeyframeMapper keyframeMapper;
    private final VideoClipMapper videoClipMapper;
    private final RepairEventMapper repairEventMapper;
    private final RenderMessageProducer renderMessageProducer;

    @Override
    @Transactional
    public CreateVideoTaskResponse create(CreateVideoTaskRequest request, UUID userId) {
        // Determine task mode: default to PRODUCT_CREATIVE for fashion creative loop
        String taskMode = request.getTaskMode() != null ? request.getTaskMode() : "PRODUCT_CREATIVE";
        boolean isFashionMode = "PRODUCT_CREATIVE".equals(taskMode)
                || "REFERENCE_STORYBOARD".equals(taskMode)
                || "USER_SCRIPT".equals(taskMode)
                || "CUSTOM_STORYBOARD".equals(taskMode);
        String effectiveVideoType = request.getVideoType() != null ? request.getVideoType() : "pain_point_solution";

        if (!isFashionMode && request.getVideoType() == null) {
            throw new BusinessException("videoType is required for legacy mode");
        }
        if (!isFashionMode && !VideoType.V1_ALLOWED.contains(effectiveVideoType)) {
            throw new BusinessException("V1 only supports: " + String.join(", ", VideoType.V1_ALLOWED));
        }
        if (!ALLOWED_DURATIONS.contains(request.getDuration())) {
            throw new BusinessException("Duration must be one of: 15, 20, 25, 30");
        }

        // Validate product ownership
        ProductEntity product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new ResourceNotFoundException("Product", request.getProductId());
        }
        if (!product.getUserId().equals(userId)) {
            throw new ResourceForbiddenException("Product does not belong to current user");
        }

        // Serialize quota and task-limit checks per user. This prevents two
        // concurrent create requests from both passing the active-task count.
        quotaService.lockAndRefreshDailyQuota(userId);

        // Check concurrent task limit: max 2 in-progress tasks
        long activeCount = videoTaskMapper.selectCount(new LambdaQueryWrapper<VideoTaskEntity>()
                .eq(VideoTaskEntity::getUserId, userId)
                .notIn(VideoTaskEntity::getStatus, "completed", "failed", "exported", "cancelled"));
        if (activeCount >= MAX_CONCURRENT_TASKS) {
            throw new BusinessException(40001,
                    "You already have " + activeCount + " active task(s). Maximum " + MAX_CONCURRENT_TASKS + " concurrent tasks allowed. Please wait for them to complete or fail.");
        }

        // Check daily task limit: max 10 per day
        OffsetDateTime todayStart = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todayCount = videoTaskMapper.selectCount(new LambdaQueryWrapper<VideoTaskEntity>()
                .eq(VideoTaskEntity::getUserId, userId)
                .ge(VideoTaskEntity::getCreatedAt, todayStart));
        if (todayCount >= MAX_DAILY_TASKS) {
            throw new BusinessException(40002,
                    "Daily task limit reached (" + MAX_DAILY_TASKS + "). Please try again tomorrow.");
        }

        UUID taskId = UUID.randomUUID();

        // Insert task
        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(request.getProductId());
        // Fashion mode: start at asset_uploading; Legacy mode: start at analyzing
        String initialStatus = isFashionMode ? "asset_uploading" : "analyzing";
        task.setStatus(initialStatus);
        task.setProgress(0);
        task.setDuration(request.getDuration());
        task.setVideoType(effectiveVideoType);
        task.setNeedSubtitles(request.getNeedSubtitles() != null ? request.getNeedSubtitles() : true);
        task.setNeedVoiceover(request.getNeedVoiceover() != null ? request.getNeedVoiceover() : false);
        task.setManifestVersion("1.0.0");
        task.setSchemaVersion("1.0.0");
        task.setRetryCount(0);

        // Fashion Creative Loop V1 fields
        task.setTaskMode(taskMode);
        task.setProductCategory(request.getProductCategory() != null ? request.getProductCategory() : "general");
        task.setShotCount(request.getShotCount());
        task.setCurrentVersion(1);

        videoTaskMapper.insert(task);

        // Pre-deduct 1 video quota after task insert because quota_records.task_id
        // has a foreign key to video_tasks(id). The surrounding transaction rolls
        // back the task if quota consumption fails.
        String idempotencyKey = "task:" + taskId + ":video:create";
        quotaService.consumeQuota(userId, taskId, "video", 1, idempotencyKey);

        log.info("VideoTask created: taskId={}, videoType={}, duration={}s, taskMode={}", taskId, effectiveVideoType, request.getDuration(), taskMode);

        // Dispatch to Python AI orchestrator only for legacy mode
        //如果当前任务不是新版 Fashion Creative Loop 流程，就启动旧版 V1 的 Python AI 分析流程
        if (!isFashionMode) {
            List<String> imageUrls = productImageMapper.findByProductId(product.getId()).stream()
                    .map(img -> img.getUrl())
                    .collect(Collectors.toList());
            aiServiceClient.startProductAnalysis(
                    taskId, product.getId(), userId,
                    product.getName(), product.getDescription(), product.getProductLink(),
                    imageUrls, product.getTargetMarket(), product.getLanguage()
            );
        }

        return new CreateVideoTaskResponse(taskId, initialStatus, 0);
    }

    @Override
    public VideoTaskResponse getById(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);
        return toResponse(task);
    }

    @Override
    public PageResult<VideoTaskResponse> list(String status, UUID productId, int page, int pageSize, UUID userId) {
        Page<VideoTaskEntity> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<VideoTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoTaskEntity::getUserId, userId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(VideoTaskEntity::getStatus, status);
        }
        if (productId != null) {
            wrapper.eq(VideoTaskEntity::getProductId, productId);
        }
        wrapper.orderByDesc(VideoTaskEntity::getCreatedAt);

        Page<VideoTaskEntity> result = videoTaskMapper.selectPage(p, wrapper);
        List<VideoTaskResponse> items = result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PageResult<>(items, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal(), (int) result.getPages());
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse selectPlan(UUID taskId, SelectPlanRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        VideoPlanEntity plan = videoPlanMapper.findOwnedPlan(request.getPlanId(), taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoPlan", request.getPlanId()));

        if ("plan_generated".equals(task.getStatus())) {
            VideoTaskStateMachine.validateTransition(task.getStatus(), "waiting_plan_selection");
            task.setStatus("waiting_plan_selection");
        }
        VideoTaskStateMachine.validateTransition(task.getStatus(), "script_generating");

        task.setSelectedPlanId(plan.getId());
        task.setStatus("script_generating");
        task.setProgress(30);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        // Dispatch Phase 2 workflow to Python AI
        aiServiceClient.startSelectedPlanGeneration(
                taskId, task.getProductId(), userId,
                plan.getId(), toSelectedPlanPayload(plan),
                task.getDuration(), task.getVideoType(),
                task.getNeedSubtitles() != null && task.getNeedSubtitles(),
                task.getNeedVoiceover() != null && task.getNeedVoiceover()
        );

        log.info("Plan selected: taskId={}, planId={}", taskId, request.getPlanId());
        return new VideoTaskStatusResponse(taskId, "script_generating", 30);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse retry(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!"failed".equals(task.getStatus())) {
            throw new BusinessException("Only failed tasks can be retried");
        }
        if (task.getErrorRetryable() == null || !task.getErrorRetryable()) {
            throw new BusinessException("This task error is not retryable");
        }

        // Refund old quota
        String refundKey = "task:" + taskId + ":refund:video:" + task.getRetryCount();
        quotaService.refundQuota(userId, taskId, "video", 1, refundKey);

        // Determine retry target
        String retryTarget = VideoTaskStateMachine.getRetryTarget(task.getFailedStage());

        // Reset task
        task.setStatus(retryTarget);
        task.setProgress(0);
        task.setFailedStage(null);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setErrorRetryable(null);
        task.setRetryCount(task.getRetryCount() + 1);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        dispatchRetry(task, userId, retryTarget);

        log.info("Task retry: taskId={}, retryTarget={}, retryCount={}", taskId, retryTarget, task.getRetryCount());

        return new VideoTaskStatusResponse(taskId, retryTarget, 0);
    }

    // ── Fashion Creative Loop V1 methods ──

    @Override
    @Transactional
    public VideoTaskStatusResponse confirmPlan(UUID taskId, ConfirmPlanRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        VideoPlanEntity plan = videoPlanMapper.findOwnedPlan(request.getPlanId(), taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoPlan", request.getPlanId()));

        VideoTaskStateMachine.validateTransition(task.getStatus(), "storyboard_generating");

        task.setSelectedPlanId(plan.getId());
        task.setStatus("storyboard_generating");
        task.setProgress(40);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        aiServiceClient.startStoryboardGeneration(
                taskId,
                task.getProductId(),
                userId,
                plan.getId(),
                toSelectedPlanPayload(plan),
                task.getDuration(),
                task.getVideoType()
        );

        log.info("Plan confirmed, advancing to storyboard generation: taskId={}", taskId);
        return new VideoTaskStatusResponse(taskId, "storyboard_generating", 40);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse confirmStoryboard(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        VideoTaskStateMachine.validateTransition(task.getStatus(), "keyframe_configuring");

        task.setStatus("keyframe_configuring");
        task.setProgress(50);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        log.info("Storyboard confirmed, advancing to keyframe configuration: taskId={}", taskId);
        return new VideoTaskStatusResponse(taskId, "keyframe_configuring", 50);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse requestRender(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        List<VideoClipEntity> currentVersionClips = videoClipMapper.findByTaskIdAndVersion(taskId, task.getCurrentVersion());
        long confirmedClips = currentVersionClips.stream()
                .filter(clip -> "confirmed".equals(clip.getStatus()))
                .count();
        if (confirmedClips == 0) {
            throw new BusinessException("At least one confirmed video clip is required before rendering.");
        }

        // Validate all clips are confirmed
        long unconfirmedClips = videoClipMapper.countUnconfirmedByTaskIdAndVersion(taskId, task.getCurrentVersion());
        if (unconfirmedClips > 0) {
            throw new BusinessException("All video clips must be confirmed before rendering. " + unconfirmedClips + " clip(s) pending.");
        }

        VideoTaskStateMachine.validateTransition(task.getStatus(), "rendering");

        task.setStatus("rendering");
        task.setProgress(90);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        // Build RenderManifest from confirmed keyframes and video clips
        String renderTaskId = UUID.randomUUID().toString();
        task.setRenderTaskId(renderTaskId);
        videoTaskMapper.updateById(task);

        log.info("Render requested: taskId={}, renderTaskId={}", taskId, renderTaskId);
        return new VideoTaskStatusResponse(taskId, "rendering", 90);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse approveFinalReview(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        VideoTaskStateMachine.validateTransition(task.getStatus(), "completed");

        task.setStatus("completed");
        task.setProgress(100);
        task.setCompletedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        log.info("Final review approved: taskId={}", taskId);
        return new VideoTaskStatusResponse(taskId, "completed", 100);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse submitFeedback(UUID taskId, FeedbackRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        VideoTaskStateMachine.validateTransition(task.getStatus(), "repairing");

        // Create repair event
        RepairEventEntity repairEvent = new RepairEventEntity();
        repairEvent.setId(UUID.randomUUID());
        repairEvent.setTaskId(taskId);
        repairEvent.setUserId(userId);
        repairEvent.setTargetType(request.getTargetType());
        repairEvent.setTargetId(request.getTargetId());
        repairEvent.setUserFeedback(request.getFeedbackText());
        repairEvent.setIssueType(request.getCategory());
        repairEvent.setBeforeVersion(task.getCurrentVersion());
        repairEvent.setStatus("created");
        repairEventMapper.insert(repairEvent);

        // Advance task
        task.setStatus("repairing");
        task.setProgress(95);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        Map<String, Object> currentState = Map.of(
                "status", task.getStatus(),
                "currentVersion", task.getCurrentVersion(),
                "repairEventId", repairEvent.getId().toString()
        );
        aiServiceClient.startRepairWorkflow(
                taskId,
                task.getProductId(),
                userId,
                repairEvent.getId(),
                request.getFeedbackText(),
                request.getCategory(),
                request.getTargetType(),
                currentState
        );

        log.info("Feedback submitted, task entering repair: taskId={}, repairEventId={}", taskId, repairEvent.getId());
        return new VideoTaskStatusResponse(taskId, "repairing", 95);
    }

    @Override
    public List<RepairEventResponse> getRepairEvents(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        return repairEventMapper.findByTaskId(taskId).stream()
                .map(e -> RepairEventResponse.builder()
                        .repairEventId(e.getId())
                        .taskId(e.getTaskId())
                        .targetType(e.getTargetType())
                        .targetId(e.getTargetId())
                        .userFeedback(e.getUserFeedback())
                        .issueType(e.getIssueType())
                        .status(e.getStatus())
                        .createdAt(e.getCreatedAt())
                        .updatedAt(e.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse cancelTask(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        Set<String> terminals = Set.of("completed", "exported", "failed", "cancelled");
        if (terminals.contains(task.getStatus())) {
            throw new BusinessException("Cannot cancel a task that is already " + task.getStatus());
        }

        VideoTaskStateMachine.validateTransition(task.getStatus(), "cancelled");

        task.setStatus("cancelled");
        task.setProgress(0);
        task.setCompletedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        log.info("Task cancelled: taskId={}, previousStatus={}", taskId, task.getStatus());
        return new VideoTaskStatusResponse(taskId, "cancelled", 0);
    }

    private VideoTaskEntity findTask(UUID taskId) {
        VideoTaskEntity task = videoTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("VideoTask", taskId);
        }
        return task;
    }

    private void checkOwnership(VideoTaskEntity task, UUID userId) {
        if (!task.getUserId().equals(userId)) {
            throw new ResourceForbiddenException("Task does not belong to current user");
        }
    }

    private Map<String, Object> toSelectedPlanPayload(VideoPlanEntity plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", plan.getId().toString());
        payload.put("type", plan.getType());
        payload.put("title", plan.getTitle());
        payload.put("hook", plan.getHook());
        payload.put("structure", plan.getStructure());
        payload.put("reason", plan.getReason());
        payload.put("estimatedDuration", plan.getEstimatedDuration());
        payload.put("score", plan.getScore());
        return payload;
    }

    private void dispatchRetry(VideoTaskEntity task, UUID userId, String retryTarget) {
        ProductEntity product = productMapper.selectById(task.getProductId());
        if (product == null) {
            throw new ResourceNotFoundException("Product", task.getProductId());
        }

        if ("analyzing".equals(retryTarget)) {
            List<String> imageUrls = productImageMapper.findByProductId(product.getId()).stream()
                    .map(img -> img.getUrl())
                    .collect(Collectors.toList());
            aiServiceClient.startProductAnalysis(
                    task.getId(), product.getId(), userId,
                    product.getName(), product.getDescription(), product.getProductLink(),
                    imageUrls, product.getTargetMarket(), product.getLanguage()
            );
            return;
        }

        if ("script_generating".equals(retryTarget) || "material_generating".equals(retryTarget) || "rendering".equals(retryTarget)) {
            if (task.getSelectedPlanId() == null) {
                throw new BusinessException("Task has no selected plan to retry");
            }
            VideoPlanEntity plan = videoPlanMapper.findOwnedPlan(task.getSelectedPlanId(), task.getId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("VideoPlan", task.getSelectedPlanId()));
            aiServiceClient.startSelectedPlanGeneration(
                    task.getId(), task.getProductId(), userId,
                    plan.getId(), toSelectedPlanPayload(plan),
                    task.getDuration(), task.getVideoType(),
                    task.getNeedSubtitles() != null && task.getNeedSubtitles(),
                    task.getNeedVoiceover() != null && task.getNeedVoiceover()
            );
            return;
        }

        if ("checking".equals(retryTarget)) {
            log.warn("Retry target checking has no local dispatcher yet: taskId={}", task.getId());
            return;
        }

        // Fashion Creative Loop V1 retry targets
        if ("asset_analyzing".equals(retryTarget) || "plan_generating".equals(retryTarget)
                || "storyboard_generating".equals(retryTarget) || "image_generating".equals(retryTarget)
                || "video_clip_generating".equals(retryTarget) || "repairing".equals(retryTarget)) {
            log.info("Retry target {} requires Python AI orchestrator dispatch (not yet wired): taskId={}",
                    retryTarget, task.getId());
            // Future: dispatch to appropriate AI workflow based on retryTarget
        }
    }

    private VideoTaskResponse toResponse(VideoTaskEntity task) {
        return VideoTaskResponse.builder()
                .taskId(task.getId())
                .productId(task.getProductId())
                .status(task.getStatus())
                .progress(task.getProgress())
                .duration(task.getDuration())
                .videoType(task.getVideoType())
                .needSubtitles(task.getNeedSubtitles())
                .needVoiceover(task.getNeedVoiceover())
                .selectedPlanId(task.getSelectedPlanId())
                .failedStage(task.getFailedStage())
                .errorCode(task.getErrorCode())
                .errorMessage(task.getErrorMessage())
                .errorRetryable(task.getErrorRetryable())
                .retryCount(task.getRetryCount())
                .manifestVersion(task.getManifestVersion())
                .schemaVersion(task.getSchemaVersion())
                .taskMode(task.getTaskMode())
                .productCategory(task.getProductCategory())
                .shotCount(task.getShotCount())
                .currentVersion(task.getCurrentVersion())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
