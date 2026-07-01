package com.tk.ai.video.module.videotask.entity;

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
@TableName(value = "video_tasks", autoResultMap = true)
public class VideoTaskEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID userId;
    private UUID productId;
    private String status;
    private int progress;
    private int duration;
    private String videoType;
    private Boolean needSubtitles;
    private Boolean needVoiceover;
    private UUID selectedPlanId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> renderManifest;

    private String manifestVersion;
    private String schemaVersion;
    private String failedStage;
    private String errorCode;
    private String errorMessage;
    private Boolean errorRetryable;
    private int retryCount;

    // Fashion Creative Loop V1 fields
    private String taskMode;
    private String productCategory;
    private Integer shotCount;
    private int currentVersion;

    private String aiWorkflowId;
    private String renderTaskId;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
