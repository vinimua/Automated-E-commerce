package com.tk.ai.video.module.videotask.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateVideoTaskRequest {

    @NotNull
    private UUID productId;

    @NotNull
    private Integer duration;

    @NotNull
    private String videoType;

    private Boolean needSubtitles = true;
    private Boolean needVoiceover = false;
}
