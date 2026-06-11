package com.tk.ai.video.module.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tk.ai.video.common.*;
import com.tk.ai.video.module.quota.service.QuotaService;
import com.tk.ai.video.module.video.dto.*;
import com.tk.ai.video.module.video.entity.VideoEntity;
import com.tk.ai.video.module.video.mapper.VideoMapper;
import com.tk.ai.video.module.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoMapper videoMapper;
    private final QuotaService quotaService;

    @Override
    public PageResult<VideoResponse> list(String status, UUID productId, int page, int pageSize, UUID userId) {
        Page<VideoEntity> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<VideoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoEntity::getUserId, userId);
        if (StringUtils.hasText(status)) wrapper.eq(VideoEntity::getStatus, status);
        if (productId != null) wrapper.eq(VideoEntity::getProductId, productId);
        wrapper.orderByDesc(VideoEntity::getCreatedAt);

        Page<VideoEntity> result = videoMapper.selectPage(p, wrapper);
        List<VideoResponse> items = result.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
        return new PageResult<>(items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal(), (int) result.getPages());
    }

    @Override
    public VideoResponse getById(UUID videoId, UUID userId) {
        VideoEntity video = videoMapper.selectById(videoId);
        if (video == null) throw new ResourceNotFoundException("Video", videoId);
        if (!video.getUserId().equals(userId)) throw new ResourceForbiddenException("Video not owned by user");
        return toResponse(video);
    }

    @Override
    @Transactional
    public VideoExportResponse export(UUID videoId, UUID userId) {
        VideoEntity video = videoMapper.selectById(videoId);
        if (video == null) throw new ResourceNotFoundException("Video", videoId);
        if (!video.getUserId().equals(userId)) throw new ResourceForbiddenException("Video not owned by user");
        if (!"completed".equals(video.getStatus())) throw new BusinessException("Only completed videos can be exported");

        // Idempotent export quota deduction
        String idempotencyKey = "video:" + videoId + ":export";
        quotaService.consumeQuota(userId, video.getTaskId(), "export", 1, idempotencyKey);

        video.setStatus("exported");
        video.setExportedAt(OffsetDateTime.now());
        videoMapper.updateById(video);

        String downloadUrl = video.getVideoUrl(); // Phase 5: generate COS presigned download URL
        return new VideoExportResponse(videoId, "exported", downloadUrl);
    }

    private VideoResponse toResponse(VideoEntity v) {
        VideoResponse rsp = new VideoResponse();
        rsp.setVideoId(v.getId());
        rsp.setTaskId(v.getTaskId());
        rsp.setProductId(v.getProductId());
        rsp.setTitle(v.getTitle());
        rsp.setVideoUrl(v.getVideoUrl());
        rsp.setCoverUrl(v.getCoverUrl());
        rsp.setDuration(v.getDuration());
        rsp.setResolution(v.getResolution());
        rsp.setQualityScore(v.getQualityScore());
        rsp.setRiskScore(v.getRiskScore());
        rsp.setCaption(v.getCaption());
        rsp.setHashtags(v.getHashtags());
        rsp.setStatus(v.getStatus());
        return rsp;
    }
}
