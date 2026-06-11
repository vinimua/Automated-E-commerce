package com.tk.ai.video.module.auth.entity;

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
@TableName("refresh_tokens")
public class RefreshTokenEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID userId;
    private String tokenHash;
    private OffsetDateTime expiresAt;
    private Boolean revoked;
    private OffsetDateTime createdAt;
}
