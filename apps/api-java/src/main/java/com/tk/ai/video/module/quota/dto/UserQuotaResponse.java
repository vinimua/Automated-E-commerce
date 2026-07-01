package com.tk.ai.video.module.quota.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserQuotaResponse {

    private int videoQuota;
    private int imageQuota;
    private int videoClipQuota;
    private int exportQuota;

    private int usedVideoCount;
    private int usedImageCount;
    private int usedVideoClipCount;
    private int usedExportCount;
    private LocalDate quotaDate;
}
