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
    private final RenderMessageProducer renderMessageProducer;

    @Override
    @Transactional
    public void handleCallback(AiCallbackRequest request) {
        //入口 + 幂等守卫
        VideoTaskEntity task = videoTaskMapper.selectById(request.getTaskId());
        if (task == null) {
            throw new ResourceNotFoundException("VideoTask", request.getTaskId());
        }

        if (isStageAlreadyProcessed(request.getStage(), task.getStatus())) {
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
        //按阶段分流
        String stage = request.getStage();

        switch (stage) {
            case "product_analysis" -> {
                // Save analysis to product
                if (request.getProductAnalysis() != null) {
                    ProductEntity product = productMapper.selectById(task.getProductId());
                    if (product != null) {
                        Map<String, Object> analysis = request.getProductAnalysis();
                        if (analysis.get("category") != null) product.setCategory((String) analysis.get("category"));
                        product.setSellingPoints(asStringList(analysis.get("sellingPoints")));
                        product.setPainPoints(asStringList(analysis.get("painPoints")));
                        product.setTargetAudience(asStringList(analysis.get("targetAudience")));
                        product.setScenes(asStringList(analysis.get("scenes")));
                        product.setRecommendedVideoTypes(asStringList(analysis.get("recommendedVideoTypes")));
                        product.setRiskTips(asStringList(analysis.get("riskTips")));
                        product.setVideoScore(getNullableInt(analysis, "videoScore"));
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
                        plan.setId(UUID.randomUUID());
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
                advanceTask(task, "waiting_plan_selection");
            }
            case "storyboard" -> {
                if (request.getStoryboard() != null) {
                    Map<String, Object> sb = request.getStoryboard();
                    StoryboardEntity storyboard = new StoryboardEntity();
                    storyboard.setId(UUID.randomUUID());
                    storyboard.setTaskId(task.getId());
                    storyboard.setPlanId(task.getSelectedPlanId());
                    storyboard.setProductId(task.getProductId());
                    storyboard.setUserId(task.getUserId());
                    storyboard.setTitle((String) sb.get("title"));
                    storyboard.setHook((String) sb.get("hook"));
                    storyboard.setScript((String) sb.get("script"));
                    storyboard.setCaption((String) sb.get("caption"));
                    storyboard.setCoverText((String) sb.get("coverText"));
                    storyboard.setMusicSuggestion((String) sb.get("musicSuggestion"));
                    storyboard.setHashtags(asStringList(sb.get("hashtags")));
                    storyboard.setRawAiOutput(sb);
                    storyboardMapper.insert(storyboard);

                    // Save shots
                    if (sb.get("shots") instanceof List<?> shotsList) {
                        for (Object s : shotsList) {
                            if (s instanceof Map<?, ?> shotMap) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> sm = (Map<String, Object>) shotMap;
                                StoryboardShotEntity shot = new StoryboardShotEntity();
                                shot.setId(UUID.randomUUID());
                                shot.setStoryboardId(storyboard.getId());
                                shot.setTaskId(task.getId());
                                shot.setUserId(task.getUserId());
                                shot.setShotNo(getInt(sm, "shotNo"));
                                shot.setDuration(getInt(sm, "duration"));
                                shot.setScene((String) sm.get("scene"));
                                shot.setSubtitle((String) sm.get("subtitle"));
                                shot.setMaterialType((String) sm.get("materialType"));
                                shot.setPrompt((String) sm.get("prompt"));
                                shot.setNegativePrompt((String) sm.get("negativePrompt"));
                                storyboardShotMapper.insert(shot);
                            }
                        }
                    }
                }
                advanceTask(task, "script_generated");
            }
            case "material" -> {
                // Materials are saved in the callback handler; Phase 5 will create material records
                advanceTask(task, "material_generating");
                advanceTask(task, "material_generated");
            }
            case "quality_check" -> {
                log.debug("Quality check callback received: taskId={}", task.getId());
            }
            case "render_manifest" -> {
                if (request.getRenderManifest() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> manifest = request.getRenderManifest();
                    task.setRenderManifest(manifest);
                    Object manifestVersion = manifest.get("manifestVersion");
                    if (manifestVersion instanceof String version) {
                        task.setManifestVersion(version);
                    }
                    // Generate render task ID and push to RabbitMQ
                    String renderTaskId = UUID.randomUUID().toString();
                    task.setRenderTaskId(renderTaskId);
                    String correlationId = org.slf4j.MDC.get("correlationId");
                    if (correlationId == null) {
                        correlationId = UUID.randomUUID().toString();
                    }
                    renderMessageProducer.sendRenderTask(
                            task.getId(), renderTaskId, correlationId, manifest);
                }
                advanceTask(task, "rendering");
            /*当 switch 匹配到 "render_manifest" 这个分支时：
提取渲染清单 Map。
若清单中有 manifestVersion 字段且为字符串，则设置到任务对象。
生成一个渲染任务 ID 和链路关联 ID。
将渲染任务发送到消息队列，然后把本地任务状态置为“rendering”。*/
            }
            default -> log.warn("Unknown callback stage: {}", stage);
        }
    }

    private void handleFailed(AiCallbackRequest request, VideoTaskEntity task) {
        //任何阶段失败走这里
        if (request.getError() != null) {
            task.setFailedStage((String) request.getError().get("failedStage"));
            task.setErrorCode((String) request.getError().get("errorCode"));
            task.setErrorMessage((String) request.getError().get("errorMessage"));
            task.setErrorRetryable(Boolean.TRUE.equals(request.getError().get("retryable")));
        }
        advanceTask(task, "failed");
    }
//状态机推进 (line 193-209)
    private void advanceTask(VideoTaskEntity task, String targetStatus) {
        try {
            VideoTaskStateMachine.validateTransition(task.getStatus(), targetStatus);
        } catch (InvalidStateTransitionException e) {
            log.warn("State transition skipped (already advanced?): {} -> {}", task.getStatus(), targetStatus);
            return;
        }
        String previousStatus = task.getStatus();
        task.setStatus(targetStatus);
        task.setUpdatedAt(OffsetDateTime.now());
        if ("completed".equals(targetStatus) || "failed".equals(targetStatus)) {
            task.setCompletedAt(OffsetDateTime.now());
            task.setProgress("completed".equals(targetStatus) ? 100 : task.getProgress());
        }
        videoTaskMapper.updateById(task);
        log.info("Task advanced: taskId={}, {} -> {}", task.getId(), previousStatus, targetStatus);
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private Integer getNullableInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return null;
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
//幂等判断
    private boolean isStageAlreadyProcessed(String stage, String taskStatus) {
        if (stage == null) {
            return false;
        }
        return switch (stage) {
            case "product_analysis" -> !List.of("analyzing", "failed").contains(taskStatus);
            case "video_plan" -> List.of("plan_generated", "waiting_plan_selection", "script_generating",
                    "script_generated", "material_generating", "material_generated", "rendering", "checking",
                    "completed", "exported").contains(taskStatus);
            case "storyboard" -> List.of("script_generated", "material_generating", "material_generated",
                    "rendering", "checking", "completed", "exported").contains(taskStatus);
            case "material" -> List.of("material_generated", "rendering", "checking", "completed", "exported").contains(taskStatus);
            case "quality_check" -> List.of("rendering", "checking", "completed", "exported").contains(taskStatus);
            case "render_manifest" -> List.of("rendering", "checking", "completed", "exported").contains(taskStatus);
            default -> false;
        };
    }
/*product_analysis
允许重试的状态是 analyzing 和 failed。
如果当前状态是 analyzing（正在分析）或 failed（失败），说明还没成功完成分析，可以（甚至应该）再次处理 → 返回 false（未处理）。
如果当前状态是 video_plan、script_generating 等更后面的状态，说明产品分析早就完成并推进到后续流程了，不需要再处理 → 返回 true（已处理，跳过）。 */

}
