package com.tk.ai.video.module.storyboard.dto;

import lombok.Data;
import java.util.List;

@Data
public class UpdateStoryboardRequest {
    private String title;
    private String hook;
    private String coverText;
    private String caption;
    private List<String> hashtags;
    private List<StoryboardShotDto> shots;
}
