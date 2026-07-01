package com.tk.ai.video.module.repairevent.service.impl;

import com.tk.ai.video.common.*;
import com.tk.ai.video.module.repairevent.dto.FeedbackRequest;
import com.tk.ai.video.module.repairevent.dto.RepairEventResponse;
import com.tk.ai.video.module.repairevent.entity.RepairEventEntity;
import com.tk.ai.video.module.repairevent.mapper.RepairEventMapper;
import com.tk.ai.video.module.repairevent.service.RepairEventService;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepairEventServiceImpl implements RepairEventService {

    private final RepairEventMapper repairEventMapper;
    private final VideoTaskMapper videoTaskMapper;

    @Override
    public List<RepairEventResponse> getRepairEvents(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);
        return repairEventMapper.findByTaskId(taskId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RepairEventResponse createRepairEvent(UUID taskId, FeedbackRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        RepairEventEntity entity = new RepairEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(taskId);
        entity.setUserId(userId);
        entity.setTargetType(request.getTargetType());
        entity.setTargetId(request.getTargetId());
        entity.setUserFeedback(request.getFeedbackText());
        entity.setIssueType(request.getCategory());
        entity.setBeforeVersion(task.getCurrentVersion());
        entity.setStatus("created");
        repairEventMapper.insert(entity);

        log.info("RepairEvent created: taskId={}, eventId={}, targetType={}", taskId, entity.getId(), request.getTargetType());
        return toResponse(entity);
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

    private RepairEventResponse toResponse(RepairEventEntity entity) {
        return RepairEventResponse.builder()
                .repairEventId(entity.getId())
                .taskId(entity.getTaskId())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .userFeedback(entity.getUserFeedback())
                .issueType(entity.getIssueType())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
