package com.tk.ai.video.module.quota.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quota_records")
public class QuotaRecordEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID userId;
    private UUID taskId;
    private String type;
    private int amount;
    private String direction;
    private String reason;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
}
