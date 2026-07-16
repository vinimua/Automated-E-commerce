package com.tk.ai.video.module.videotask.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tk.ai.video.common.*;
import com.tk.ai.video.module.creativestate.entity.CreativeStateEntity;
import com.tk.ai.video.module.creativestate.mapper.CreativeStateMapper;
import com.tk.ai.video.module.product.entity.ProductEntity;
import com.tk.ai.video.module.product.entity.ProductImageEntity;
import com.tk.ai.video.module.product.mapper.ProductImageMapper;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.taskasset.entity.TaskAssetEntity;
import com.tk.ai.video.module.taskasset.mapper.TaskAssetMapper;
import com.tk.ai.video.module.repairevent.dto.FeedbackRequest;
import com.tk.ai.video.module.repairevent.dto.RepairEventResponse;
import com.tk.ai.video.module.repairevent.entity.RepairEventEntity;
import com.tk.ai.video.module.repairevent.mapper.RepairEventMapper;
import com.tk.ai.video.module.storyboard.entity.StoryboardEntity;
import com.tk.ai.video.module.storyboard.entity.StoryboardShotEntity;
import com.tk.ai.video.module.storyboard.entity.VideoPlanEntity;
import com.tk.ai.video.module.storyboard.mapper.StoryboardMapper;
import com.tk.ai.video.module.storyboard.mapper.StoryboardShotMapper;
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
import com.tk.ai.video.module.video.entity.VideoEntity;
import com.tk.ai.video.module.video.mapper.VideoMapper;
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
    private static final Set<String> ALLOWED_TASK_MODES = Set.of(
            "PRODUCT_CREATIVE", "REFERENCE_STORYBOARD", "USER_SCRIPT", "CUSTOM_STORYBOARD"
    );
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "pain_point_solution", "before_after", "review", "product_showcase", "ugc_style", "tutorial"
    );
    private static final int MAX_CONCURRENT_TASKS = 2;
    private static final int MAX_DAILY_TASKS = 100;

    private final VideoTaskMapper videoTaskMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final VideoPlanMapper videoPlanMapper;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardShotMapper storyboardShotMapper;
    private final QuotaService quotaService;
    private final AiServiceClient aiServiceClient;

    // Fashion Creative Loop V1 mappers
    private final KeyframeMapper keyframeMapper;
    private final VideoClipMapper videoClipMapper;
    private final RepairEventMapper repairEventMapper;
    private final RenderMessageProducer renderMessageProducer;
    private final TaskAssetMapper taskAssetMapper;
    private final CreativeStateMapper creativeStateMapper;
    private final CreativeContextAssembler creativeContextAssembler;
    private final VideoMapper videoMapper;

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
    @Transactional
    public FashionTaskCreateResponse createFashionTask(CreateFashionTaskRequest request, UUID userId) {
        // 1. TaskMode-specific validation
        String taskMode = request.getTaskMode() != null ? request.getTaskMode() : "PRODUCT_CREATIVE";
        List<String> imageUrls = request.getImageUrls() != null ? request.getImageUrls() : List.of();
        String effectiveVideoType = request.getVideoType() != null ? request.getVideoType() : "pain_point_solution";

        if (!ALLOWED_TASK_MODES.contains(taskMode)) {
            throw new BusinessException("Invalid taskMode: " + taskMode);
        }
        if (!ALLOWED_VIDEO_TYPES.contains(effectiveVideoType)) {
            throw new BusinessException("Invalid videoType: " + effectiveVideoType);
        }
        if ("REFERENCE_STORYBOARD".equals(taskMode) && (request.getReferenceVideoUrl() == null || request.getReferenceVideoUrl().isBlank())) {
            throw new BusinessException("REFERENCE_STORYBOARD mode requires a reference video URL");
        }
        if ("USER_SCRIPT".equals(taskMode) && (request.getScriptText() == null || request.getScriptText().isBlank())) {
            throw new BusinessException("USER_SCRIPT mode requires script text");
        }
        if ("CUSTOM_STORYBOARD".equals(taskMode) && (request.getStoryboardText() == null || request.getStoryboardText().isBlank())) {
            throw new BusinessException("CUSTOM_STORYBOARD mode requires storyboard text");
        }
        if (!ALLOWED_DURATIONS.contains(request.getDuration())) {
            throw new BusinessException("Duration must be one of: 15, 20, 25, 30");
        }

        // 2. Quota checks
        quotaService.lockAndRefreshDailyQuota(userId);
        long activeCount = videoTaskMapper.selectCount(new LambdaQueryWrapper<VideoTaskEntity>()
                .eq(VideoTaskEntity::getUserId, userId)
                .notIn(VideoTaskEntity::getStatus, "completed", "failed", "exported", "cancelled"));
        if (activeCount >= MAX_CONCURRENT_TASKS) {
            throw new BusinessException(40001, "You already have " + activeCount + " active task(s). Maximum " + MAX_CONCURRENT_TASKS + " concurrent tasks allowed.");
        }
        OffsetDateTime todayStart = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todayCount = videoTaskMapper.selectCount(new LambdaQueryWrapper<VideoTaskEntity>()
                .eq(VideoTaskEntity::getUserId, userId)
                .ge(VideoTaskEntity::getCreatedAt, todayStart));
        if (todayCount >= MAX_DAILY_TASKS) {
            throw new BusinessException(40002, "Daily task limit reached (" + MAX_DAILY_TASKS + "). Please try again tomorrow.");
        }

        // 3. Create product
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity();
        product.setId(productId);
        product.setUserId(userId);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setProductLink(request.getProductLink());
        product.setTargetMarket(request.getTargetMarket());
        product.setLanguage(request.getLanguage());
        product.setStatus("active");
        productMapper.insert(product);

        // Product images
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImageEntity img = new ProductImageEntity();
            img.setId(UUID.randomUUID());
            img.setProductId(productId);
            img.setUserId(userId);
            img.setUrl(imageUrls.get(i));
            img.setIsPrimary(i == 0);
            productImageMapper.insert(img);
        }

        // 4. Create video task
        UUID taskId = UUID.randomUUID();
        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("asset_uploading");
        task.setProgress(0);
        task.setDuration(request.getDuration());
        task.setVideoType(effectiveVideoType);
        task.setNeedSubtitles(request.getNeedSubtitles() != null ? request.getNeedSubtitles() : true);
        task.setNeedVoiceover(false);
        task.setManifestVersion("1.0.0");
        task.setSchemaVersion("1.0.0");
        task.setRetryCount(0);
        task.setTaskMode(taskMode);
        task.setProductCategory(request.getProductCategory() != null ? request.getProductCategory() : "general");
        task.setShotCount(request.getShotCount());
        task.setCurrentVersion(1);
        videoTaskMapper.insert(task);

        // Consume video quota
        String idempotencyKey = "task:" + taskId + ":video:create";
        quotaService.consumeQuota(userId, taskId, "video", 1, idempotencyKey);

        // 5. Create task assets
        for (int i = 0; i < imageUrls.size(); i++) {
            TaskAssetEntity asset = new TaskAssetEntity();
            asset.setId(UUID.randomUUID());
            asset.setTaskId(taskId);
            asset.setUserId(userId);
            asset.setProductId(productId);
            asset.setAssetKind("image");
            asset.setUrl(imageUrls.get(i));
            asset.setAssetRole(i == 0 ? "product_front" : "product_detail");
            asset.setSource("user_upload");
            asset.setConfirmed(false);
            taskAssetMapper.insert(asset);
        }

        // Reference video asset
        if (request.getReferenceVideoUrl() != null && !request.getReferenceVideoUrl().isBlank()) {
            TaskAssetEntity refAsset = new TaskAssetEntity();
            refAsset.setId(UUID.randomUUID());
            refAsset.setTaskId(taskId);
            refAsset.setUserId(userId);
            refAsset.setProductId(productId);
            refAsset.setAssetKind("video");
            refAsset.setUrl(request.getReferenceVideoUrl());
            refAsset.setAssetRole("reference_video");
            refAsset.setSource("user_upload");
            refAsset.setConfirmed(false);
            taskAssetMapper.insert(refAsset);
        }

        // 6. Create creative state
        CreativeStateEntity cs = new CreativeStateEntity();
        cs.setId(UUID.randomUUID());
        cs.setTaskId(taskId);
        cs.setUserId(userId);
        cs.setVersion(1);

        Map<String, Object> productJson = new LinkedHashMap<>();
        productJson.put("name", request.getName());
        productJson.put("description", request.getDescription() != null ? request.getDescription() : "");
        productJson.put("productLink", request.getProductLink() != null ? request.getProductLink() : "");
        productJson.put("imageUrls", imageUrls);
        productJson.put("targetMarket", request.getTargetMarket());
        productJson.put("language", request.getLanguage());
        cs.setProductJson(productJson);

        if (request.getReferenceVideoUrl() != null && !request.getReferenceVideoUrl().isBlank()) {
            Map<String, Object> refVideo = new LinkedHashMap<>();
            refVideo.put("url", request.getReferenceVideoUrl());
            refVideo.put("source", "user_upload");
            cs.setReferenceVideoJson(refVideo);
        }

        Map<String, Object> userReq = new LinkedHashMap<>();
        userReq.put("taskMode", taskMode);
        userReq.put("duration", request.getDuration());
        userReq.put("needSubtitles", request.getNeedSubtitles() != null ? request.getNeedSubtitles() : true);
        userReq.put("description", request.getDescription() != null ? request.getDescription() : "");
        userReq.put("rawPrompt", request.getCreativePrompt() != null ? request.getCreativePrompt().trim() : "");
        userReq.put("parsed", Map.of());
        userReq.put("confirmed", Map.of());
        if (request.getScriptText() != null && !request.getScriptText().isBlank()) {
            userReq.put("scriptText", request.getScriptText());
        }
        if (request.getStoryboardText() != null && !request.getStoryboardText().isBlank()) {
            userReq.put("storyboardText", request.getStoryboardText());
        }
        cs.setUserRequirementsJson(userReq);
        creativeStateMapper.insert(cs);

        log.info("Fashion task created: taskId={}, productId={}, taskMode={}, images={}, refVideo={}",
                taskId, productId, taskMode, imageUrls.size(), request.getReferenceVideoUrl() != null);

        return new FashionTaskCreateResponse(taskId, productId, "asset_uploading", 0);
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
        task.setVideoType(plan.getType() != null ? plan.getType() : task.getVideoType());
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
                task.getVideoType(),
                creativeContextAssembler.assemble(task)
        );

        log.info("Plan confirmed, advancing to storyboard generation: taskId={}", taskId);
        return new VideoTaskStatusResponse(taskId, "storyboard_generating", 40);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse confirmStoryboard(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        StoryboardEntity storyboard = storyboardMapper.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException("Storyboard not found for task"));
        Map<String, Object> storyboardMap = storyboard.getRawAiOutput();
        if (storyboardMap == null || storyboardMap.isEmpty()) {
            throw new BusinessException("Storyboard raw AI output is empty");
        }

        VideoTaskStateMachine.validateTransition(task.getStatus(), "keyframe_configuring");

        task.setStatus("keyframe_configuring");
        task.setProgress(50);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        aiServiceClient.startKeyframeGeneration(taskId, task.getProductId(), userId, storyboardMap);
        log.info("Auto-triggered keyframe generation after storyboard confirm: taskId={}", taskId);

        log.info("Storyboard confirmed, advancing to keyframe configuration: taskId={}", taskId);
        return new VideoTaskStatusResponse(taskId, "keyframe_configuring", 50);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse requestRender(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        List<KeyframeEntity> confirmedKeyframes = keyframeMapper.findByTaskIdAndVersion(taskId, task.getCurrentVersion()).stream()
                .filter(k -> "confirmed".equals(k.getStatus()))
                .toList();
        if (confirmedKeyframes.isEmpty()) {
            throw new BusinessException("At least one confirmed keyframe is required before rendering.");
        }

        List<VideoClipEntity> confirmedClips = videoClipMapper.findByTaskIdAndVersion(taskId, task.getCurrentVersion()).stream()
                .filter(clip -> "confirmed".equals(clip.getStatus()))
                .toList();
        if (confirmedClips.isEmpty()) {
            throw new BusinessException("At least one confirmed video clip is required before rendering.");
        }
        if (confirmedClips.size() < 4) {
            throw new BusinessException("RenderManifest requires at least 4 confirmed video clips.");
        }

        Map<Integer, VideoClipEntity> clipsByShotNo = confirmedClips.stream()
                .collect(Collectors.toMap(VideoClipEntity::getShotNo, c -> c, (left, right) -> right));
        for (KeyframeEntity keyframe : confirmedKeyframes) {
            VideoClipEntity clip = clipsByShotNo.get(keyframe.getShotNo());
            if (clip == null || !"confirmed".equals(clip.getStatus())) {
                throw new BusinessException("Every confirmed keyframe must have a confirmed video clip before rendering. Missing shotNo=" + keyframe.getShotNo());
            }
        }

        VideoTaskStateMachine.validateTransition(task.getStatus(), "rendering");

        String renderTaskId = UUID.randomUUID().toString();
        UUID videoId = UUID.randomUUID();
        Map<String, Object> renderManifest = buildRenderManifest(task, videoId, confirmedClips);

        task.setStatus("rendering");
        task.setProgress(90);
        task.setRenderTaskId(renderTaskId);
        task.setRenderManifest(renderManifest);
        task.setManifestVersion("1.0.0");
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        String correlationId = org.slf4j.MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        renderMessageProducer.sendRenderTask(taskId, renderTaskId, correlationId, renderManifest);

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

        videoMapper.findLatestByTaskId(taskId).ifPresent(video -> {
            video.setStatus("completed");
            videoMapper.updateById(video);
        });

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
        repairEvent.setStatus("in_progress");
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

    private Map<String, Object> buildRenderManifest(VideoTaskEntity task, UUID videoId, List<VideoClipEntity> confirmedClips) {
        List<VideoClipEntity> orderedClips = confirmedClips.stream()
                .sorted(java.util.Comparator.comparingInt(VideoClipEntity::getShotNo))
                .toList();
        if (orderedClips.size() > 12) {
            throw new BusinessException("RenderManifest supports at most 12 video clips.");
        }

        StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId()).orElse(null);
        Map<Integer, StoryboardShotEntity> shotsByNo = storyboard == null
                ? Map.of()
                : storyboardShotMapper.findByStoryboardId(storyboard.getId()).stream()
                .collect(Collectors.toMap(StoryboardShotEntity::getShotNo, s -> s, (left, right) -> right));
        ProductEntity product = productMapper.selectById(task.getProductId());

        int totalDuration = task.getDuration();
        List<Integer> durations = distributeDurations(totalDuration, orderedClips.size());

        List<Map<String, Object>> assets = new java.util.ArrayList<>();
        for (int i = 0; i < orderedClips.size(); i++) {
            VideoClipEntity clip = orderedClips.get(i);
            if (!StringUtils.hasText(clip.getUrl())) {
                throw new BusinessException("Confirmed video clip has no URL, shotNo=" + clip.getShotNo());
            }
            StoryboardShotEntity shot = shotsByNo.get(clip.getShotNo());
            Map<String, Object> asset = new LinkedHashMap<>();
            asset.put("shotNo", clip.getShotNo());
            asset.put("type", "video");
            asset.put("url", clip.getUrl());
            asset.put("duration", durations.get(i));
            asset.put("subtitle", normalizeSubtitle(shot != null ? shot.getSubtitle() : null, clip.getShotNo()));
            asset.put("edit", Map.of(
                    "transition", i == 0 ? "none" : "quick_cut",
                    "zoom", "none",
                    "position", "center",
                    "crop", "cover"
            ));
            assets.add(asset);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestVersion", "1.0.0");
        manifest.put("taskId", task.getId().toString());
        manifest.put("videoId", videoId.toString());
        manifest.put("videoType", task.getVideoType());
        manifest.put("template", templateForVideoType(task.getVideoType()));
        manifest.put("resolution", "1080x1920");
        manifest.put("fps", 30);
        manifest.put("duration", totalDuration);
        manifest.put("assets", assets);
        manifest.put("subtitleStyle", Map.of(
                "fontSize", 48,
                "position", "bottom_center",
                "maxLines", 2,
                "background", "semi_transparent",
                "safeAreaBottom", 180
        ));
        Map<String, Object> music = new LinkedHashMap<>();
        music.put("type", "default");
        music.put("url", null);
        music.put("volume", 0.35);
        manifest.put("music", music);

        Map<String, Object> voiceover = new LinkedHashMap<>();
        voiceover.put("enabled", false);
        voiceover.put("url", null);
        voiceover.put("volume", 0.8);
        manifest.put("voiceover", voiceover);
        manifest.put("cover", Map.of(
                "text", normalizeCoverText(storyboard != null ? storyboard.getTitle() : null, product),
                "sourceShotNo", orderedClips.get(0).getShotNo()
        ));
        manifest.put("output", Map.of(
                "format", "mp4",
                "codec", "h264",
                "bitrate", "8M"
        ));
        manifest.put("metadata", Map.of(
                "productName", product != null && product.getName() != null ? product.getName() : "",
                "taskMode", task.getTaskMode() != null ? task.getTaskMode() : "",
                "currentVersion", task.getCurrentVersion()
        ));
        return manifest;
    }

    private List<Integer> distributeDurations(int totalDuration, int count) {
        if (count <= 0) {
            return List.of();
        }
        if (totalDuration < count || totalDuration > count * 8) {
            throw new BusinessException("RenderManifest asset durations must be between 1 and 8 seconds each. duration="
                    + totalDuration + ", clipCount=" + count);
        }
        int base = Math.max(1, totalDuration / count);
        int remainder = totalDuration - (base * count);
        List<Integer> durations = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int value = base + (i < remainder ? 1 : 0);
            durations.add(Math.min(8, Math.max(1, value)));
        }
        int sum = durations.stream().mapToInt(Integer::intValue).sum();
        int index = 0;
        while (sum < totalDuration) {
            int current = durations.get(index);
            if (current < 8) {
                durations.set(index, current + 1);
                sum++;
            }
            index = (index + 1) % durations.size();
        }
        while (sum > totalDuration) {
            int current = durations.get(index);
            if (current > 1) {
                durations.set(index, current - 1);
                sum--;
            }
            index = (index + 1) % durations.size();
        }
        return durations;
    }

    private String normalizeSubtitle(String subtitle, int shotNo) {
        String value = StringUtils.hasText(subtitle) ? subtitle.trim() : "Shot " + shotNo;
        return value.length() <= 90 ? value : value.substring(0, 90);
    }

    private String normalizeCoverText(String title, ProductEntity product) {
        String value = StringUtils.hasText(title)
                ? title.trim()
                : product != null && StringUtils.hasText(product.getName())
                ? product.getName().trim()
                : "Generated video";
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private String templateForVideoType(String videoType) {
        return switch (videoType) {
            case "before_after" -> "before_after_v1";
            case "review", "ugc_style" -> "review_v1";
            default -> "pain_point_solution_v1";
        };
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
        Map<String, Object> creativeCtx = creativeContextAssembler.assemble(task);

        if ("asset_analyzing".equals(retryTarget)) {
            List<Map<String, Object>> aiAssets = taskAssetMapper.findByTaskId(task.getId()).stream()
                    .filter(a -> "image".equals(a.getAssetKind()) && a.getUrl() != null && !a.getUrl().isBlank())
                    .map(a -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("assetId", a.getId().toString());
                        item.put("assetKind", a.getAssetKind());
                        item.put("assetRole", a.getAssetRole());
                        item.put("source", a.getSource());
                        item.put("url", a.getUrl());
                        return item;
                    })
                    .collect(Collectors.toList());
            aiServiceClient.startAssetAnalysis(task.getId(), product.getId(), userId, creativeCtx, aiAssets);
            return;
        }

        if ("plan_generating".equals(retryTarget)) {
            aiServiceClient.startCreativePlanGeneration(task.getId(), product.getId(), userId, creativeCtx);
            return;
        }

        if ("storyboard_generating".equals(retryTarget)) {
            if (task.getSelectedPlanId() == null) {
                throw new BusinessException("Task has no selected plan to retry storyboard generation");
            }
            VideoPlanEntity plan = videoPlanMapper.findOwnedPlan(task.getSelectedPlanId(), task.getId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("VideoPlan", task.getSelectedPlanId()));
            aiServiceClient.startStoryboardGeneration(
                    task.getId(), product.getId(), userId,
                    plan.getId(), toSelectedPlanPayload(plan),
                    task.getDuration(), task.getVideoType(), creativeCtx);
            return;
        }

        if ("image_generating".equals(retryTarget)) {
            StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId())
                    .orElseThrow(() -> new BusinessException("Storyboard not found, cannot retry image generation"));
            Map<String, Object> storyboardMap = storyboard.getRawAiOutput();
            if (storyboardMap == null || storyboardMap.isEmpty()) {
                throw new BusinessException("Storyboard raw AI output is empty, cannot retry image generation");
            }
            aiServiceClient.startKeyframeGeneration(task.getId(), product.getId(), userId, storyboardMap);
            return;
        }

        if ("video_clip_generating".equals(retryTarget)) {
            StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId())
                    .orElseThrow(() -> new BusinessException("Storyboard not found, cannot retry video clip generation"));
            Map<String, Object> storyboardMap = storyboard.getRawAiOutput();
            if (storyboardMap == null || storyboardMap.isEmpty()) {
                throw new BusinessException("Storyboard raw AI output is empty, cannot retry video clip generation");
            }
            List<Map<String, Object>> keyframeItems = keyframeMapper
                    .findByTaskIdAndVersion(task.getId(), task.getCurrentVersion()).stream()
                    .filter(k -> "confirmed".equals(k.getStatus()))
                    .map(k -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", k.getId().toString());
                        item.put("shotId", k.getShotId() != null ? k.getShotId().toString() : null);
                        item.put("shotNo", k.getShotNo());
                        item.put("imagePurpose", k.getImagePurpose());
                        item.put("url", k.getUrl());
                        item.put("prompt", k.getPrompt());
                        item.put("source", k.getSource());
                        return item;
                    })
                    .collect(Collectors.toList());
            Map<String, Object> keyframePayload = new LinkedHashMap<>();
            keyframePayload.put("version", task.getCurrentVersion());
            keyframePayload.put("keyframes", keyframeItems);
            aiServiceClient.startVideoClipGeneration(task.getId(), product.getId(), userId, storyboardMap, keyframePayload);
            return;
        }

        if ("repairing".equals(retryTarget)) {
            RepairEventEntity latestRepair = repairEventMapper.findByTaskId(task.getId()).stream()
                    .filter(e -> "in_progress".equals(e.getStatus()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("No in-progress repair event found for retry"));
            aiServiceClient.startRepairWorkflow(
                    task.getId(), product.getId(), userId,
                    latestRepair.getId(), latestRepair.getUserFeedback(),
                    latestRepair.getIssueType(), latestRepair.getTargetType(),
                    Map.of("status", task.getStatus(), "currentVersion", task.getCurrentVersion(),
                            "repairEventId", latestRepair.getId().toString()));
            return;
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
                .selectedPlanTitle(task.getSelectedPlanId() != null
                        ? videoPlanMapper.selectById(task.getSelectedPlanId()).getTitle()
                        : null)
                .assetAnalysis(task.getAssetAnalysis())
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
