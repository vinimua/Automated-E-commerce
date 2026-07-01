package com.tk.ai.video.module.quota.service.impl;

import com.tk.ai.video.module.quota.entity.UserQuotaEntity;
import com.tk.ai.video.module.quota.mapper.QuotaRecordMapper;
import com.tk.ai.video.module.quota.mapper.UserQuotaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceImplTest {

    @Mock
    private UserQuotaMapper userQuotaMapper;

    @Mock
    private QuotaRecordMapper quotaRecordMapper;

    @InjectMocks
    private QuotaServiceImpl service;

    @Test
    void getQuotaByUserIdResetsUsageWhenQuotaDateIsBeforeToday() {
        UUID userId = UUID.randomUUID();
        UserQuotaEntity quota = new UserQuotaEntity();
        quota.setId(UUID.randomUUID());
        quota.setUserId(userId);
        quota.setVideoQuota(10);
        quota.setImageQuota(50);
        quota.setVideoClipQuota(10);
        quota.setExportQuota(10);
        quota.setUsedVideoCount(10);
        quota.setUsedImageCount(12);
        quota.setUsedVideoClipCount(3);
        quota.setUsedExportCount(2);
        quota.setQuotaDate(LocalDate.now().minusDays(1));

        when(userQuotaMapper.selectByUserIdForUpdate(userId)).thenReturn(Optional.of(quota));

        var response = service.getQuotaByUserId(userId);

        assertThat(response.getUsedVideoCount()).isZero();
        assertThat(response.getUsedImageCount()).isZero();
        assertThat(response.getUsedVideoClipCount()).isZero();
        assertThat(response.getUsedExportCount()).isZero();
        assertThat(response.getQuotaDate()).isEqualTo(LocalDate.now());
        verify(userQuotaMapper).updateById(quota);
    }
}
