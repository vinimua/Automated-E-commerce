package com.tk.ai.video.module.taskasset.entity;

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
@TableName(value = "task_assets", autoResultMap = true)
public class TaskAssetEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID taskId;
    private UUID userId;
    private UUID productId;
    private UUID shotId;

    @TableField("type")
    private String assetKind;

    @TableField("role")
    private String assetRole;

    private String source;

    private String url;

    private String fileName;

    private String mimeType;
    private Long sizeBytes;

    private String description;

    @TableField("is_confirmed")
    private Boolean confirmed;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> aiTags;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
