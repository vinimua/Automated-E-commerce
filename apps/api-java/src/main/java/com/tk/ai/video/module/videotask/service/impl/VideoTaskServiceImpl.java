package com.tk.ai.video.module.videotask.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tk.ai.video.common.*;
import com.tk.ai.video.module.product.entity.ProductEntity;
import com.tk.ai.video.module.product.mapper.ProductImageMapper;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.videotask.dto.*;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.enums.VideoType;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import com.tk.ai.video.module.videotask.service.VideoTaskService;
import com.tk.ai.video.module.videotask.state.VideoTaskStateMachine;
import com.tk.ai.video.module.quota.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTaskServiceImpl implements VideoTaskService {

    private final VideoTaskMapper videoTaskMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final QuotaService quotaService;
    private final AiServiceClient aiServiceClient;

    @Override
    @Transactional
    public CreateVideoTaskResponse create(CreateVideoTaskRequest request, UUID userId) {
        // V1 videoType freeze: only 3 types allowed
        if (!VideoType.V1_ALLOWED.contains(request.getVideoType())) {
            throw new BusinessException("V1 only supports: " + String.join(", ", VideoType.V1_ALLOWED));
        }

        // Validate product ownership
        ProductEntity product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new ResourceNotFoundException("Product", request.getProductId());
        }
        if (!product.getUserId().equals(userId)) {
            throw new ResourceForbiddenException("Product does not belong to current user");
        }

        // Pre-deduct 1 video quota
        UUID taskId = UUID.randomUUID();
        String idempotencyKey = "task:" + taskId + ":video:create";
        quotaService.consumeQuota(userId, taskId, "video", 1, idempotencyKey);

        // Insert task
        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(request.getProductId());
        task.setStatus("analyzing");
        task.setProgress(0);
        task.setDuration(request.getDuration());
        task.setVideoType(request.getVideoType());
        task.setNeedSubtitles(request.getNeedSubtitles() != null ? request.getNeedSubtitles() : true);
        task.setNeedVoiceover(request.getNeedVoiceover() != null ? request.getNeedVoiceover() : false);
        task.setManifestVersion("1.0.0");
        task.setSchemaVersion("1.0.0");
        task.setRetryCount(0);
        videoTaskMapper.insert(task);

        log.info("VideoTask created: taskId={}, videoType={}, duration={}s", taskId, request.getVideoType(), request.getDuration());

        // Dispatch to Python AI orchestrator
        List<String> imageUrls = productImageMapper.findByProductId(product.getId()).stream()
                .map(img -> img.getUrl())
                .collect(Collectors.toList());

        aiServiceClient.startProductAnalysis(
                taskId, product.getId(), userId,
                product.getName(), product.getDescription(), product.getProductLink(),
                imageUrls, product.getTargetMarket(), product.getLanguage()
        );

        return new CreateVideoTaskResponse(taskId, "analyzing", 0);
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

        VideoTaskStateMachine.validateTransition(task.getStatus(), "script_generating");

        task.setSelectedPlanId(request.getPlanId());
        task.setStatus("script_generating");
        task.setProgress(30);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        // Dispatch Phase 2 workflow to Python AI
        ProductEntity product = productMapper.selectById(task.getProductId());
        aiServiceClient.startSelectedPlanGeneration(
                taskId, task.getProductId(), userId,
                request.getPlanId(), null,
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

        log.info("Task retry: taskId={}, retryTarget={}, retryCount={}", taskId, retryTarget, task.getRetryCount());

        return new VideoTaskStatusResponse(taskId, retryTarget, 0);
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
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
