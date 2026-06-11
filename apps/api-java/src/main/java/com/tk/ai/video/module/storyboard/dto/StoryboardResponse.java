package com.tk.ai.video.module.storyboard.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class StoryboardResponse {
    private UUID storyboardId;
    private String title;
    private String hook;
    private String script;
    private String coverText;
    private String caption;
    private List<String> hashtags;
    private String musicSuggestion;
    private List<StoryboardShotDto> shots;
}
