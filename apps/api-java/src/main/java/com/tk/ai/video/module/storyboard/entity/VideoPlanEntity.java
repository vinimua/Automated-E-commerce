package com.tk.ai.video.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName("video_plans")
public class VideoPlanEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;
    private UUID taskId;
    private UUID productId;
    private UUID userId;
    private String type;
    private String title;
    private String hook;
    private String structure;
    private String reason;
    private Integer estimatedDuration;
    private Integer score;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rawAiOutput;
    private OffsetDateTime createdAt;
}
