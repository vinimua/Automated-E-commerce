package com.tk.ai.video.module.keyframe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tk.ai.video.common.json.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "keyframes", autoResultMap = true)
public class KeyframeEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID taskId;
    private UUID storyboardId;
    private UUID shotId;
    private UUID userId;

    private int shotNo;
    private String source;
    private UUID assetId;
    private UUID materialId;
    private String imagePurpose;
    private String url;
    private String prompt;
    private String negativePrompt;
    private String userInstruction;
    private String provider;
    private String modelName;

    private String status;
    private int version;
    private String errorMessage;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
