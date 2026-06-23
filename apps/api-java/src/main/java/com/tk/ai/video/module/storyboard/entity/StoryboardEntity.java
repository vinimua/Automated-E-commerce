package com.tk.ai.video.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tk.ai.video.common.json.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "storyboards", autoResultMap = true)
public class StoryboardEntity {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID taskId;
    private UUID planId;
    private UUID productId;
    private UUID userId;
    private String title;
    private String hook;
    private String script;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> subtitles;
    private String coverText;
    private String caption;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> hashtags;
    private String musicSuggestion;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawAiOutput;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
