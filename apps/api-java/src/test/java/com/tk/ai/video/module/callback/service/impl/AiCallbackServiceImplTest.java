package com.tk.ai.video.module.callback.service.impl;

import com.tk.ai.video.module.callback.dto.AiCallbackRequest;
import com.tk.ai.video.module.creativestate.mapper.CreativeStateMapper;
import com.tk.ai.video.module.keyframe.mapper.KeyframeMapper;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.quota.service.QuotaService;
import com.tk.ai.video.module.repairevent.mapper.RepairEventMapper;
import com.tk.ai.video.module.storyboard.entity.StoryboardEntity;
import com.tk.ai.video.module.storyboard.entity.StoryboardShotEntity;
import com.tk.ai.video.module.storyboard.mapper.StoryboardMapper;
import com.tk.ai.video.module.storyboard.mapper.StoryboardShotMapper;
import com.tk.ai.video.module.storyboard.mapper.VideoPlanMapper;
import com.tk.ai.video.module.videoclip.mapper.VideoClipMapper;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCallbackServiceImplTest {

    @Mock
    private VideoTaskMapper videoTaskMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private VideoPlanMapper videoPlanMapper;
    @Mock
    private StoryboardMapper storyboardMapper;
    @Mock
    private StoryboardShotMapper storyboardShotMapper;
    @Mock
    private RenderMessageProducer renderMessageProducer;
    @Mock
    private KeyframeMapper keyframeMapper;
    @Mock
    private VideoClipMapper videoClipMapper;
    @Mock
    private CreativeStateMapper creativeStateMapper;
    @Mock
    private RepairEventMapper repairEventMapper;
    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private AiCallbackServiceImpl service;

    @Test
    void storyboardCallbackPersistsStoryboardShotsAndAdvancesTask() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setSelectedPlanId(planId);
        task.setStatus("storyboard_generating");

        AiCallbackRequest request = new AiCallbackRequest();
        request.setTaskId(taskId);
        request.setSchemaVersion("1.0.0");
        request.setStage("storyboard");
        request.setStatus("success");
        request.setStoryboard(Map.of(
                "title", "Opening Storyboard",
                "hook", "Strong hook",
                "script", "Full script",
                "shots", List.of(Map.of(
                        "shotNo", 1,
                        "duration", 3,
                        "scene", "Product detail",
                        "subtitle", "Soft linen",
                        "materialType", "image",
                        "prompt", "Clean product photo"
                ))
        ));

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);

        service.handleCallback(request);

        ArgumentCaptor<StoryboardEntity> storyboardCaptor = ArgumentCaptor.forClass(StoryboardEntity.class);
        verify(storyboardMapper).insert(storyboardCaptor.capture());
        assertThat(storyboardCaptor.getValue().getTaskId()).isEqualTo(taskId);
        assertThat(storyboardCaptor.getValue().getPlanId()).isEqualTo(planId);
        assertThat(storyboardCaptor.getValue().getTitle()).isEqualTo("Opening Storyboard");

        ArgumentCaptor<StoryboardShotEntity> shotCaptor = ArgumentCaptor.forClass(StoryboardShotEntity.class);
        verify(storyboardShotMapper).insert(shotCaptor.capture());
        assertThat(shotCaptor.getValue().getTaskId()).isEqualTo(taskId);
        assertThat(shotCaptor.getValue().getShotNo()).isEqualTo(1);
        assertThat(shotCaptor.getValue().getScene()).isEqualTo("Product detail");

        assertThat(task.getStatus()).isEqualTo("waiting_storyboard_confirmation");
        verify(videoTaskMapper).updateById(task);
    }

    @Test
    void storyboardCallbackIsIdempotentAfterStoryboardAlreadyProcessed() {
        UUID taskId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(UUID.randomUUID());
        task.setProductId(UUID.randomUUID());
        task.setStatus("waiting_storyboard_confirmation");

        AiCallbackRequest request = new AiCallbackRequest();
        request.setTaskId(taskId);
        request.setSchemaVersion("1.0.0");
        request.setStage("storyboard");
        request.setStatus("success");
        request.setStoryboard(Map.of("title", "Duplicate storyboard"));

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);

        service.handleCallback(request);

        verify(storyboardMapper, never()).insert(any(StoryboardEntity.class));
        verify(storyboardShotMapper, never()).insert(any(StoryboardShotEntity.class));
        verify(videoTaskMapper, never()).updateById(any(VideoTaskEntity.class));
    }

    @Test
    void assetAnalysisCallbackBackfillsMissingAnalysisAfterStatusAlreadyAdvanced() {
        UUID taskId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(UUID.randomUUID());
        task.setProductId(UUID.randomUUID());
        task.setStatus("waiting_asset_confirmation");
        task.setAssetAnalysis(null);

        AiCallbackRequest request = new AiCallbackRequest();
        request.setTaskId(taskId);
        request.setSchemaVersion("1.0.0");
        request.setStage("asset_analysis");
        request.setStatus("success");
        request.setFashionAssetAnalysis(Map.of(
                "schemaVersion", "1.0",
                "analysisText", "分析结果",
                "analyzedAssetIds", List.of("asset-1"),
                "model", "fixture",
                "analyzedAt", "2026-07-15T00:00:00Z"
        ));

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);

        service.handleCallback(request);

        assertThat(task.getAssetAnalysis()).isNotNull();
        assertThat(task.getAssetAnalysis()).containsEntry("analysisText", "分析结果");
        assertThat(task.getStatus()).isEqualTo("waiting_asset_confirmation");
        verify(videoTaskMapper).updateById(task);
    }
}
