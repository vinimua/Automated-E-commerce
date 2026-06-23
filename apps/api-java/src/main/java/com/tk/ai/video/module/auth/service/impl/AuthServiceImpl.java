package com.tk.ai.video.module.auth.service.impl;

import com.tk.ai.video.common.BusinessException;
import com.tk.ai.video.module.auth.dto.*;
import com.tk.ai.video.module.auth.entity.RefreshTokenEntity;
import com.tk.ai.video.module.auth.entity.UserEntity;
import com.tk.ai.video.module.auth.mapper.RefreshTokenMapper;
import com.tk.ai.video.module.auth.mapper.UserMapper;
import com.tk.ai.video.module.auth.service.AuthService;
import com.tk.ai.video.module.quota.entity.UserQuotaEntity;
import com.tk.ai.video.module.quota.mapper.UserQuotaMapper;
import com.tk.ai.video.security.JwtTokenProvider;
import com.tk.ai.video.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final UserQuotaMapper userQuotaMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userMapper.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("Email already registered");
        }

        // Create user
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER.name().toLowerCase());
        user.setStatus("active");
        userMapper.insert(user);

        // Create initial quota row
        UserQuotaEntity quota = new UserQuotaEntity();
        quota.setId(UUID.randomUUID());
        quota.setUserId(user.getId());
        quota.setVideoQuota(10);  // Default: 10 video generations
        quota.setImageQuota(50);  // Default: 50 AI image generations
        quota.setVideoClipQuota(10);  // Default: 10 AI video clip generations
        quota.setExportQuota(10); // Default: 10 exports
        quota.setUsedVideoCount(0);
        quota.setUsedImageCount(0);
        quota.setUsedVideoClipCount(0);
        quota.setUsedExportCount(0);
        userQuotaMapper.insert(quota);

        log.info("User registered: userId={}, email={}", user.getId(), user.getEmail());

        // Generate tokens
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userMapper.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password");
        }

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshTokenEntity storedToken = refreshTokenMapper.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (Boolean.TRUE.equals(storedToken.getRevoked())) {
            throw new BusinessException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("Refresh token has expired");
        }

        // Load user
        UserEntity user = userMapper.findById(storedToken.getUserId())
                .orElseThrow(() -> new BusinessException("User not found"));

        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("Account is disabled");
        }

        // Revoke old token (rotate)
        storedToken.setRevoked(true);
        refreshTokenMapper.updateById(storedToken);

        // Issue new pair
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(UserEntity user) {
        Role role = Role.valueOf(user.getRole().toUpperCase());
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), role);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        // Store refresh token hash
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash(hashToken(refreshToken))
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .revoked(false)
                .build();
        refreshTokenMapper.insert(entity);

        return AuthResponse.builder()
                .userId(user.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
