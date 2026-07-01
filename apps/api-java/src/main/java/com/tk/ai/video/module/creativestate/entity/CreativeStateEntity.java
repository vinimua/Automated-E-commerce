package com.tk.ai.video.module.creativestate.entity;

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
@TableName(value = "creative_states", autoResultMap = true)
public class CreativeStateEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID taskId;
    private UUID userId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> productJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> modelJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> sceneJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> outfitJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> referenceVideoJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> constraintsJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> userRequirementsJson;

    private int version;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
