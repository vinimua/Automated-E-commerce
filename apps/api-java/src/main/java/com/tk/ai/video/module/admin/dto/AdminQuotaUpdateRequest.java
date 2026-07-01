package com.tk.ai.video.module.admin.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminQuotaUpdateRequest {
    @Min(0)
    private Integer videoQuota;

    @Min(0)
    private Integer imageQuota;

    @Min(0)
    private Integer videoClipQuota;

    @Min(0)
    private Integer exportQuota;
}
