package com.tk.ai.video.module.videoclip.service.impl;

import com.tk.ai.video.common.*;
import com.tk.ai.video.module.videoclip.dto.*;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoClipServiceImpl implements VideoClipService {

    private final VideoClipMapper videoClipMapper;
    private final VideoTaskMapper videoTaskMapper;

    @Override
    public List<VideoClipResponse> getClips(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);
        return videoClipMapper.findByTaskId(taskId).stream()
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

        if (Boolean.TRUE.equals(request.getConfirmed())) {
            if (!"uploaded".equals(clip.getStatus()) && !"generated".equals(clip.getStatus())) {
                throw new BusinessException("Video clip must be uploaded or generated before confirming, current: " + clip.getStatus());
            }
            clip.setStatus("confirmed");
            clip.setUpdatedAt(OffsetDateTime.now());
            videoClipMapper.updateById(clip);

            log.info("VideoClip confirmed: taskId={}, clipId={}, shotNo={}", taskId, clipId, clip.getShotNo());
            return checkAndAdvanceAfterAllConfirmed(task);
        } else {
            return rejectClip(taskId, clipId, request, userId);
        }
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse rejectClip(UUID taskId, UUID clipId, ConfirmVideoClipRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        VideoClipEntity clip = videoClipMapper.findByIdAndTaskId(clipId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoClip", clipId));

        clip.setStatus("rejected");
        clip.setUpdatedAt(OffsetDateTime.now());
        videoClipMapper.updateById(clip);

        log.info("VideoClip rejected: taskId={}, clipId={}, shotNo={}, feedback={}", taskId, clipId, clip.getShotNo(), request.getFeedback());

        return new VideoTaskStatusResponse(taskId, task.getStatus(), task.getProgress());
    }

    private VideoTaskStatusResponse checkAndAdvanceAfterAllConfirmed(VideoTaskEntity task) {
        long unconfirmed = videoClipMapper.countUnconfirmedByTaskIdAndVersion(task.getId(), task.getCurrentVersion());
        if (unconfirmed == 0) {
            // All clips confirmed — advance to rendering
            String nextStatus = "rendering";
            VideoTaskStateMachine.validateTransition(task.getStatus(), nextStatus);
            task.setStatus(nextStatus);
            task.setProgress(85);
            task.setUpdatedAt(OffsetDateTime.now());
            videoTaskMapper.updateById(task);

            log.info("All video clips confirmed, advancing: taskId={}, status={}", task.getId(), nextStatus);
            return new VideoTaskStatusResponse(task.getId(), nextStatus, task.getProgress());
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
}
