package com.tk.ai.video.module.user.service;

import com.tk.ai.video.module.user.dto.UserInfoResponse;

import java.util.UUID;

public interface UserService {
    UserInfoResponse getCurrentUser(UUID userId);
}
