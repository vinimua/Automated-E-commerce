package com.tk.ai.video.module.keyframe.service.impl;

import com.tk.ai.video.common.*;
import com.tk.ai.video.module.keyframe.dto.*;
import com.tk.ai.video.module.keyframe.entity.KeyframeEntity;
import com.tk.ai.video.module.keyframe.mapper.KeyframeMapper;
import com.tk.ai.video.module.keyframe.service.KeyframeService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyframeServiceImpl implements KeyframeService {

    private final KeyframeMapper keyframeMapper;
    private final VideoTaskMapper videoTaskMapper;
    private final AiServiceClient aiServiceClient;

    @Override
    public List<KeyframeResponse> getKeyframes(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);
        return keyframeMapper.findByTaskId(taskId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<KeyframeResponse> addKeyframe(UUID taskId, CreateKeyframeRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        // Must be in keyframe_configuring state
        if (!"keyframe_configuring".equals(task.getStatus()) && !"waiting_image_confirmation".equals(task.getStatus())) {
            throw new BusinessException("Cannot add keyframes when task status is " + task.getStatus());
        }

        // Determine source: if url provided with no AI prompt, treat as user_upload
        String source = request.getSource();
        if (source == null) {
            source = "user_upload";
        }
        boolean isUserUpload = "user_upload".equals(source);

        KeyframeEntity entity = new KeyframeEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(taskId);
        entity.setUserId(userId);
        entity.setShotNo(request.getShotNo());
        entity.setSource(source);
        entity.setAssetId(request.getAssetId());
        entity.setImagePurpose(request.getImagePurpose() != null ? request.getImagePurpose() : "first_frame");
        entity.setUrl(request.getUrl());
        entity.setPrompt(request.getPrompt());
        entity.setUserInstruction(request.getUserInstruction());
        entity.setStatus(isUserUpload ? "uploaded" : "draft");
        entity.setVersion(task.getCurrentVersion());
        keyframeMapper.insert(entity);

        // User-uploaded keyframes do NOT consume image quota.
        // AI-generated keyframes consume quota when the callback confirms success (in AiCallbackServiceImpl).
        if (isUserUpload && "keyframe_configuring".equals(task.getStatus())) {
            VideoTaskStateMachine.validateTransition(task.getStatus(), "waiting_image_confirmation");
            task.setStatus("waiting_image_confirmation");
            task.setUpdatedAt(OffsetDateTime.now());
            videoTaskMapper.updateById(task);
        } else if ("ai_generated".equals(source)) {
            if ("keyframe_configuring".equals(task.getStatus())) {
                VideoTaskStateMachine.validateTransition(task.getStatus(), "image_generating");
                task.setStatus("image_generating");
                task.setProgress(60);
                task.setUpdatedAt(OffsetDateTime.now());
                videoTaskMapper.updateById(task);
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("keyframeId", entity.getId().toString());
            params.put("shotNo", entity.getShotNo());
            params.put("imagePurpose", entity.getImagePurpose());
            params.put("prompt", entity.getPrompt() != null ? entity.getPrompt() : "");
            params.put("userInstruction", entity.getUserInstruction() != null ? entity.getUserInstruction() : "");
            aiServiceClient.startKeyframeGeneration(taskId, task.getProductId(), userId, params);
        }

        log.info("Keyframe added: taskId={}, keyframeId={}, shotNo={}, source={}", taskId, entity.getId(), request.getShotNo(), source);
        return getKeyframes(taskId, userId);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse confirmKeyframe(UUID taskId, UUID keyframeId, ConfirmKeyframeRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        KeyframeEntity keyframe = keyframeMapper.findByIdAndTaskId(keyframeId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Keyframe", keyframeId));

        if (Boolean.TRUE.equals(request.getConfirmed())) {
            if (!"uploaded".equals(keyframe.getStatus()) && !"generated".equals(keyframe.getStatus())) {
                throw new BusinessException("Keyframe must be uploaded or generated before confirming, current: " + keyframe.getStatus());
            }
            keyframe.setStatus("confirmed");
            keyframe.setUpdatedAt(OffsetDateTime.now());
            keyframeMapper.updateById(keyframe);

            log.info("Keyframe confirmed: taskId={}, keyframeId={}, shotNo={}", taskId, keyframeId, keyframe.getShotNo());
            return checkAndAdvanceAfterAllConfirmed(task);
        } else {
            return rejectKeyframe(taskId, keyframeId, request, userId);
        }
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse rejectKeyframe(UUID taskId, UUID keyframeId, ConfirmKeyframeRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        KeyframeEntity keyframe = keyframeMapper.findByIdAndTaskId(keyframeId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Keyframe", keyframeId));

        keyframe.setStatus("rejected");
        keyframe.setUpdatedAt(OffsetDateTime.now());
        keyframeMapper.updateById(keyframe);

        log.info("Keyframe rejected: taskId={}, keyframeId={}, shotNo={}, feedback={}", taskId, keyframeId, keyframe.getShotNo(), request.getFeedback());

        // Stay in current state, user can re-upload or re-generate
        return new VideoTaskStatusResponse(taskId, task.getStatus(), task.getProgress());
    }

    private VideoTaskStatusResponse checkAndAdvanceAfterAllConfirmed(VideoTaskEntity task) {
        long unconfirmed = keyframeMapper.countUnconfirmedByTaskIdAndVersion(task.getId(), task.getCurrentVersion());
        if (unconfirmed == 0) {
            // All keyframes must be explicitly confirmed before video clip generation.
            if ("waiting_image_confirmation".equals(task.getStatus()) || "keyframe_configuring".equals(task.getStatus())) {
                String nextStatus = "video_clip_generating";
                VideoTaskStateMachine.validateTransition(task.getStatus(), "waiting_image_confirmation");
                task.setStatus("waiting_image_confirmation");
                task.setUpdatedAt(OffsetDateTime.now());
                videoTaskMapper.updateById(task);

                VideoTaskStateMachine.validateTransition("waiting_image_confirmation", nextStatus);
                task.setStatus(nextStatus);
                task.setUpdatedAt(OffsetDateTime.now());
                videoTaskMapper.updateById(task);

                log.info("All keyframes confirmed, advancing: taskId={}, status={}", task.getId(), nextStatus);
                return new VideoTaskStatusResponse(task.getId(), nextStatus, task.getProgress());
            }
        }
        return new VideoTaskStatusResponse(task.getId(), task.getStatus(), task.getProgress());
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

    private KeyframeResponse toResponse(KeyframeEntity entity) {
        return KeyframeResponse.builder()
                .keyframeId(entity.getId())
                .taskId(entity.getTaskId())
                .shotId(entity.getShotId())
                .shotNo(entity.getShotNo())
                .source(entity.getSource())
                .assetId(entity.getAssetId())
                .imagePurpose(entity.getImagePurpose())
                .url(entity.getUrl())
                .prompt(entity.getPrompt())
                .userInstruction(entity.getUserInstruction())
                .provider(entity.getProvider())
                .modelName(entity.getModelName())
                .status(entity.getStatus())
                .version(entity.getVersion())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
