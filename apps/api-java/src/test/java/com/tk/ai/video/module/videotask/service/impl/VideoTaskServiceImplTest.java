package com.tk.ai.video.module.videotask.service.impl;

import com.tk.ai.video.common.AiServiceClient;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.product.mapper.ProductImageMapper;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.quota.service.QuotaService;
import com.tk.ai.video.module.callback.service.impl.RenderMessageProducer;
import com.tk.ai.video.module.keyframe.mapper.KeyframeMapper;
import com.tk.ai.video.module.repairevent.mapper.RepairEventMapper;
import com.tk.ai.video.module.storyboard.entity.VideoPlanEntity;
import com.tk.ai.video.module.storyboard.mapper.VideoPlanMapper;
import com.tk.ai.video.module.videoclip.mapper.VideoClipMapper;
import com.tk.ai.video.module.videotask.dto.ConfirmPlanRequest;
import com.tk.ai.video.module.videotask.dto.SelectPlanRequest;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private QuotaService quotaService;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private KeyframeMapper keyframeMapper;
    @Mock
    private VideoClipMapper videoClipMapper;
    @Mock
    private RepairEventMapper repairEventMapper;
    @Mock
    private RenderMessageProducer renderMessageProducer;

    @InjectMocks
    private VideoTaskServiceImpl service;

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
                eq("product_showcase")
        );
    }
}
