package com.tk.ai.video.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName("model_logs")
public class ModelLogEntity {
    @TableId(type = IdType.ASSIGN_UUID)
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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestSummary;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> responseSummary;
    private OffsetDateTime createdAt;
}
