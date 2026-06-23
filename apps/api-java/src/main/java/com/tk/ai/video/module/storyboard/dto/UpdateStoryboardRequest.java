package com.tk.ai.video.module.storyboard.dto;

import jakarta.validation.Valid;
import lombok.Data;
import java.util.List;

@Data
public class UpdateStoryboardRequest {
    private String title;
    private String hook;
    private String coverText;
    private String caption;
    private List<String> hashtags;
    @Valid
    private List<StoryboardShotDto> shots;
}
