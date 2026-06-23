package com.tk.ai.video.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.auth.entity.UserEntity;
import com.tk.ai.video.module.auth.mapper.UserMapper;
import com.tk.ai.video.module.log.entity.ModelLogEntity;
import com.tk.ai.video.module.log.entity.RenderLogEntity;
import com.tk.ai.video.module.log.mapper.ModelLogMapper;
import com.tk.ai.video.module.log.mapper.RenderLogMapper;
import com.tk.ai.video.module.user.dto.UserInfoResponse;
import com.tk.ai.video.module.video.entity.VideoEntity;
import com.tk.ai.video.module.video.mapper.VideoMapper;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
}
