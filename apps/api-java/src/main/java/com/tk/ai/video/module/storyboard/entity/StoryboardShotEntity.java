package com.tk.ai.video.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("storyboard_shots")
public class StoryboardShotEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;
    private UUID storyboardId;
    private UUID taskId;
    private UUID userId;
    private int shotNo;
    private int duration;
    private String scene;
    private String action;
    private String subtitle;
    private String materialType;
    private String prompt;
    private String negativePrompt;
    private String editInstruction;
    private OffsetDateTime createdAt;
}
