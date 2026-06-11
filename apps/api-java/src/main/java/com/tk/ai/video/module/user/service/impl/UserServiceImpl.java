package com.tk.ai.video.module.user.service.impl;

import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.auth.entity.UserEntity;
import com.tk.ai.video.module.auth.mapper.UserMapper;
import com.tk.ai.video.module.user.dto.UserInfoResponse;
import com.tk.ai.video.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserInfoResponse getCurrentUser(UUID userId) {
        UserEntity user = userMapper.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserInfoResponse rsp = new UserInfoResponse();
        rsp.setId(user.getId());
        rsp.setEmail(user.getEmail());
        rsp.setRole(user.getRole());
        rsp.setStatus(user.getStatus());
        rsp.setCreatedAt(user.getCreatedAt());
        return rsp;
    }
}
