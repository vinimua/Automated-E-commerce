package com.tk.ai.video.module.storyboard.service.impl;

import com.tk.ai.video.common.ResourceForbiddenException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.storyboard.dto.*;
import com.tk.ai.video.module.storyboard.entity.*;
import com.tk.ai.video.module.storyboard.mapper.*;
import com.tk.ai.video.module.storyboard.service.StoryboardService;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryboardServiceImpl implements StoryboardService {

    private final VideoTaskMapper videoTaskMapper;
    private final VideoPlanMapper videoPlanMapper;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardShotMapper storyboardShotMapper;

    @Override
    public List<VideoPlanResponse> getPlans(UUID taskId, UUID userId) {
        VideoTaskEntity task = videoTaskMapper.selectById(taskId);
        if (task == null) throw new ResourceNotFoundException("VideoTask", taskId);
        if (!task.getUserId().equals(userId)) throw new ResourceForbiddenException("Task not owned by user");

        return videoPlanMapper.findByTaskId(taskId).stream()
                .map(this::toPlanResponse)
                .collect(Collectors.toList());
    }

    @Override
    public StoryboardResponse getStoryboard(UUID taskId, UUID userId) {
        VideoTaskEntity task = videoTaskMapper.selectById(taskId);
        if (task == null) throw new ResourceNotFoundException("VideoTask", taskId);
        if (!task.getUserId().equals(userId)) throw new ResourceForbiddenException("Task not owned by user");

        StoryboardEntity storyboard = storyboardMapper.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Storyboard not found for task", taskId));
        return toStoryboardResponse(storyboard);
    }

    @Override
    @Transactional
    public StoryboardResponse updateStoryboard(UUID storyboardId, UpdateStoryboardRequest request, UUID userId) {
        StoryboardEntity storyboard = storyboardMapper.selectById(storyboardId);
        if (storyboard == null) throw new ResourceNotFoundException("Storyboard", storyboardId);
        if (!storyboard.getUserId().equals(userId)) throw new ResourceForbiddenException("Storyboard not owned by user");

        if (request.getTitle() != null) storyboard.setTitle(request.getTitle());
        if (request.getHook() != null) storyboard.setHook(request.getHook());
        if (request.getCoverText() != null) storyboard.setCoverText(request.getCoverText());
        if (request.getCaption() != null) storyboard.setCaption(request.getCaption());
        if (request.getHashtags() != null) storyboard.setHashtags(request.getHashtags());
        storyboardMapper.updateById(storyboard);

        // Update shots if provided
        if (request.getShots() != null) {
            storyboardShotMapper.deleteByStoryboardId(storyboardId);
            for (StoryboardShotDto shotDto : request.getShots()) {
                StoryboardShotEntity shot = new StoryboardShotEntity();
                shot.setId(UUID.randomUUID());
                shot.setStoryboardId(storyboardId);
                shot.setTaskId(storyboard.getTaskId());
                shot.setUserId(userId);
                shot.setShotNo(shotDto.getShotNo());
                shot.setDuration(shotDto.getDuration());
                shot.setScene(shotDto.getScene());
                shot.setAction(shotDto.getAction());
                shot.setSubtitle(shotDto.getSubtitle());
                shot.setMaterialType(shotDto.getMaterialType());
                shot.setPrompt(shotDto.getPrompt());
                shot.setEditInstruction(shotDto.getEditInstruction());
                storyboardShotMapper.insert(shot);
            }
        }

        return toStoryboardResponse(storyboard);
    }

    //把 VideoPlanEntity（数据库记录）转成 VideoPlanResponse（返回给前端的 DTO）。
    private VideoPlanResponse toPlanResponse(VideoPlanEntity plan) {
        VideoPlanResponse rsp = new VideoPlanResponse();
        rsp.setPlanId(plan.getId());
        rsp.setType(plan.getType());
        rsp.setTitle(plan.getTitle());
        rsp.setHook(plan.getHook());
        rsp.setStructure(plan.getStructure());
        rsp.setReason(plan.getReason());
        rsp.setEstimatedDuration(plan.getEstimatedDuration());
        rsp.setScore(plan.getScore());
        return rsp;
    }
//把 StoryboardEntity + 关联的镜头列表 StoryboardShotEntity 一起转成 StoryboardResponse。
    private StoryboardResponse toStoryboardResponse(StoryboardEntity storyboard) {
        List<StoryboardShotEntity> shots = storyboardShotMapper.findByStoryboardId(storyboard.getId());

        StoryboardResponse rsp = new StoryboardResponse();
        rsp.setStoryboardId(storyboard.getId());
        rsp.setTitle(storyboard.getTitle());
        rsp.setHook(storyboard.getHook());
        rsp.setScript(storyboard.getScript());
        rsp.setCoverText(storyboard.getCoverText());
        rsp.setCaption(storyboard.getCaption());
        rsp.setHashtags(storyboard.getHashtags());
        rsp.setMusicSuggestion(storyboard.getMusicSuggestion());
        rsp.setShots(shots.stream().map(s -> {
            StoryboardShotDto dto = new StoryboardShotDto();
            dto.setShotNo(s.getShotNo());
            dto.setDuration(s.getDuration());
            dto.setScene(s.getScene());
            dto.setAction(s.getAction());
            dto.setSubtitle(s.getSubtitle());
            dto.setMaterialType(s.getMaterialType());
            dto.setPrompt(s.getPrompt());
            dto.setEditInstruction(s.getEditInstruction());
            return dto;
        }).collect(Collectors.toList()));
        return rsp;
    }
}
