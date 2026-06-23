package com.tk.ai.video.module.storyboard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StoryboardShotDto {
    @Min(1)
    private int shotNo;
    @Min(1)
    @Max(8)
    private int duration;
    @NotBlank
    private String scene;
    private String action;
    @NotBlank
    private String subtitle;
    @NotBlank
    @Pattern(regexp = "product_image|product_image_motion|ai_image|ai_video|text_animation|uploaded_video")
    private String materialType;
    private String prompt;
    private String editInstruction;
}
