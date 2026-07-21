package com.tk.ai.video.module.videoclip.service.impl;

import com.tk.ai.video.common.AiServiceClient;
import com.tk.ai.video.common.BusinessException;
import com.tk.ai.video.common.ResourceForbiddenException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.keyframe.entity.KeyframeEntity;
import com.tk.ai.video.module.keyframe.mapper.KeyframeMapper;
import com.tk.ai.video.module.storyboard.entity.StoryboardEntity;
import com.tk.ai.video.module.storyboard.entity.StoryboardShotEntity;
import com.tk.ai.video.module.storyboard.mapper.StoryboardMapper;
import com.tk.ai.video.module.storyboard.mapper.StoryboardShotMapper;
import com.tk.ai.video.module.videoclip.dto.ConfirmVideoClipRequest;
import com.tk.ai.video.module.videoclip.dto.VideoClipResponse;
import com.tk.ai.video.module.videoclip.entity.VideoClipEntity;
import com.tk.ai.video.module.videoclip.mapper.VideoClipMapper;
import com.tk.ai.video.module.videoclip.service.VideoClipService;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import com.tk.ai.video.module.videotask.state.VideoTaskStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class VideoClipServiceImpl implements VideoClipService {

    private final VideoClipMapper videoClipMapper;
    private final VideoTaskMapper videoTaskMapper;
    private final AiServiceClient aiServiceClient;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardShotMapper storyboardShotMapper;
    private final KeyframeMapper keyframeMapper;

    @Override
    public List<VideoClipResponse> getClips(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);
        return videoClipMapper.findByTaskIdAndVersion(taskId, task.getCurrentVersion()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse confirmClip(UUID taskId, UUID clipId, ConfirmVideoClipRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        VideoClipEntity clip = videoClipMapper.findByIdAndTaskId(clipId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoClip", clipId));
        ensureCurrentVersion(task, clip);

        if (Boolean.TRUE.equals(request.getConfirmed())) {
            if (!"uploaded".equals(clip.getStatus()) && !"generated".equals(clip.getStatus())) {
                throw new BusinessException("Video clip must be uploaded or generated before confirming, current: " + clip.getStatus());
            }
            clip.setStatus("confirmed");
            clip.setUpdatedAt(OffsetDateTime.now());
            videoClipMapper.updateById(clip);

            log.info("VideoClip confirmed: taskId={}, clipId={}, shotNo={}", taskId, clipId, clip.getShotNo());
            return checkAndAdvanceAfterAllConfirmed(task);
        }

        return rejectClip(taskId, clipId, request, userId);
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse rejectClip(UUID taskId, UUID clipId, ConfirmVideoClipRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        VideoClipEntity clip = videoClipMapper.findByIdAndTaskId(clipId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoClip", clipId));
        ensureCurrentVersion(task, clip);

        clip.setStatus("rejected");
        clip.setUpdatedAt(OffsetDateTime.now());
        videoClipMapper.updateById(clip);

        log.info("VideoClip rejected: taskId={}, clipId={}, shotNo={}, feedback={}",
                taskId, clipId, clip.getShotNo(), request.getFeedback());

        return new VideoTaskStatusResponse(taskId, task.getStatus(), task.getProgress());
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse unconfirmClip(UUID taskId, UUID clipId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!"waiting_video_clip_confirmation".equals(task.getStatus())) {
            throw new BusinessException("Cannot unconfirm clips when task status is " + task.getStatus());
        }

        VideoClipEntity clip = videoClipMapper.findByIdAndTaskId(clipId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoClip", clipId));
        ensureCurrentVersion(task, clip);

        if (!"confirmed".equals(clip.getStatus())) {
            throw new BusinessException("Only confirmed clips can be unconfirmed, current: " + clip.getStatus());
        }

        clip.setStatus("generated");
        clip.setUpdatedAt(OffsetDateTime.now());
        videoClipMapper.updateById(clip);

        log.info("VideoClip unconfirmed: taskId={}, clipId={}, shotNo={}", taskId, clipId, clip.getShotNo());
        return new VideoTaskStatusResponse(taskId, task.getStatus(), task.getProgress());
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse generateClips(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!"waiting_image_confirmation".equals(task.getStatus())
                && !"waiting_video_clip_confirmation".equals(task.getStatus())) {
            throw new BusinessException("Cannot generate video clips when task status is " + task.getStatus());
        }

        List<KeyframeEntity> confirmedKeyframes = loadConfirmedKeyframes(task);
        if (confirmedKeyframes.isEmpty()) {
            throw new BusinessException("No confirmed keyframes available for video clip generation.");
        }

        Map<Integer, VideoClipEntity> existingByShotNo = videoClipMapper
                .findByTaskIdAndVersion(taskId, task.getCurrentVersion())
                .stream()
                .collect(Collectors.toMap(VideoClipEntity::getShotNo, c -> c, (left, right) -> right));

        Set<Integer> targetShotNos = confirmedKeyframes.stream()
                .filter(kf -> shouldGenerate(existingByShotNo.get(kf.getShotNo())))
                .map(KeyframeEntity::getShotNo)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        if (targetShotNos.isEmpty()) {
            throw new BusinessException("No missing, rejected, or failed video clips need generation.");
        }

        StoryboardContext storyboardContext = loadStoryboardContext(task);
        upsertGeneratingClips(task, userId, storyboardContext, confirmedKeyframes, existingByShotNo, targetShotNos);

        VideoTaskStateMachine.validateTransition(task.getStatus(), "video_clip_generating");
        task.setStatus("video_clip_generating");
        task.setProgress(75);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        aiServiceClient.startVideoClipGeneration(
                taskId,
                task.getProductId(),
                userId,
                buildStoryboardPayload(storyboardContext, targetShotNos),
                buildKeyframePayload(confirmedKeyframes, targetShotNos, task.getCurrentVersion())
        );

        log.info("Video clip generation triggered: taskId={}, targetShots={}", taskId, targetShotNos);
        return new VideoTaskStatusResponse(taskId, "video_clip_generating", task.getProgress());
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse regenerateClip(UUID taskId, UUID clipId, String prompt, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!"waiting_video_clip_confirmation".equals(task.getStatus())) {
            throw new BusinessException("Can only regenerate clips when task status is waiting_video_clip_confirmation");
        }

        VideoClipEntity clip = videoClipMapper.findByIdAndTaskId(clipId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoClip", clipId));
        ensureCurrentVersion(task, clip);

        if (!"rejected".equals(clip.getStatus()) && !"failed".equals(clip.getStatus())) {
            throw new BusinessException("Only rejected or failed clips can be regenerated, current: " + clip.getStatus());
        }

        List<KeyframeEntity> confirmedKeyframes = loadConfirmedKeyframes(task).stream()
                .filter(k -> k.getShotNo() == clip.getShotNo())
                .toList();
        if (confirmedKeyframes.isEmpty()) {
            throw new BusinessException("No confirmed keyframe found for shotNo=" + clip.getShotNo());
        }

        clip.setStatus("generating");
        clip.setErrorMessage(null);
        if (prompt != null && !prompt.isBlank()) {
            clip.setPrompt(prompt.trim());
        }
        clip.setUpdatedAt(OffsetDateTime.now());
        videoClipMapper.updateById(clip);

        VideoTaskStateMachine.validateTransition(task.getStatus(), "video_clip_generating");
        task.setStatus("video_clip_generating");
        task.setProgress(75);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        StoryboardContext storyboardContext = loadStoryboardContext(task);
        Set<Integer> targetShotNos = Set.of(clip.getShotNo());
        Map<String, Object> storyboardPayload = buildStoryboardPayload(storyboardContext, targetShotNos);
        if (prompt != null && !prompt.isBlank() && storyboardPayload.get("shots") instanceof List<?> shots) {
            for (Object s : shots) {
                if (s instanceof Map<?, ?> shotMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sm = (Map<String, Object>) shotMap;
                    sm.put("prompt", prompt.trim());
                }
            }
        }
        aiServiceClient.startVideoClipGeneration(
                taskId,
                task.getProductId(),
                userId,
                storyboardPayload,
                buildKeyframePayload(confirmedKeyframes, targetShotNos, task.getCurrentVersion())
        );

        log.info("Video clip regeneration triggered: taskId={}, clipId={}, shotNo={}", taskId, clipId, clip.getShotNo());
        return new VideoTaskStatusResponse(taskId, "video_clip_generating", task.getProgress());
    }

    private VideoTaskStatusResponse checkAndAdvanceAfterAllConfirmed(VideoTaskEntity task) {
        if (!areAllConfirmedKeyframesBackedByConfirmedClips(task)) {
            return new VideoTaskStatusResponse(task.getId(), task.getStatus(), task.getProgress());
        }

        String nextStatus = "rendering";
        VideoTaskStateMachine.validateTransition(task.getStatus(), nextStatus);
        task.setStatus(nextStatus);
        task.setProgress(85);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        log.info("All video clips confirmed, advancing: taskId={}, status={}", task.getId(), nextStatus);
        return new VideoTaskStatusResponse(task.getId(), nextStatus, task.getProgress());
    }

    private boolean areAllConfirmedKeyframesBackedByConfirmedClips(VideoTaskEntity task) {
        List<KeyframeEntity> confirmedKeyframes = loadConfirmedKeyframes(task);
        if (confirmedKeyframes.isEmpty()) {
            log.warn("Cannot advance video clips: no confirmed keyframes found, taskId={}", task.getId());
            return false;
        }

        Map<Integer, VideoClipEntity> clipsByShotNo = videoClipMapper
                .findByTaskIdAndVersion(task.getId(), task.getCurrentVersion())
                .stream()
                .collect(Collectors.toMap(VideoClipEntity::getShotNo, c -> c, (left, right) -> right));

        for (KeyframeEntity keyframe : confirmedKeyframes) {
            VideoClipEntity clip = clipsByShotNo.get(keyframe.getShotNo());
            if (clip == null || !"confirmed".equals(clip.getStatus())) {
                log.info("Video clip confirmation incomplete: taskId={}, missingOrUnconfirmedShotNo={}",
                        task.getId(), keyframe.getShotNo());
                return false;
            }
        }
        return true;
    }

    private void upsertGeneratingClips(
            VideoTaskEntity task,
            UUID userId,
            StoryboardContext storyboardContext,
            List<KeyframeEntity> confirmedKeyframes,
            Map<Integer, VideoClipEntity> existingByShotNo,
            Set<Integer> targetShotNos
    ) {
        for (KeyframeEntity kf : confirmedKeyframes) {
            if (!targetShotNos.contains(kf.getShotNo())) {
                continue;
            }

            StoryboardShotEntity shot = storyboardContext.shotByNo().get(kf.getShotNo());
            VideoClipEntity clip = existingByShotNo.getOrDefault(kf.getShotNo(), new VideoClipEntity());
            boolean isNew = clip.getId() == null;
            if (isNew) {
                clip.setId(UUID.randomUUID());
                clip.setTaskId(task.getId());
                clip.setUserId(userId);
                clip.setShotNo(kf.getShotNo());
            }

            clip.setStoryboardId(storyboardContext.storyboard().getId());
            clip.setShotId(shot != null ? shot.getId() : kf.getShotId());
            clip.setKeyframeId(kf.getId());
            clip.setSource("ai_generated");
            clip.setUrl(null);
            clip.setPrompt(kf.getPrompt());
            clip.setNegativePrompt(null);
            clip.setProvider(null);
            clip.setModelName(null);
            clip.setStatus("generating");
            clip.setDuration(shot != null ? shot.getDuration() : clip.getDuration());
            clip.setVersion(task.getCurrentVersion());
            clip.setErrorMessage(null);
            clip.setUpdatedAt(OffsetDateTime.now());

            if (isNew) {
                videoClipMapper.insert(clip);
            } else {
                videoClipMapper.updateById(clip);
            }
        }
    }

    private List<KeyframeEntity> loadConfirmedKeyframes(VideoTaskEntity task) {
        return keyframeMapper.findByTaskIdAndVersion(task.getId(), task.getCurrentVersion()).stream()
                .filter(k -> "confirmed".equals(k.getStatus()))
                .toList();
    }

    private boolean shouldGenerate(VideoClipEntity clip) {
        return clip == null
                || clip.getStatus() == null
                || List.of("draft", "rejected", "failed").contains(clip.getStatus());
    }

    private StoryboardContext loadStoryboardContext(VideoTaskEntity task) {
        StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId()).orElse(null);
        if (storyboard == null) {
            throw new BusinessException("No storyboard found for task, cannot generate video clips");
        }

        List<StoryboardShotEntity> shots = storyboardShotMapper.findByStoryboardId(storyboard.getId());
        Map<Integer, StoryboardShotEntity> shotByNo = shots.stream()
                .collect(Collectors.toMap(StoryboardShotEntity::getShotNo, s -> s, (left, right) -> right));
        return new StoryboardContext(storyboard, shots, shotByNo);
    }

    private Map<String, Object> buildStoryboardPayload(StoryboardContext context, Set<Integer> targetShotNos) {
        List<Map<String, Object>> shots = context.shots().stream()
                .filter(s -> targetShotNos == null || targetShotNos.isEmpty() || targetShotNos.contains(s.getShotNo()))
                .map(s -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("shotId", s.getId().toString());
                    item.put("shotNo", s.getShotNo());
                    item.put("duration", s.getDuration());
                    item.put("scene", s.getScene() != null ? s.getScene() : "");
                    item.put("action", s.getAction() != null ? s.getAction() : "");
                    item.put("subtitle", s.getSubtitle() != null ? s.getSubtitle() : "");
                    item.put("materialType", s.getMaterialType() != null ? s.getMaterialType() : "ai_video");
                    item.put("prompt", s.getPrompt() != null ? s.getPrompt() : "");
                    item.put("negativePrompt", s.getNegativePrompt() != null ? s.getNegativePrompt() : "");
                    item.put("editInstruction", s.getEditInstruction() != null ? s.getEditInstruction() : "");
                    return item;
                })
                .collect(Collectors.toList());

        StoryboardEntity storyboard = context.storyboard();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", storyboard.getTitle() != null ? storyboard.getTitle() : "");
        payload.put("hook", storyboard.getHook() != null ? storyboard.getHook() : "");
        payload.put("caption", storyboard.getCaption() != null ? storyboard.getCaption() : "");
        payload.put("hashtags", storyboard.getHashtags() != null ? storyboard.getHashtags() : List.of());
        payload.put("coverText", storyboard.getCoverText() != null ? storyboard.getCoverText() : "");
        payload.put("musicSuggestion", storyboard.getMusicSuggestion() != null ? storyboard.getMusicSuggestion() : "");
        payload.put("shots", shots);
        if (targetShotNos != null && !targetShotNos.isEmpty()) {
            payload.put("targetShotNos", targetShotNos.stream().sorted().collect(Collectors.toList()));
        }
        return payload;
    }

    private Map<String, Object> buildKeyframePayload(List<KeyframeEntity> keyframes, Set<Integer> targetShotNos, int version) {
        List<Map<String, Object>> items = keyframes.stream()
                .filter(k -> targetShotNos == null || targetShotNos.isEmpty() || targetShotNos.contains(k.getShotNo()))
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", version);
        payload.put("keyframes", items);
        if (targetShotNos != null && !targetShotNos.isEmpty()) {
            payload.put("targetShotNos", targetShotNos.stream().sorted().collect(Collectors.toList()));
        }
        return payload;
    }

    private void ensureCurrentVersion(VideoTaskEntity task, VideoClipEntity clip) {
        if (clip.getVersion() != task.getCurrentVersion()) {
            throw new BusinessException("Video clip is not in current task version.");
        }
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

    private VideoClipResponse toResponse(VideoClipEntity entity) {
        return VideoClipResponse.builder()
                .clipId(entity.getId())
                .taskId(entity.getTaskId())
                .shotId(entity.getShotId())
                .keyframeId(entity.getKeyframeId())
                .shotNo(entity.getShotNo())
                .source(entity.getSource())
                .url(entity.getUrl())
                .prompt(entity.getPrompt())
                .provider(entity.getProvider())
                .modelName(entity.getModelName())
                .status(entity.getStatus())
                .duration(entity.getDuration())
                .version(entity.getVersion())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private record StoryboardContext(
            StoryboardEntity storyboard,
            List<StoryboardShotEntity> shots,
            Map<Integer, StoryboardShotEntity> shotByNo
    ) {
    }
}
