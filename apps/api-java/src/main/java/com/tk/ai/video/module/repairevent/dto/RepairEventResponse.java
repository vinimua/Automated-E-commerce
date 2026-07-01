package com.tk.ai.video.module.repairevent.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class RepairEventResponse {
    private UUID repairEventId;
    private UUID taskId;
    private String targetType;
    private String targetId;
    private String userFeedback;
    private String issueType;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
