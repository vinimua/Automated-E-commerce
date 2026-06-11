package com.tk.ai.video.module.callback.service.impl;

import com.tk.ai.video.common.InvalidStateTransitionException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.callback.dto.AiCallbackRequest;
import com.tk.ai.video.module.callback.service.AiCallbackService;
import com.tk.ai.video.module.product.entity.ProductEntity;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.storyboard.entity.*;
import com.tk.ai.video.module.storyboard.mapper.*;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import com.tk.ai.video.module.videotask.state.VideoTaskStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallbackServiceImpl implements AiCallbackService {

    private final VideoTaskMapper videoTaskMapper;
    private final ProductMapper productMapper;
    private final VideoPlanMapper videoPlanMapper;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardShotMapper storyboardShotMapper;

    @Override
    @Transactional
    public void handleCallback(AiCallbackRequest request) {
        VideoTaskEntity task = videoTaskMapper.selectById(request.getTaskId());
        if (task == null) {
            throw new ResourceNotFoundException("VideoTask", request.getTaskId());
        }

        // Idempotency: check if status already reflects this callback
        String expectedNextStatus = request.getNextTaskStatus();
        if (expectedNextStatus != null && expectedNextStatus.equals(task.getStatus())) {
            log.debug("Callback already processed: taskId={}, stage={}, status={}",
                    request.getTaskId(), request.getStage(), request.getStatus());
            return;
        }

        if ("success".equals(request.getStatus())) {
            handleSuccess(request, task);
        } else {
            handleFailed(request, task);
        }
    }

    private void handleSuccess(AiCallbackRequest request, VideoTaskEntity task) {
        String stage = request.getStage();

        switch (stage) {
            case "product_analysis" -> {
                // Save analysis to product
                if (request.getProductAnalysis() != null) {
                    ProductEntity product = productMapper.selectById(task.getProductId());
                    if (product != null) {
                        Map<String, Object> analysis = request.getProductAnalysis();
                        if (analysis.get("category") != null) product.setCategory((String) analysis.get("category"));
                        productMapper.updateById(product);
                    }
                }
                advanceTask(task, "analysis_completed");
            }
            case "video_plan" -> {
                // Save plans
                if (request.getPlans() != null) {
                    for (Map<String, Object> planData : request.getPlans()) {
                        VideoPlanEntity plan = new VideoPlanEntity();
                        plan.setTaskId(task.getId());
                        plan.setProductId(task.getProductId());
                        plan.setUserId(task.getUserId());
                        plan.setType((String) planData.getOrDefault("type", task.getVideoType()));
                        plan.setTitle((String) planData.get("title"));
                        plan.setHook((String) planData.get("hook"));
                        plan.setStructure((String) planData.get("structure"));
                        plan.setReason((String) planData.get("reason"));
                        plan.setEstimatedDuration(getInt(planData, "estimatedDuration"));
                        plan.setScore(getInt(planData, "score"));
                        plan.setRawAiOutput(planData);
                        videoPlanMapper.insert(plan);
                    }
                }
                advanceTask(task, "plan_generated");
            }
            case "storyboard" -> {
                if (request.getStoryboard() != null) {
                    Map<String, Object> sb = request.getStoryboard();
                    StoryboardEntity storyboard = new StoryboardEntity();
                    storyboard.setTaskId(task.getId());
                    storyboard.setPlanId(task.getSelectedPlanId());
                    storyboard.setProductId(task.getProductId());
                    storyboard.setUserId(task.getUserId());
                    storyboard.setTitle((String) sb.get("title"));
                    storyboard.setHook((String) sb.get("hook"));
                    storyboard.setScript((String) sb.get("script"));
                    storyboard.setCaption((String) sb.get("caption"));
                    storyboardMapper.insert(storyboard);

                    // Save shots
                    if (sb.get("shots") instanceof List<?> shotsList) {
                        for (Object s : shotsList) {
                            if (s instanceof Map<?, ?> shotMap) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> sm = (Map<String, Object>) shotMap;
                                StoryboardShotEntity shot = new StoryboardShotEntity();
                                shot.setStoryboardId(storyboard.getId());
                                shot.setTaskId(task.getId());
                                shot.setUserId(task.getUserId());
                                shot.setShotNo(getInt(sm, "shotNo"));
                                shot.setDuration(getInt(sm, "duration"));
                                shot.setScene((String) sm.get("scene"));
                                shot.setSubtitle((String) sm.get("subtitle"));
                                shot.setMaterialType((String) sm.get("materialType"));
                                shot.setPrompt((String) sm.get("prompt"));
                                storyboardShotMapper.insert(shot);
                            }
                        }
                    }
                }
                advanceTask(task, "script_generated");
            }
            case "material" -> {
                // Materials are saved in the callback handler; Phase 5 will create material records
                advanceTask(task, "material_generated");
            }
            case "quality_check" -> {
                advanceTask(task, "checking");
            }
            case "render_manifest" -> {
                if (request.getRenderManifest() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> manifest = request.getRenderManifest();
                    task.setRenderManifest(manifest);
                }
                advanceTask(task, "rendering");
            }
            default -> log.warn("Unknown callback stage: {}", stage);
        }
    }

    private void handleFailed(AiCallbackRequest request, VideoTaskEntity task) {
        if (request.getError() != null) {
            task.setFailedStage((String) request.getError().get("failedStage"));
            task.setErrorCode((String) request.getError().get("errorCode"));
            task.setErrorMessage((String) request.getError().get("errorMessage"));
            task.setErrorRetryable(Boolean.TRUE.equals(request.getError().get("retryable")));
        }
        advanceTask(task, "failed");
    }

    private void advanceTask(VideoTaskEntity task, String targetStatus) {
        try {
            VideoTaskStateMachine.validateTransition(task.getStatus(), targetStatus);
        } catch (InvalidStateTransitionException e) {
            log.warn("State transition skipped (already advanced?): {} -> {}", task.getStatus(), targetStatus);
            return;
        }
        task.setStatus(targetStatus);
        task.setUpdatedAt(OffsetDateTime.now());
        if ("completed".equals(targetStatus) || "failed".equals(targetStatus)) {
            task.setCompletedAt(OffsetDateTime.now());
            task.setProgress("completed".equals(targetStatus) ? 100 : task.getProgress());
        }
        videoTaskMapper.updateById(task);
        log.info("Task advanced: taskId={}, {} -> {}", task.getId(), task.getStatus(), targetStatus);
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }
}
