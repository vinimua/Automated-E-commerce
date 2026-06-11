package com.tk.ai.video.module.video.service;

import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.video.dto.*;

import java.util.UUID;

public interface VideoService {
    PageResult<VideoResponse> list(String status, UUID productId, int page, int pageSize, UUID userId);
    VideoResponse getById(UUID videoId, UUID userId);
    VideoExportResponse export(UUID videoId, UUID userId);
}
