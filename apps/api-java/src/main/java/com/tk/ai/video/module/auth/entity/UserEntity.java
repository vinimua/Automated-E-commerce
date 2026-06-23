package com.tk.ai.video.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("users")
public class UserEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private String email;
    private String passwordHash;
    private String role;
    private String status;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
