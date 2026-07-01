package com.tk.ai.video.module.taskasset.service.impl;

import com.tk.ai.video.common.*;
import com.tk.ai.video.module.taskasset.dto.*;
import com.tk.ai.video.module.taskasset.entity.TaskAssetEntity;
import com.tk.ai.video.module.taskasset.mapper.TaskAssetMapper;
import com.tk.ai.video.module.taskasset.service.TaskAssetService;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import com.tk.ai.video.module.videotask.state.VideoTaskStateMachine;
import com.tk.ai.video.common.AiServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssetServiceImpl implements TaskAssetService {

    private static final Set<String> ASSET_UPLOADABLE_STATUSES = Set.of(
            "asset_uploading", "waiting_asset_confirmation", "keyframe_configuring"
    );

    private final TaskAssetMapper taskAssetMapper;
    private final VideoTaskMapper videoTaskMapper;
    private final AiServiceClient aiServiceClient;

    @Override
    public List<TaskAssetResponse> getAssets(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);
        return taskAssetMapper.findByTaskId(taskId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<TaskAssetResponse> addAsset(UUID taskId, CreateAssetRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!ASSET_UPLOADABLE_STATUSES.contains(task.getStatus())) {
            throw new BusinessException("Cannot add assets when task status is " + task.getStatus());
        }

        TaskAssetEntity entity = new TaskAssetEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(taskId);
        entity.setUserId(userId);
        entity.setProductId(task.getProductId());
        entity.setAssetKind(request.getAssetKind());
        entity.setAssetRole(request.getAssetRole());
        entity.setSource(request.getSource());
        entity.setUrl(request.getUrl());
        entity.setFileName(request.getFileName());
        entity.setMimeType(request.getMimeType());
        entity.setSizeBytes(request.getSizeBytes());
        entity.setDescription(request.getDescription());
        entity.setConfirmed(false);
        entity.setMetadata(request.getMetadata());
        taskAssetMapper.insert(entity);

        log.info("Asset added: taskId={}, assetId={}, role={}", taskId, entity.getId(), entity.getAssetRole());
        return getAssets(taskId, userId);
    }

    @Override
    @Transactional
    public List<TaskAssetResponse> updateAssetRole(UUID taskId, UUID assetId, UpdateAssetRoleRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        TaskAssetEntity asset = taskAssetMapper.findByIdAndTaskId(assetId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskAsset", assetId));

        asset.setAssetRole(request.getAssetRole());
        asset.setUpdatedAt(OffsetDateTime.now());
        taskAssetMapper.updateById(asset);

        log.info("Asset role updated: taskId={}, assetId={}, role={}", taskId, assetId, request.getAssetRole());
        return getAssets(taskId, userId);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse confirmAssets(UUID taskId, ConfirmAssetsRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        List<TaskAssetEntity> assets = taskAssetMapper.findByTaskId(taskId);
        if (assets.isEmpty()) {
            throw new BusinessException("At least one asset is required before confirmation.");
        }
        markRequestedAssetsConfirmed(request, assets);

        if ("asset_uploading".equals(task.getStatus())) {
            VideoTaskStateMachine.validateTransition(task.getStatus(), "asset_analyzing");
            task.setStatus("asset_analyzing");
            task.setProgress(15);
            task.setUpdatedAt(OffsetDateTime.now());
            videoTaskMapper.updateById(task);

            aiServiceClient.startAssetAnalysis(
                    task.getId(),
                    task.getProductId(),
                    task.getUserId(),
                    Map.of("assetCount", assets.size())
            );

            log.info("Assets uploaded, starting asset analysis: taskId={}", taskId);
            return new VideoTaskStatusResponse(taskId, "asset_analyzing", task.getProgress());
        }

        if (!"waiting_asset_confirmation".equals(task.getStatus())) {
            throw new BusinessException("Cannot confirm analyzed assets when task status is " + task.getStatus());
        }

        // Determine next state: reference_analyzing (if reference video present) or plan_generating.
        String nextStatus = determineNextStatus(task);
        VideoTaskStateMachine.validateTransition(task.getStatus(), nextStatus);
        task.setStatus(nextStatus);
        task.setProgress("reference_analyzing".equals(nextStatus) ? 25 : 30);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        if ("reference_analyzing".equals(nextStatus)) {
            aiServiceClient.startReferenceAnalysis(task.getId(), task.getProductId(), task.getUserId(), Map.of());
        } else if ("plan_generating".equals(nextStatus)) {
            aiServiceClient.startCreativePlanGeneration(task.getId(), task.getProductId(), task.getUserId(), Map.of());
        }

        log.info("Assets confirmed: taskId={}, newStatus={}", taskId, nextStatus);
        return new VideoTaskStatusResponse(taskId, nextStatus, task.getProgress());
    }

    private void markRequestedAssetsConfirmed(ConfirmAssetsRequest request, List<TaskAssetEntity> assets) {
        if (request != null && request.getAssetIds() != null && !request.getAssetIds().isEmpty()) {
            Set<UUID> idsToConfirm = Set.copyOf(request.getAssetIds());
            for (TaskAssetEntity asset : assets) {
                if (idsToConfirm.contains(asset.getId())) {
                    asset.setConfirmed(true);
                    asset.setUpdatedAt(OffsetDateTime.now());
                    taskAssetMapper.updateById(asset);
                }
            }
            return;
        }

        for (TaskAssetEntity asset : assets) {
            asset.setConfirmed(true);
            asset.setUpdatedAt(OffsetDateTime.now());
            taskAssetMapper.updateById(asset);
        }
    }

    private String determineNextStatus(VideoTaskEntity task) {
        // Check if there are any reference video assets
        List<TaskAssetEntity> refVideos = taskAssetMapper.findByTaskId(task.getId()).stream()
                .filter(a -> "reference_video".equals(a.getAssetRole()))
                .collect(Collectors.toList());
        if (!refVideos.isEmpty()) {
            return "reference_analyzing";
        }
        return "plan_generating";
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

    private TaskAssetResponse toResponse(TaskAssetEntity entity) {
        return TaskAssetResponse.builder()
                .assetId(entity.getId())
                .taskId(entity.getTaskId())
                .productId(entity.getProductId())
                .assetKind(entity.getAssetKind())
                .assetRole(entity.getAssetRole())
                .source(entity.getSource())
                .url(entity.getUrl())
                .fileName(entity.getFileName())
                .mimeType(entity.getMimeType())
                .sizeBytes(entity.getSizeBytes())
                .description(entity.getDescription())
                .confirmed(entity.getConfirmed())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
