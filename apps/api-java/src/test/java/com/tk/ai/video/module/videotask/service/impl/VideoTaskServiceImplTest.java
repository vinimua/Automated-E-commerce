package com.tk.ai.video.module.videotask.service.impl;

import com.tk.ai.video.common.AiServiceClient;
import com.tk.ai.video.common.CreativeContextAssembler;
import com.tk.ai.video.common.BusinessException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.creativestate.entity.CreativeStateEntity;
import com.tk.ai.video.module.creativestate.mapper.CreativeStateMapper;
import com.tk.ai.video.module.product.entity.ProductEntity;
import com.tk.ai.video.module.product.entity.ProductImageEntity;
import com.tk.ai.video.module.product.mapper.ProductImageMapper;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.quota.service.QuotaService;
import com.tk.ai.video.module.callback.service.impl.RenderMessageProducer;
import com.tk.ai.video.module.keyframe.mapper.KeyframeMapper;
import com.tk.ai.video.module.repairevent.mapper.RepairEventMapper;
import com.tk.ai.video.module.storyboard.entity.StoryboardEntity;
import com.tk.ai.video.module.storyboard.entity.VideoPlanEntity;
import com.tk.ai.video.module.storyboard.mapper.StoryboardMapper;
import com.tk.ai.video.module.storyboard.mapper.VideoPlanMapper;
import com.tk.ai.video.module.taskasset.entity.TaskAssetEntity;
import com.tk.ai.video.module.taskasset.mapper.TaskAssetMapper;
import com.tk.ai.video.module.videoclip.mapper.VideoClipMapper;
import com.tk.ai.video.module.videotask.dto.ConfirmPlanRequest;
import com.tk.ai.video.module.videotask.dto.CreateFashionTaskRequest;
import com.tk.ai.video.module.videotask.dto.FashionTaskCreateResponse;
import com.tk.ai.video.module.videotask.dto.SelectPlanRequest;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoTaskServiceImplTest {

    @Mock
    private VideoTaskMapper videoTaskMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductImageMapper productImageMapper;
    @Mock
    private VideoPlanMapper videoPlanMapper;
    @Mock
    private StoryboardMapper storyboardMapper;
    @Mock
    private QuotaService quotaService;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private CreativeContextAssembler creativeContextAssembler;
    @Mock
    private KeyframeMapper keyframeMapper;
    @Mock
    private VideoClipMapper videoClipMapper;
    @Mock
    private RepairEventMapper repairEventMapper;
    @Mock
    private RenderMessageProducer renderMessageProducer;
    @Mock
    private TaskAssetMapper taskAssetMapper;
    @Mock
    private CreativeStateMapper creativeStateMapper;

    @InjectMocks
    private VideoTaskServiceImpl service;

    @Test
    void createFashionTaskCreatesProductTaskAssetsAndCreativeState() {
        UUID userId = UUID.randomUUID();
        CreateFashionTaskRequest request = new CreateFashionTaskRequest();
        request.setName("Linen Shirt");
        request.setDescription("Breathable summer shirt");
        request.setProductLink("https://shop.example/products/linen-shirt");
        request.setImageUrls(List.of("https://cdn.example/linen-front.jpg"));
        request.setTargetMarket("US");
        request.setLanguage("en");
        request.setDuration(20);
        request.setNeedSubtitles(true);
        request.setTaskMode("PRODUCT_CREATIVE");

        when(videoTaskMapper.selectCount(any())).thenReturn(0L);

        FashionTaskCreateResponse response = service.createFashionTask(request, userId);

        assertThat(response.getTaskId()).isNotNull();
        assertThat(response.getProductId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("asset_uploading");
        assertThat(response.getProgress()).isZero();

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insert(productCaptor.capture());
        assertThat(productCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(productCaptor.getValue().getName()).isEqualTo("Linen Shirt");

        ArgumentCaptor<ProductImageEntity> imageCaptor = ArgumentCaptor.forClass(ProductImageEntity.class);
        verify(productImageMapper).insert(imageCaptor.capture());
        assertThat(imageCaptor.getValue().getUrl()).isEqualTo("https://cdn.example/linen-front.jpg");
        assertThat(imageCaptor.getValue().getIsPrimary()).isTrue();

        ArgumentCaptor<VideoTaskEntity> taskCaptor = ArgumentCaptor.forClass(VideoTaskEntity.class);
        verify(videoTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(taskCaptor.getValue().getProductId()).isEqualTo(response.getProductId());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("asset_uploading");
        assertThat(taskCaptor.getValue().getTaskMode()).isEqualTo("PRODUCT_CREATIVE");
        assertThat(taskCaptor.getValue().getVideoType()).isEqualTo("pain_point_solution");

        ArgumentCaptor<TaskAssetEntity> assetCaptor = ArgumentCaptor.forClass(TaskAssetEntity.class);
        verify(taskAssetMapper).insert(assetCaptor.capture());
        assertThat(assetCaptor.getValue().getTaskId()).isEqualTo(response.getTaskId());
        assertThat(assetCaptor.getValue().getAssetKind()).isEqualTo("image");
        assertThat(assetCaptor.getValue().getAssetRole()).isEqualTo("product_front");
        assertThat(assetCaptor.getValue().getSource()).isEqualTo("user_upload");

        ArgumentCaptor<CreativeStateEntity> creativeStateCaptor = ArgumentCaptor.forClass(CreativeStateEntity.class);
        verify(creativeStateMapper).insert(creativeStateCaptor.capture());
        assertThat(creativeStateCaptor.getValue().getTaskId()).isEqualTo(response.getTaskId());
        assertThat(creativeStateCaptor.getValue().getProductJson()).containsEntry("name", "Linen Shirt");
        assertThat(creativeStateCaptor.getValue().getUserRequirementsJson())
                .containsEntry("taskMode", "PRODUCT_CREATIVE")
                .containsEntry("duration", 20);

        verify(quotaService).consumeQuota(eq(userId), eq(response.getTaskId()), eq("video"), eq(1), eq("task:" + response.getTaskId() + ":video:create"));
    }

    @Test
    void createFashionTaskAllowsProductCreativeWithoutInitialImages() {
        UUID userId = UUID.randomUUID();
        CreateFashionTaskRequest request = new CreateFashionTaskRequest();
        request.setName("Linen Shirt");
        request.setDescription("Breathable summer shirt");
        request.setProductLink("https://shop.example/products/linen-shirt");
        request.setTargetMarket("US");
        request.setLanguage("en");
        request.setDuration(20);
        request.setNeedSubtitles(true);
        request.setTaskMode("PRODUCT_CREATIVE");

        when(videoTaskMapper.selectCount(any())).thenReturn(0L);

        FashionTaskCreateResponse response = service.createFashionTask(request, userId);

        assertThat(response.getTaskId()).isNotNull();
        assertThat(response.getProductId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("asset_uploading");
        assertThat(response.getProgress()).isZero();

        verify(productMapper).insert(any(ProductEntity.class));
        verify(productImageMapper, never()).insert(any(ProductImageEntity.class));
        verify(videoTaskMapper).insert(any(VideoTaskEntity.class));
        verify(taskAssetMapper, never()).insert(any(TaskAssetEntity.class));
        verify(creativeStateMapper).insert(any(CreativeStateEntity.class));
        verify(quotaService).consumeQuota(eq(userId), eq(response.getTaskId()), eq("video"), eq(1), eq("task:" + response.getTaskId() + ":video:create"));
    }

    @Test
    void createFashionTaskRejectsInvalidTaskModeBeforeWrites() {
        UUID userId = UUID.randomUUID();
        CreateFashionTaskRequest request = new CreateFashionTaskRequest();
        request.setName("Linen Shirt");
        request.setTargetMarket("US");
        request.setLanguage("en");
        request.setDuration(20);
        request.setTaskMode("UNKNOWN_MODE");

        assertThatThrownBy(() -> service.createFashionTask(request, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid taskMode");

        verify(productMapper, never()).insert(any(ProductEntity.class));
        verify(productImageMapper, never()).insert(any(ProductImageEntity.class));
        verify(videoTaskMapper, never()).insert(any(VideoTaskEntity.class));
        verify(taskAssetMapper, never()).insert(any(TaskAssetEntity.class));
        verify(creativeStateMapper, never()).insert(any(CreativeStateEntity.class));
        verify(quotaService, never()).consumeQuota(any(), any(), any(), anyInt(), any());
    }

    @Test
    void selectPlanValidatesOwnershipAndDispatchesSelectedPlanPayload() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("waiting_plan_selection");
        task.setDuration(20);
        task.setVideoType("pain_point_solution");
        task.setNeedSubtitles(true);
        task.setNeedVoiceover(false);

        VideoPlanEntity plan = new VideoPlanEntity();
        plan.setId(planId);
        plan.setTaskId(taskId);
        plan.setUserId(userId);
        plan.setProductId(productId);
        plan.setType("pain_point_solution");
        plan.setTitle("Plan title");
        plan.setHook("Hook");
        plan.setStructure("Hook -> Demo -> CTA");
        plan.setReason("Best fit");
        plan.setEstimatedDuration(20);
        plan.setScore(88);

        SelectPlanRequest request = new SelectPlanRequest();
        request.setPlanId(planId);

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(videoPlanMapper.findOwnedPlan(planId, taskId, userId)).thenReturn(Optional.of(plan));

        service.selectPlan(taskId, request, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> selectedPlanCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiServiceClient).startSelectedPlanGeneration(
                eq(taskId),
                eq(productId),
                eq(userId),
                eq(planId),
                selectedPlanCaptor.capture(),
                eq(20),
                eq("pain_point_solution"),
                eq(true),
                eq(false)
        );

        assertThat(selectedPlanCaptor.getValue())
                .containsEntry("planId", planId.toString())
                .containsEntry("title", "Plan title")
                .containsEntry("score", 88);
    }

    @Test
    void selectPlanRejectsPlanOutsideCurrentTask() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setStatus("waiting_plan_selection");

        SelectPlanRequest request = new SelectPlanRequest();
        request.setPlanId(planId);

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(videoPlanMapper.findOwnedPlan(planId, taskId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.selectPlan(taskId, request, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(aiServiceClient, never()).startSelectedPlanGeneration(
                any(), any(), any(), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean()
        );
    }

    @Test
    void confirmPlanPersistsSelectedPlanAndStartsStoryboardGeneration() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("waiting_plan_selection");
        task.setDuration(20);
        task.setVideoType("product_showcase");

        VideoPlanEntity plan = new VideoPlanEntity();
        plan.setId(planId);
        plan.setTaskId(taskId);
        plan.setUserId(userId);
        plan.setProductId(productId);
        plan.setType("product_showcase");
        plan.setTitle("Fashion plan");
        plan.setHook("Hook");
        plan.setStructure("Hook -> Try-on -> CTA");
        plan.setReason("Strong product detail flow");
        plan.setEstimatedDuration(20);
        plan.setScore(90);

        ConfirmPlanRequest request = new ConfirmPlanRequest();
        request.setPlanId(planId);

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(videoPlanMapper.findOwnedPlan(planId, taskId, userId)).thenReturn(Optional.of(plan));

        service.confirmPlan(taskId, request, userId);

        assertThat(task.getSelectedPlanId()).isEqualTo(planId);
        assertThat(task.getStatus()).isEqualTo("storyboard_generating");
        verify(aiServiceClient).startStoryboardGeneration(
                eq(taskId),
                eq(productId),
                eq(userId),
                eq(planId),
                any(),
                eq(20),
                eq("product_showcase"),
                any()
        );
    }

    @Test
    void confirmStoryboardValidatesStoryboardAndStartsKeyframeGeneration() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Map<String, Object> rawStoryboard = Map.of(
                "title", "Storyboard",
                "shots", List.of(Map.of("shotNo", 1, "scene", "Opening"))
        );

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("waiting_storyboard_confirmation");
        task.setProgress(40);

        StoryboardEntity storyboard = new StoryboardEntity();
        storyboard.setId(UUID.randomUUID());
        storyboard.setTaskId(taskId);
        storyboard.setUserId(userId);
        storyboard.setRawAiOutput(rawStoryboard);

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(storyboardMapper.findByTaskId(taskId)).thenReturn(Optional.of(storyboard));

        service.confirmStoryboard(taskId, userId);

        assertThat(task.getStatus()).isEqualTo("keyframe_configuring");
        assertThat(task.getProgress()).isEqualTo(50);
        verify(videoTaskMapper).updateById(task);
        verify(aiServiceClient).startKeyframeGeneration(taskId, productId, userId, rawStoryboard);
    }

    @Test
    void confirmStoryboardRejectsMissingStoryboardBeforeStateChange() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        VideoTaskEntity task = new VideoTaskEntity();
        task.setId(taskId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setStatus("waiting_storyboard_confirmation");
        task.setProgress(40);

        when(videoTaskMapper.selectById(taskId)).thenReturn(task);
        when(storyboardMapper.findByTaskId(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmStoryboard(taskId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Storyboard not found");

        assertThat(task.getStatus()).isEqualTo("waiting_storyboard_confirmation");
        assertThat(task.getProgress()).isEqualTo(40);
        verify(videoTaskMapper, never()).updateById(any(VideoTaskEntity.class));
        verify(aiServiceClient, never()).startKeyframeGeneration(any(), any(), any(), any());
    }
}
