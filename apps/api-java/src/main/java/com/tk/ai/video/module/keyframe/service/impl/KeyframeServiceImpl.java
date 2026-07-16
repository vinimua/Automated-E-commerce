package com.tk.ai.video.module.keyframe.service.impl;

import com.tk.ai.video.common.*;
import com.tk.ai.video.module.keyframe.dto.*;
import com.tk.ai.video.module.keyframe.entity.KeyframeEntity;
import com.tk.ai.video.module.keyframe.mapper.KeyframeMapper;
import com.tk.ai.video.module.keyframe.service.KeyframeService;
import com.tk.ai.video.module.storyboard.entity.StoryboardEntity;
import com.tk.ai.video.module.storyboard.entity.StoryboardShotEntity;
import com.tk.ai.video.module.storyboard.mapper.StoryboardMapper;
import com.tk.ai.video.module.storyboard.mapper.StoryboardShotMapper;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyframeServiceImpl implements KeyframeService {

    private final KeyframeMapper keyframeMapper;
    private final VideoTaskMapper videoTaskMapper;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardShotMapper storyboardShotMapper;
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

        StoryboardContext storyboardContext = loadStoryboardContext(task);
        StoryboardShotEntity shot = storyboardContext.shotByNo().get(request.getShotNo());
        if (shot == null) {
            throw new BusinessException("No storyboard shot found for shotNo=" + request.getShotNo());
        }

        KeyframeEntity entity = keyframeMapper
                .findByTaskIdAndShotNoAndVersion(taskId, request.getShotNo(), task.getCurrentVersion())
                .orElseGet(() -> {
                    KeyframeEntity created = new KeyframeEntity();
                    created.setId(UUID.randomUUID());
                    created.setTaskId(taskId);
                    created.setUserId(userId);
                    created.setShotNo(request.getShotNo());
                    created.setVersion(task.getCurrentVersion());
                    return created;
                });
        boolean isNew = entity.getCreatedAt() == null;
        if ("confirmed".equals(entity.getStatus())) {
            throw new BusinessException("Keyframe already confirmed for shotNo=" + request.getShotNo());
        }

        entity.setStoryboardId(storyboardContext.storyboard().getId());
        entity.setShotId(shot.getId());
        entity.setShotNo(request.getShotNo());
        entity.setSource(source);
        entity.setAssetId(request.getAssetId());
        entity.setMaterialId(null);
        entity.setImagePurpose(request.getImagePurpose() != null ? request.getImagePurpose() : "first_frame");
        entity.setUrl(request.getUrl());
        entity.setPrompt(request.getPrompt());
        entity.setNegativePrompt(null);
        entity.setUserInstruction(request.getUserInstruction());
        entity.setProvider(null);
        entity.setModelName(null);
        entity.setErrorMessage(null);
        entity.setStatus(isUserUpload ? "uploaded" : "generating");
        entity.setVersion(task.getCurrentVersion());
        entity.setUpdatedAt(OffsetDateTime.now());
        if (isNew) {
            keyframeMapper.insert(entity);
        } else {
            keyframeMapper.updateById(entity);
        }

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

            Map<String, Object> storyboardMap = buildStoryboardPayload(storyboardContext, Set.of(request.getShotNo()));
            aiServiceClient.startKeyframeGeneration(taskId, task.getProductId(), userId, storyboardMap);
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

    @Override
    @Transactional
    public List<KeyframeResponse> generateKeyframes(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        // Validate: storyboard must be confirmed
        String status = task.getStatus();
        if (!"keyframe_configuring".equals(status) && !"waiting_image_confirmation".equals(status)) {
            throw new BusinessException("Cannot trigger keyframe generation when task status is " + status);
        }

        // Fetch storyboard for AI workflow
        StoryboardContext storyboardContext = loadStoryboardContext(task);
        Map<String, Object> storyboardMap = buildStoryboardPayload(storyboardContext, Set.of());
        if (storyboardMap.isEmpty()) {
            throw new BusinessException("No storyboard found for task, cannot generate keyframes");
        }

        // Create draft keyframe entities for all unconfigured shots
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shots = (List<Map<String, Object>>) storyboardMap.getOrDefault("shots", List.of());
        Set<Integer> targetShotNos = new HashSet<>();
        List<KeyframeEntity> existingKeyframes = keyframeMapper.findByTaskIdAndVersion(taskId, task.getCurrentVersion());
        Map<Integer, KeyframeEntity> existingByShotNo = existingKeyframes.stream()
                .collect(Collectors.toMap(KeyframeEntity::getShotNo, k -> k, (left, right) -> right));
        for (Map<String, Object> shot : shots) {
            Integer shotNo = (Integer) shot.get("shotNo");
            if (shotNo == null) continue;

            KeyframeEntity existing = existingByShotNo.get(shotNo);
            if (existing != null && !shouldBatchGenerate(existing)) {
                continue;
            }
            targetShotNos.add(shotNo);

            StoryboardShotEntity storyboardShot = storyboardContext.shotByNo().get(shotNo);
            KeyframeEntity entity = existing != null ? existing : new KeyframeEntity();
            boolean isNew = entity.getId() == null;
            if (isNew) {
                entity.setId(UUID.randomUUID());
                entity.setTaskId(taskId);
                entity.setUserId(userId);
                entity.setShotNo(shotNo);
                entity.setVersion(task.getCurrentVersion());
            }
            entity.setStoryboardId(storyboardContext.storyboard().getId());
            entity.setShotId(storyboardShot != null ? storyboardShot.getId() : null);
            entity.setShotNo(shotNo);
            entity.setSource("ai_generated");
            entity.setAssetId(null);
            entity.setMaterialId(null);
            entity.setImagePurpose("first_frame");
            entity.setUrl(null);
            entity.setPrompt((String) shot.getOrDefault("prompt", ""));
            entity.setNegativePrompt((String) shot.getOrDefault("negativePrompt", ""));
            entity.setProvider(null);
            entity.setModelName(null);
            entity.setStatus("generating");
            entity.setErrorMessage(null);
            entity.setVersion(task.getCurrentVersion());
            entity.setUpdatedAt(OffsetDateTime.now());
            if (isNew) {
                keyframeMapper.insert(entity);
            } else {
                keyframeMapper.updateById(entity);
            }
        }

        if (targetShotNos.isEmpty()) {
            throw new BusinessException("No unconfigured storyboard shots found for keyframe generation");
        }

        log.info("Created {} draft keyframes for taskId={}", targetShotNos.size(), taskId);

        // Transition task to image_generating
        if ("keyframe_configuring".equals(task.getStatus()) || "waiting_image_confirmation".equals(task.getStatus())) {
            VideoTaskStateMachine.validateTransition(task.getStatus(), "image_generating");
            task.setStatus("image_generating");
            task.setProgress(60);
            task.setUpdatedAt(OffsetDateTime.now());
            videoTaskMapper.updateById(task);
        }

        // Trigger AI workflow
        aiServiceClient.startKeyframeGeneration(taskId, task.getProductId(), userId,
                filterStoryboardPayload(storyboardMap, targetShotNos));

        return getKeyframes(taskId, userId);
    }

    @Override
    @Transactional
    public List<KeyframeResponse> regenerateKeyframe(UUID taskId, UUID keyframeId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        KeyframeEntity keyframe = keyframeMapper.findByIdAndTaskId(keyframeId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Keyframe", keyframeId));

        // Validate: keyframe must be rejected or failed
        if (!"rejected".equals(keyframe.getStatus()) && !"failed".equals(keyframe.getStatus())) {
            throw new BusinessException("Can only regenerate rejected or failed keyframes, current status: " + keyframe.getStatus());
        }

        // Validate: task in correct state
        String status = task.getStatus();
        if (!"keyframe_configuring".equals(status)
                && !"waiting_image_confirmation".equals(status)
                && !"image_generating".equals(status)) {
            throw new BusinessException("Cannot regenerate keyframe when task status is " + status);
        }

        // Reset keyframe to generating
        keyframe.setStatus("generating");
        keyframe.setErrorMessage(null);
        keyframe.setUpdatedAt(OffsetDateTime.now());
        keyframeMapper.updateById(keyframe);

        // Transition task to image_generating if needed
        if ("keyframe_configuring".equals(task.getStatus()) || "waiting_image_confirmation".equals(task.getStatus())) {
            VideoTaskStateMachine.validateTransition(task.getStatus(), "image_generating");
            task.setStatus("image_generating");
            task.setProgress(60);
            task.setUpdatedAt(OffsetDateTime.now());
            videoTaskMapper.updateById(task);
        }

        // Trigger AI workflow for this single shot
        StoryboardContext storyboardContext = loadStoryboardContext(task);
        Map<String, Object> storyboardMap = buildStoryboardPayload(storyboardContext, Set.of(keyframe.getShotNo()));
        aiServiceClient.startKeyframeGeneration(taskId, task.getProductId(), userId, storyboardMap);

        log.info("Keyframe regeneration triggered: taskId={}, keyframeId={}, shotNo={}", taskId, keyframeId, keyframe.getShotNo());
        return getKeyframes(taskId, userId);
    }

    private VideoTaskStatusResponse checkAndAdvanceAfterAllConfirmed(VideoTaskEntity task) {
        if (areAllStoryboardShotsConfirmed(task)) {
            // All keyframes must be explicitly confirmed before video clip generation.
            if ("waiting_image_confirmation".equals(task.getStatus()) || "keyframe_configuring".equals(task.getStatus())) {
                String nextStatus = "video_clip_generating";
                if ("keyframe_configuring".equals(task.getStatus())) {
                    VideoTaskStateMachine.validateTransition(task.getStatus(), "waiting_image_confirmation");
                    task.setStatus("waiting_image_confirmation");
                    task.setUpdatedAt(OffsetDateTime.now());
                    videoTaskMapper.updateById(task);
                }

                VideoTaskStateMachine.validateTransition("waiting_image_confirmation", nextStatus);
                task.setStatus(nextStatus);
                task.setUpdatedAt(OffsetDateTime.now());
                videoTaskMapper.updateById(task);

                aiServiceClient.startVideoClipGeneration(
                        task.getId(),
                        task.getProductId(),
                        task.getUserId(),
                        Map.of(),
                        buildKeyframePayload(task)
                );

                log.info("All keyframes confirmed, advancing: taskId={}, status={}", task.getId(), nextStatus);
                return new VideoTaskStatusResponse(task.getId(), nextStatus, task.getProgress());
            }
        }
        return new VideoTaskStatusResponse(task.getId(), task.getStatus(), task.getProgress());
    }

    private boolean areAllStoryboardShotsConfirmed(VideoTaskEntity task) {
        StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId()).orElse(null);
        if (storyboard == null) {
            log.warn("Cannot advance keyframes: no storyboard found, taskId={}", task.getId());
            return false;
        }

        List<StoryboardShotEntity> shots = storyboardShotMapper.findByStoryboardId(storyboard.getId());
        if (shots.isEmpty()) {
            log.warn("Cannot advance keyframes: storyboard has no shots, taskId={}, storyboardId={}",
                    task.getId(), storyboard.getId());
            return false;
        }

        List<KeyframeEntity> keyframes = keyframeMapper.findByTaskIdAndVersion(task.getId(), task.getCurrentVersion());
        for (StoryboardShotEntity shot : shots) {
            boolean confirmed = keyframes.stream().anyMatch(k ->
                    k.getShotNo() == shot.getShotNo()
                            && "confirmed".equals(k.getStatus())
                            && (k.getShotId() == null || Objects.equals(k.getShotId(), shot.getId()))
            );
            if (!confirmed) {
                log.info("Keyframe confirmation incomplete: taskId={}, missingOrUnconfirmedShotNo={}",
                        task.getId(), shot.getShotNo());
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> buildStoryboardPayload(VideoTaskEntity task) {
        return buildStoryboardPayload(loadStoryboardContext(task), Set.of());
    }

    private StoryboardContext loadStoryboardContext(VideoTaskEntity task) {
        StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId()).orElse(null);
        if (storyboard == null) {
            throw new BusinessException("No storyboard found for task, cannot configure keyframes");
        }

        List<StoryboardShotEntity> shots = storyboardShotMapper.findByStoryboardId(storyboard.getId());
        Map<Integer, StoryboardShotEntity> shotByNo = shots.stream()
                .collect(Collectors.toMap(StoryboardShotEntity::getShotNo, s -> s, (left, right) -> right));
        return new StoryboardContext(storyboard, shots, shotByNo);
    }

    private Map<String, Object> buildStoryboardPayload(StoryboardContext context, Set<Integer> targetShotNos) {
        StoryboardEntity storyboard = context.storyboard();
        List<Map<String, Object>> shotMaps = context.shots().stream()
                .filter(s -> targetShotNos == null || targetShotNos.isEmpty() || targetShotNos.contains(s.getShotNo()))
                .map(s -> {
            Map<String, Object> shot = new LinkedHashMap<>();
            shot.put("shotId", s.getId().toString());
            shot.put("shotNo", s.getShotNo());
            shot.put("duration", s.getDuration());
            shot.put("scene", s.getScene() != null ? s.getScene() : "");
            shot.put("action", s.getAction() != null ? s.getAction() : "");
            shot.put("subtitle", s.getSubtitle() != null ? s.getSubtitle() : "");
            shot.put("materialType", s.getMaterialType() != null ? s.getMaterialType() : "ai_image");
            shot.put("prompt", s.getPrompt() != null ? s.getPrompt() : "");
            shot.put("negativePrompt", s.getNegativePrompt() != null ? s.getNegativePrompt() : "");
            shot.put("editInstruction", s.getEditInstruction() != null ? s.getEditInstruction() : "");
            return shot;
        }).collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", storyboard.getTitle() != null ? storyboard.getTitle() : "");
        payload.put("hook", storyboard.getHook() != null ? storyboard.getHook() : "");
        payload.put("duration", 20); // default, storyboard entity doesn't store duration separately
        payload.put("caption", storyboard.getCaption() != null ? storyboard.getCaption() : "");
        payload.put("hashtags", storyboard.getHashtags() != null ? storyboard.getHashtags() : List.of());
        payload.put("coverText", storyboard.getCoverText() != null ? storyboard.getCoverText() : "");
        payload.put("musicSuggestion", storyboard.getMusicSuggestion() != null ? storyboard.getMusicSuggestion() : "");
        payload.put("shots", shotMaps);
        if (targetShotNos != null && !targetShotNos.isEmpty()) {
            payload.put("targetShotNos", targetShotNos.stream().sorted().collect(Collectors.toList()));
        }
        return payload;
    }

    private boolean shouldBatchGenerate(KeyframeEntity keyframe) {
        return keyframe == null
                || keyframe.getStatus() == null
                || List.of("draft", "rejected", "failed").contains(keyframe.getStatus());
    }

    private record StoryboardContext(
            StoryboardEntity storyboard,
            List<StoryboardShotEntity> shots,
            Map<Integer, StoryboardShotEntity> shotByNo
    ) {
    }

    private Map<String, Object> filterStoryboardPayload(Map<String, Object> storyboardMap, Set<Integer> targetShotNos) {
        if (storyboardMap.isEmpty() || targetShotNos == null || targetShotNos.isEmpty()) {
            return storyboardMap;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shots = (List<Map<String, Object>>) storyboardMap.getOrDefault("shots", List.of());
        List<Map<String, Object>> filteredShots = shots.stream()
                .filter(shot -> targetShotNos.contains((Integer) shot.get("shotNo")))
                .collect(Collectors.toList());
        Map<String, Object> filtered = new LinkedHashMap<>(storyboardMap);
        filtered.put("shots", filteredShots);
        return filtered;
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

    private Map<String, Object> buildKeyframePayload(VideoTaskEntity task) {
        List<Map<String, Object>> keyframes = keyframeMapper
                .findByTaskIdAndVersion(task.getId(), task.getCurrentVersion())
                .stream()
                .filter(k -> "confirmed".equals(k.getStatus()))
                .map(k -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", k.getId().toString());
                    item.put("shotNo", k.getShotNo());
                    item.put("imagePurpose", k.getImagePurpose());
                    item.put("url", k.getUrl());
                    item.put("prompt", k.getPrompt());
                    item.put("source", k.getSource());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", task.getCurrentVersion());
        payload.put("keyframes", keyframes);
        return payload;
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
