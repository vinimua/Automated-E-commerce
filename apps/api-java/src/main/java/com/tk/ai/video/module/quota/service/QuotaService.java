package com.tk.ai.video.module.quota.service;

import com.tk.ai.video.module.quota.dto.UserQuotaResponse;

import java.util.UUID;

public interface QuotaService {

    UserQuotaResponse getQuotaByUserId(UUID userId);

    void consumeQuota(UUID userId, UUID taskId, String type, int amount, String idempotencyKey);

    void refundQuota(UUID userId, UUID taskId, String type, int amount, String idempotencyKey);
}
