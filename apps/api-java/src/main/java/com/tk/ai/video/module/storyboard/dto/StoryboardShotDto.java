package com.tk.ai.video.module.storyboard.dto;

import lombok.Data;

@Data
public class StoryboardShotDto {
    private int shotNo;
    private int duration;
    private String scene;
    private String action;
    private String subtitle;
    private String materialType;
    private String prompt;
    private String editInstruction;
}
