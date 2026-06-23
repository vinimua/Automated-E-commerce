package com.tk.ai.video.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tk.ai.video.common.json.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "model_logs", autoResultMap = true)
public class ModelLogEntity {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID taskId;
    private UUID userId;
    private String service;
    private String provider;
    private String modelName;
    private String taskType;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal cost;
    private String status;
    private String errorMessage;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> requestSummary;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> responseSummary;
    private OffsetDateTime createdAt;
}
