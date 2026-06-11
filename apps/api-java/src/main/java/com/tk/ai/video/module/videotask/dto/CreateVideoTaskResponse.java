package com.tk.ai.video.module.videotask.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class CreateVideoTaskResponse {
    private UUID taskId;
    private String status;
    private int progress;
}
