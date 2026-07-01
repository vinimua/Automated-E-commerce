package com.tk.ai.video.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.admin.dto.AdminQuotaItemResponse;
import com.tk.ai.video.module.admin.dto.AdminQuotaUpdateRequest;
import com.tk.ai.video.module.auth.entity.UserEntity;
import com.tk.ai.video.module.auth.mapper.UserMapper;
import com.tk.ai.video.module.log.entity.ModelLogEntity;
import com.tk.ai.video.module.log.entity.RenderLogEntity;
import com.tk.ai.video.module.log.mapper.ModelLogMapper;
import com.tk.ai.video.module.log.mapper.RenderLogMapper;
import com.tk.ai.video.module.quota.entity.UserQuotaEntity;
import com.tk.ai.video.module.quota.mapper.UserQuotaMapper;
import com.tk.ai.video.module.user.dto.UserInfoResponse;
import com.tk.ai.video.module.video.entity.VideoEntity;
import com.tk.ai.video.module.video.mapper.VideoMapper;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final VideoTaskMapper videoTaskMapper;
    private final ModelLogMapper modelLogMapper;
    private final RenderLogMapper renderLogMapper;
    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final UserQuotaMapper userQuotaMapper;

    @GetMapping("/video-tasks")
    public ApiResponse<PageResult<VideoTaskEntity>> listVideoTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Page<VideoTaskEntity> p = new Page<>(page, pageSize);
        Page<VideoTaskEntity> result = videoTaskMapper.selectPage(p, null);
        return ApiResponse.ok(PageResult.from(result));
    }

    @GetMapping("/model-logs")
    public ApiResponse<PageResult<ModelLogEntity>> listModelLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Page<ModelLogEntity> p = new Page<>(page, pageSize);
        Page<ModelLogEntity> result = modelLogMapper.selectPage(p,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelLogEntity>()
                        .orderByDesc(ModelLogEntity::getCreatedAt));
        return ApiResponse.ok(PageResult.from(result));
    }

    @GetMapping("/render-logs")
    public ApiResponse<PageResult<RenderLogEntity>> listRenderLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Page<RenderLogEntity> p = new Page<>(page, pageSize);
        Page<RenderLogEntity> result = renderLogMapper.selectPage(p,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RenderLogEntity>()
                        .orderByDesc(RenderLogEntity::getCreatedAt));
        return ApiResponse.ok(PageResult.from(result));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserInfoResponse>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status
    ) {
        LambdaQueryWrapper<UserEntity> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(UserEntity::getStatus, status);
        }
        qw.orderByDesc(UserEntity::getCreatedAt);
        Page<UserEntity> p = new Page<>(page, pageSize);
        Page<UserEntity> result = userMapper.selectPage(p, qw);
        List<UserInfoResponse> items = result.getRecords().stream().map(u -> {
            UserInfoResponse dto = new UserInfoResponse();
            dto.setId(u.getId());
            dto.setEmail(u.getEmail());
            dto.setRole(u.getRole());
            dto.setStatus(u.getStatus());
            dto.setCreatedAt(u.getCreatedAt());
            return dto;
        }).toList();
        PageResult<UserInfoResponse> pageResult = new PageResult<>(
                items, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal(), (int) result.getPages());
        return ApiResponse.ok(pageResult);
    }

    @GetMapping("/videos")
    public ApiResponse<PageResult<VideoEntity>> listVideos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID productId
    ) {
        LambdaQueryWrapper<VideoEntity> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(VideoEntity::getStatus, status);
        }
        if (productId != null) {
            qw.eq(VideoEntity::getProductId, productId);
        }
        qw.orderByDesc(VideoEntity::getCreatedAt);
        Page<VideoEntity> p = new Page<>(page, pageSize);
        Page<VideoEntity> result = videoMapper.selectPage(p, qw);
        return ApiResponse.ok(PageResult.from(result));
    }

    @GetMapping("/quotas")
    public ApiResponse<PageResult<AdminQuotaItemResponse>> listQuotas(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status
    ) {
        LambdaQueryWrapper<UserEntity> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(UserEntity::getStatus, status);
        }
        qw.orderByDesc(UserEntity::getCreatedAt);
        Page<UserEntity> p = new Page<>(page, pageSize);
        Page<UserEntity> result = userMapper.selectPage(p, qw);
        List<AdminQuotaItemResponse> items = result.getRecords().stream()
                .map(user -> toQuotaItem(user, userQuotaMapper.findByUserId(user.getId()).orElse(null)))
                .toList();
        PageResult<AdminQuotaItemResponse> pageResult = new PageResult<>(
                items, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal(), (int) result.getPages());
        return ApiResponse.ok(pageResult);
    }

    @PatchMapping("/quotas/{userId}")
    @Transactional
    public ApiResponse<AdminQuotaItemResponse> updateQuota(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminQuotaUpdateRequest request
    ) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User", userId);
        }

        UserQuotaEntity quota = userQuotaMapper.selectByUserIdForUpdate(userId)
                .orElseGet(() -> createDefaultQuota(userId));

        if (request.getVideoQuota() != null) {
            quota.setVideoQuota(request.getVideoQuota());
        }
        if (request.getImageQuota() != null) {
            quota.setImageQuota(request.getImageQuota());
        }
        if (request.getVideoClipQuota() != null) {
            quota.setVideoClipQuota(request.getVideoClipQuota());
        }
        if (request.getExportQuota() != null) {
            quota.setExportQuota(request.getExportQuota());
        }
        quota.setUpdatedAt(OffsetDateTime.now());
        userQuotaMapper.updateById(quota);

        return ApiResponse.ok(toQuotaItem(user, quota));
    }

    private UserQuotaEntity createDefaultQuota(UUID userId) {
        UserQuotaEntity quota = new UserQuotaEntity();
        quota.setId(UUID.randomUUID());
        quota.setUserId(userId);
        quota.setVideoQuota(10);
        quota.setImageQuota(50);
        quota.setVideoClipQuota(10);
        quota.setExportQuota(10);
        quota.setUsedVideoCount(0);
        quota.setUsedImageCount(0);
        quota.setUsedVideoClipCount(0);
        quota.setUsedExportCount(0);
        quota.setQuotaDate(LocalDate.now());
        userQuotaMapper.insert(quota);
        return quota;
    }

    private AdminQuotaItemResponse toQuotaItem(UserEntity user, UserQuotaEntity quota) {
        AdminQuotaItemResponse dto = new AdminQuotaItemResponse();
        dto.setUserId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());

        if (quota != null) {
            dto.setVideoQuota(quota.getVideoQuota());
            dto.setImageQuota(quota.getImageQuota());
            dto.setVideoClipQuota(quota.getVideoClipQuota());
            dto.setExportQuota(quota.getExportQuota());
            dto.setUsedVideoCount(quota.getUsedVideoCount());
            dto.setUsedImageCount(quota.getUsedImageCount());
            dto.setUsedVideoClipCount(quota.getUsedVideoClipCount());
            dto.setUsedExportCount(quota.getUsedExportCount());
            dto.setQuotaDate(quota.getQuotaDate());
            dto.setUpdatedAt(quota.getUpdatedAt());
        }

        return dto;
    }
}
