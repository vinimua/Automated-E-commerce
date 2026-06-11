package com.tk.ai.video.module.storyboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class VideoPlanListResponse {
    private UUID taskId;
    private List<VideoPlanResponse> plans;
}
