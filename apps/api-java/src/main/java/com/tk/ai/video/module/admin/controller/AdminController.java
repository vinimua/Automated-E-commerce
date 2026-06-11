package com.tk.ai.video.module.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.log.entity.ModelLogEntity;
import com.tk.ai.video.module.log.entity.RenderLogEntity;
import com.tk.ai.video.module.log.mapper.ModelLogMapper;
import com.tk.ai.video.module.log.mapper.RenderLogMapper;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final VideoTaskMapper videoTaskMapper;
    private final ModelLogMapper modelLogMapper;
    private final RenderLogMapper renderLogMapper;

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
}
