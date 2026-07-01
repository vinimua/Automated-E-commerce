package com.tk.ai.video.module.taskasset.service.impl;

import com.tk.ai.video.common.AiServiceClient;
import com.tk.ai.video.module.taskasset.dto.ConfirmAssetsRequest;
import com.tk.ai.video.module.taskasset.entity.TaskAssetEntity;
import com.tk.ai.video.module.taskasset.mapper.TaskAssetMapper;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAssetServiceImplTest {

    @Mock
    private TaskAssetMapper taskAssetMapper;
    @Mock
    private VideoTaskMapper videoTaskMapper;
    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private TaskAssetServiceImpl service;

    @Test
    void confirmAssetsFromUploadingStartsAssetAnalysis() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("asset_uploading");

        TaskAssetEntity asset = new TaskAssetEntity();
        asset.setId(assetId);
        asset.setTaskId(taskId);
        asset.setUserId(userId);
        asset.setProductId(productId);
        asset.setAssetKind("image");
        asset.setAssetRole("product_front");
        asset.setSource("user_upload");
        asset.setUrl("https://example.com/front.png");
        asset.setConfirmed(false);

        ConfirmAssetsRequest request = new ConfirmAssetsRequest();
        request.setAssetIds(List.of(assetId));

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(taskAssetMapper.findByTaskId(taskId)).thenReturn(List.of(asset));

        VideoTaskStatusResponse response = service.confirmAssets(taskId, request, userId);

        assertThat(response.getStatus()).isEqualTo("asset_analyzing");
        assertThat(task.getStatus()).isEqualTo("asset_analyzing");
        assertThat(asset.getConfirmed()).isTrue();
        verify(aiServiceClient).startAssetAnalysis(eq(taskId), eq(productId), eq(userId), any());
    }
}
