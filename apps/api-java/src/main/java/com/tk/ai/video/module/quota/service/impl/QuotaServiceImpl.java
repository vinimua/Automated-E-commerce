package com.tk.ai.video.module.quota.service.impl;

import com.tk.ai.video.common.QuotaExceededException;
import com.tk.ai.video.module.quota.dto.UserQuotaResponse;
import com.tk.ai.video.module.quota.entity.QuotaRecordEntity;
import com.tk.ai.video.module.quota.entity.UserQuotaEntity;
import com.tk.ai.video.module.quota.mapper.QuotaRecordMapper;
import com.tk.ai.video.module.quota.mapper.UserQuotaMapper;
import com.tk.ai.video.module.quota.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final UserQuotaMapper userQuotaMapper;
    private final QuotaRecordMapper quotaRecordMapper;

    @Override
    public UserQuotaResponse getQuotaByUserId(UUID userId) {
        UserQuotaEntity quota = userQuotaMapper.findByUserId(userId)
                .orElseGet(() -> createInitialQuota(userId));
        return toResponse(quota);
    }

    @Override
    @Transactional
    public void consumeQuota(UUID userId, UUID taskId, String type, int amount, String idempotencyKey) {
        // Step 1: Try to insert the idempotency record
        try {
            QuotaRecordEntity record = QuotaRecordEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .taskId(taskId)
                    .type(type)
                    .amount(amount)
                    .direction("consume")
                    .idempotencyKey(idempotencyKey)
                    .build();
            quotaRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // Idempotent: already processed
            log.debug("Idempotent consume skipped for key={}", idempotencyKey);
            return;
        }

        // Step 2: Lock and update quota counters
        UserQuotaEntity quota = userQuotaMapper.selectByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Quota not found for user: " + userId));

        switch (type) {
            case "video" -> {
                if (quota.getUsedVideoCount() + amount > quota.getVideoQuota()) {
                    throw new QuotaExceededException("video");
                }
                quota.setUsedVideoCount(quota.getUsedVideoCount() + amount);
            }
            case "image" -> {
                if (quota.getUsedImageCount() + amount > quota.getImageQuota()) {
                    throw new QuotaExceededException("image");
                }
                quota.setUsedImageCount(quota.getUsedImageCount() + amount);
            }
            case "video_clip" -> {
                if (quota.getUsedVideoClipCount() + amount > quota.getVideoClipQuota()) {
                    throw new QuotaExceededException("video_clip");
                }
                quota.setUsedVideoClipCount(quota.getUsedVideoClipCount() + amount);
            }
            case "export" -> {
                if (quota.getUsedExportCount() + amount > quota.getExportQuota()) {
                    throw new QuotaExceededException("export");
                }
                quota.setUsedExportCount(quota.getUsedExportCount() + amount);
            }
            default -> throw new IllegalArgumentException("Unknown quota type: " + type);
        }

        userQuotaMapper.updateById(quota);
        log.debug("Consumed quota: userId={}, type={}, amount={}", userId, type, amount);
    }

    @Override
    @Transactional
    public void refundQuota(UUID userId, UUID taskId, String type, int amount, String idempotencyKey) {
        // Step 1: Try to insert the idempotency record
        try {
            QuotaRecordEntity record = QuotaRecordEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .taskId(taskId)
                    .type(type)
                    .amount(amount)
                    .direction("refund")
                    .idempotencyKey(idempotencyKey)
                    .build();
            quotaRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.debug("Idempotent refund skipped for key={}", idempotencyKey);
            return;
        }

        // Step 2: Lock and update quota counters
        UserQuotaEntity quota = userQuotaMapper.selectByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Quota not found for user: " + userId));

        switch (type) {
            case "video" -> {
                quota.setUsedVideoCount(Math.max(0, quota.getUsedVideoCount() - amount));
            }
            case "image" -> {
                quota.setUsedImageCount(Math.max(0, quota.getUsedImageCount() - amount));
            }
            case "video_clip" -> {
                quota.setUsedVideoClipCount(Math.max(0, quota.getUsedVideoClipCount() - amount));
            }
            case "export" -> {
                quota.setUsedExportCount(Math.max(0, quota.getUsedExportCount() - amount));
            }
            default -> throw new IllegalArgumentException("Unknown quota type: " + type);
        }

        userQuotaMapper.updateById(quota);
        log.debug("Refunded quota: userId={}, type={}, amount={}", userId, type, amount);
    }

    private UserQuotaEntity createInitialQuota(UUID userId) {
        UserQuotaEntity quota = new UserQuotaEntity();
        quota.setUserId(userId);
        quota.setId(UUID.randomUUID());
        quota.setVideoQuota(0);
        quota.setImageQuota(0);
        quota.setVideoClipQuota(0);
        quota.setExportQuota(0);
        userQuotaMapper.insert(quota);
        return quota;
    }

    private UserQuotaResponse toResponse(UserQuotaEntity quota) {
        UserQuotaResponse rsp = new UserQuotaResponse();
        rsp.setVideoQuota(quota.getVideoQuota());
        rsp.setImageQuota(quota.getImageQuota());
        rsp.setVideoClipQuota(quota.getVideoClipQuota());
        rsp.setExportQuota(quota.getExportQuota());
        rsp.setUsedVideoCount(quota.getUsedVideoCount());
        rsp.setUsedImageCount(quota.getUsedImageCount());
        rsp.setUsedVideoClipCount(quota.getUsedVideoClipCount());
        rsp.setUsedExportCount(quota.getUsedExportCount());
        return rsp;
    }
}
