package com.tk.ai.video.module.videotask.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ConfirmPlanRequest {
    @NotNull
    private UUID planId;
}
