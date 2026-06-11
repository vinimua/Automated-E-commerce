package com.tk.ai.video.module.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@TableName("videos")
public class VideoEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;
    private UUID taskId;
    private UUID productId;
    private UUID userId;
    private String title;
    private String videoUrl;
    private String coverUrl;
    private int duration;
    private String resolution;
    private int fps;
    private Integer qualityScore;
    private Integer riskScore;
    private String caption;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> hashtags;
    private String status;
    private OffsetDateTime exportedAt;
    private OffsetDateTime createdAt;
}
