package com.tk.ai.video.module.videoclip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class VideoClipListResponse {
    private UUID taskId;
    private List<VideoClipResponse> clips;
}
