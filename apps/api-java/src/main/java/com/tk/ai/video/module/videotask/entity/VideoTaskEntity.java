package com.tk.ai.video.module.videotask.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName("video_tasks")
public class VideoTaskEntity {

    @TableId(type = IdType.ASSIGN_UUID)
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

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> renderManifest;

    private String manifestVersion;
    private String schemaVersion;
    private String failedStage;
    private String errorCode;
    private String errorMessage;
    private Boolean errorRetryable;
    private int retryCount;
    private String aiWorkflowId;
    private String renderTaskId;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
