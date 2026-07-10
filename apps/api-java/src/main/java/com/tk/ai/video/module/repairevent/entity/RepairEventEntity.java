package com.tk.ai.video.module.repairevent.entity;

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
@TableName(value = "repair_events", autoResultMap = true)
public class RepairEventEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID taskId;
    private UUID userId;

    private String targetType;
    private String targetId;

    @TableField("feedback_text")
    private String userFeedback;

    @TableField("feedback_category")
    private String issueType;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> repairScope;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> repairPlan;

    private Integer beforeVersion;
    private Integer afterVersion;
    private String status;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
