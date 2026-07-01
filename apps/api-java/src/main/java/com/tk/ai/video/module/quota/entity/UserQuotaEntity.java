package com.tk.ai.video.module.quota.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("user_quotas")
public class UserQuotaEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID userId;

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
