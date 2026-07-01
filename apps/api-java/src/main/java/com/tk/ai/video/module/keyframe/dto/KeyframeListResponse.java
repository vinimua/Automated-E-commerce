package com.tk.ai.video.module.keyframe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class KeyframeListResponse {
    private UUID taskId;
    private List<KeyframeResponse> keyframes;
}
