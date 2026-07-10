package com.tk.ai.video.module.videotask.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateVideoTaskRequest {

    @NotNull
    private UUID productId;

    @NotNull
    @Min(1)
    private Integer duration;

    @Pattern(regexp = "pain_point_solution|before_after|review|product_showcase|ugc_style|tutorial")
    private String videoType;

    private Boolean needSubtitles = true;
    private Boolean needVoiceover = false;

    // Fashion Creative Loop V1 fields
    @Pattern(regexp = "PRODUCT_CREATIVE|REFERENCE_STORYBOARD|USER_SCRIPT|CUSTOM_STORYBOARD")
    private String taskMode;
    private String productCategory;
    private Integer shotCount;
}
