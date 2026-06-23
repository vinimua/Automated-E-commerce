package com.tk.ai.video.module.log.entity;

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
@TableName(value = "render_logs", autoResultMap = true)
public class RenderLogEntity {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID taskId;
    private UUID videoId;
    private String renderTaskId;
    private String template;
    private String status;
    private Integer durationSeconds;
    private String outputUrl;
    private String coverUrl;
    private String errorMessage;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;
    private OffsetDateTime createdAt;
}
