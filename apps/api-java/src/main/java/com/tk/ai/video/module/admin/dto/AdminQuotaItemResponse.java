package com.tk.ai.video.module.admin.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class AdminQuotaItemResponse {
    private UUID userId;
    private String email;
    private String role;
    private String status;

    private int videoQuota;
    private int imageQuota;
    private int videoClipQuota;
    private int exportQuota;

    private int usedVideoCount;
    private int usedImageCount;
    private int usedVideoClipCount;
    private int usedExportCount;

    private LocalDate quotaDate;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
