package com.tk.ai.video.module.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tk.ai.video.common.json.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@TableName(value = "videos", autoResultMap = true)
public class VideoEntity {
    @TableId(type = IdType.INPUT)
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
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> hashtags;
    private String status;
    private OffsetDateTime exportedAt;
    private OffsetDateTime createdAt;
}
