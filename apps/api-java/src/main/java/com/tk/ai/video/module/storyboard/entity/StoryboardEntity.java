package com.tk.ai.video.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@TableName("storyboards")
public class StoryboardEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;
    private UUID taskId;
    private UUID planId;
    private UUID productId;
    private UUID userId;
    private String title;
    private String hook;
    private String script;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> subtitles;
    private String coverText;
    private String caption;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> hashtags;
    private String musicSuggestion;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rawAiOutput;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
