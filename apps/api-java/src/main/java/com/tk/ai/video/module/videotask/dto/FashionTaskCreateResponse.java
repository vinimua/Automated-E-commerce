package com.tk.ai.video.module.videotask.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class FashionTaskCreateResponse {

    private UUID taskId;
    private UUID productId;
    private String status;
    private int progress;
}
