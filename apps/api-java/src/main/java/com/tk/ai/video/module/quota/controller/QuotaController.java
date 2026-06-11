package com.tk.ai.video.module.quota.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.quota.dto.UserQuotaResponse;
import com.tk.ai.video.module.quota.service.QuotaService;
import com.tk.ai.video.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotas")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    @GetMapping("/me")
    public ApiResponse<UserQuotaResponse> getMyQuota(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(quotaService.getQuotaByUserId(principal.getUserId()));
    }
}
