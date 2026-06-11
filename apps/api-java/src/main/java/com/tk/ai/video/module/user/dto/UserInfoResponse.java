package com.tk.ai.video.module.user.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class UserInfoResponse {
    private UUID id;
    private String email;
    private String role;
    private String status;
    private OffsetDateTime createdAt;
}
