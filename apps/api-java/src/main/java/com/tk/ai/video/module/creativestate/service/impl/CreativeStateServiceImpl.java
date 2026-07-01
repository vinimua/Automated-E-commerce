package com.tk.ai.video.module.creativestate.service.impl;

import com.tk.ai.video.common.*;
import com.tk.ai.video.module.creativestate.dto.CreativeStateResponse;
import com.tk.ai.video.module.creativestate.dto.UpdateCreativeStateRequest;
import com.tk.ai.video.module.creativestate.entity.CreativeStateEntity;
import com.tk.ai.video.module.creativestate.mapper.CreativeStateMapper;
import com.tk.ai.video.module.creativestate.service.CreativeStateService;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeStateServiceImpl implements CreativeStateService {

    private final CreativeStateMapper creativeStateMapper;
    private final VideoTaskMapper videoTaskMapper;

    @Override
    public CreativeStateResponse getCreativeState(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);
        CreativeStateEntity state = creativeStateMapper.findByTaskId(taskId)
                .orElseGet(() -> createDefault(taskId, userId));
        return toResponse(state);
    }

    @Override
    @Transactional
    public CreativeStateResponse updateCreativeState(UUID taskId, UpdateCreativeStateRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        CreativeStateEntity state = creativeStateMapper.findByTaskId(taskId)
                .orElseGet(() -> createDefault(taskId, userId));

        if (request.getProduct() != null) state.setProductJson(request.getProduct());
        if (request.getModel() != null) state.setModelJson(request.getModel());
        if (request.getScene() != null) state.setSceneJson(request.getScene());
        if (request.getOutfit() != null) state.setOutfitJson(request.getOutfit());
        if (request.getReferenceVideo() != null) state.setReferenceVideoJson(request.getReferenceVideo());
        if (request.getConstraints() != null) state.setConstraintsJson(request.getConstraints());
        if (request.getUserRequirements() != null) state.setUserRequirementsJson(request.getUserRequirements());

        state.setVersion(state.getVersion() + 1);
        state.setUpdatedAt(OffsetDateTime.now());

        if (state.getId() != null && creativeStateMapper.selectById(state.getId()) != null) {
            creativeStateMapper.updateById(state);
        } else {
            creativeStateMapper.insert(state);
        }

        log.info("CreativeState updated: taskId={}, version={}", taskId, state.getVersion());
        return toResponse(state);
    }

    private CreativeStateEntity createDefault(UUID taskId, UUID userId) {
        CreativeStateEntity state = new CreativeStateEntity();
        state.setId(UUID.randomUUID());
        state.setTaskId(taskId);
        state.setUserId(userId);
        state.setVersion(1);
        creativeStateMapper.insert(state);
        return state;
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

    private CreativeStateResponse toResponse(CreativeStateEntity entity) {
        return CreativeStateResponse.builder()
                .creativeStateId(entity.getId())
                .taskId(entity.getTaskId())
                .product(entity.getProductJson())
                .model(entity.getModelJson())
                .scene(entity.getSceneJson())
                .outfit(entity.getOutfitJson())
                .referenceVideo(entity.getReferenceVideoJson())
                .constraints(entity.getConstraintsJson())
                .userRequirements(entity.getUserRequirementsJson())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
