package com.tk.ai.video.module.callback.service.impl;

import com.tk.ai.video.common.InvalidStateTransitionException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.callback.dto.AiCallbackRequest;
import com.tk.ai.video.module.callback.service.AiCallbackService;
import com.tk.ai.video.module.creativestate.entity.CreativeStateEntity;
import com.tk.ai.video.module.creativestate.mapper.CreativeStateMapper;
import com.tk.ai.video.module.keyframe.entity.KeyframeEntity;
import com.tk.ai.video.module.keyframe.mapper.KeyframeMapper;
import com.tk.ai.video.module.product.entity.ProductEntity;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.quota.service.QuotaService;
import com.tk.ai.video.module.repairevent.entity.RepairEventEntity;
import com.tk.ai.video.module.repairevent.mapper.RepairEventMapper;
import com.tk.ai.video.module.storyboard.entity.*;
import com.tk.ai.video.module.storyboard.mapper.*;
import com.tk.ai.video.module.videoclip.entity.VideoClipEntity;
import com.tk.ai.video.module.videoclip.mapper.VideoClipMapper;
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
    private final RenderMessageProducer renderMessageProducer;// RabbitMQ 推送

    // Fashion Creative Loop V1 mappers
    private final KeyframeMapper keyframeMapper;
    private final VideoClipMapper videoClipMapper;
    private final CreativeStateMapper creativeStateMapper;// 创意状态表
    private final RepairEventMapper repairEventMapper;// 修复事件表
    private final QuotaService quotaService;

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
        //每一个 case 做三件事：解析数据 → 写入数据库 → 推进任务状态。
        switch (stage) {
            case "product_analysis" -> {
                // Python 分析了商品 → 把分析结果写回 ProductEntity
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
                //Python 生成了视频方案 → 写入 video_plans 表 → 推进到等待用户选择

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
             //   Python 构建了渲染清单 → 保存到 task + 推 RabbitMQ → Render Worker 消费

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
                    //推 RabbitMQ
                    renderMessageProducer.sendRenderTask(
                            task.getId(), renderTaskId, correlationId, manifest);
                }
                advanceTask(task, "rendering");
            }
            // ── Fashion Creative Loop V1 stage handlers ──
            case "asset_analysis" -> {
                // AI analyzed uploaded assets — advance to confirmation
                advanceTask(task, "waiting_asset_confirmation");
            }
            case "reference_analysis" -> {
                // AI analyzed reference video — save to creative_state if not exists
                if (request.getReferenceAnalysis() != null) {
                    CreativeStateEntity cs = creativeStateMapper.findByTaskId(task.getId())
                            .orElseGet(() -> {
                                CreativeStateEntity newCs = new CreativeStateEntity();
                                newCs.setId(java.util.UUID.randomUUID());
                                newCs.setTaskId(task.getId());
                                newCs.setUserId(task.getUserId());
                                newCs.setVersion(1);
                                return newCs;
                            });
                    cs.setReferenceVideoJson(request.getReferenceAnalysis());
                    cs.setUpdatedAt(java.time.OffsetDateTime.now());
                    if (creativeStateMapper.selectById(cs.getId()) != null) {
                        creativeStateMapper.updateById(cs);
                    } else {
                        creativeStateMapper.insert(cs);
                    }
                }
                advanceTask(task, "plan_generating");
            }
            case "creative_plan" -> {
                // AI generated creative plans — save as video plans
                if (request.getPlans() != null) {
                    List<Map<String, Object>> planList = request.getPlans();
                    if (planList != null) {
                        for (Map<String, Object> planData : planList) {
                            VideoPlanEntity plan = new VideoPlanEntity();
                            plan.setId(java.util.UUID.randomUUID());
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
                }
                advanceTask(task, "plan_generated");
                advanceTask(task, "waiting_plan_selection");
            }
            case "keyframe" -> {
                // AI generated keyframes — save to keyframes table
                if (request.getKeyframes() != null) {
                    for (Map<String, Object> kfData : request.getKeyframes()) {
                        int shotNo = getInt(kfData, "shotNo");
                        String kfStatus = (String) kfData.get("status");

                        KeyframeEntity kf = keyframeMapper
                                .findByTaskIdAndShotNoAndVersion(task.getId(), shotNo, task.getCurrentVersion())
                                .orElseGet(() -> {
                                    KeyframeEntity created = new KeyframeEntity();
                                    created.setId(java.util.UUID.randomUUID());
                                    created.setTaskId(task.getId());
                                    created.setUserId(task.getUserId());
                                    created.setShotNo(shotNo);
                                    created.setImagePurpose("first_frame");
                                    created.setVersion(task.getCurrentVersion());
                                    return created;
                                });
                        boolean isNew = kf.getCreatedAt() == null;

                        if ("completed".equals(kfStatus)) {
                            kf.setSource("ai_generated");
                            kf.setImagePurpose((String) kfData.getOrDefault("imagePurpose", "first_frame"));
                            kf.setUrl((String) kfData.get("url"));
                            kf.setPrompt((String) kfData.get("prompt"));
                            kf.setNegativePrompt((String) kfData.get("negativePrompt"));
                            kf.setProvider((String) kfData.get("provider"));
                            kf.setModelName((String) kfData.get("modelName"));
                            kf.setStatus("generated");
                            kf.setErrorMessage(null);
                            kf.setUpdatedAt(java.time.OffsetDateTime.now());

                            if (isNew) {
                                keyframeMapper.insert(kf);
                            } else {
                                keyframeMapper.updateById(kf);
                            }

                            // Consume image quota for AI-generated keyframe (idempotent)
                            //idempotencyKey 保证同一次生成重复回调不重复扣费
                            String idempotencyKey = "keyframe:" + kf.getId() + ":image:consume";
                            quotaService.consumeQuota(task.getUserId(), task.getId(), "image", 1, idempotencyKey);
                        } else if ("failed".equals(kfStatus)) {
                            kf.setSource("ai_generated");
                            kf.setImagePurpose((String) kfData.getOrDefault("imagePurpose", "first_frame"));
                            kf.setPrompt((String) kfData.get("prompt"));
                            kf.setNegativePrompt((String) kfData.get("negativePrompt"));
                            kf.setProvider((String) kfData.get("provider"));
                            kf.setModelName((String) kfData.get("modelName"));
                            kf.setStatus("failed");
                            kf.setErrorMessage((String) kfData.get("errorMessage"));
                            kf.setUpdatedAt(java.time.OffsetDateTime.now());

                            if (isNew) {
                                keyframeMapper.insert(kf);
                            } else {
                                keyframeMapper.updateById(kf);
                            }
                        }
                    }
                }
                advanceTask(task, "waiting_image_confirmation");
            }
            case "video_clip" -> {
                // AI generated video clips — save to video_clips table
                if (request.getClips() != null) {
                    for (Map<String, Object> clipData : request.getClips()) {
                        int shotNo = getInt(clipData, "shotNo");
                        String clipStatus = (String) clipData.get("status");

                        if ("completed".equals(clipStatus)) {
                            List<VideoClipEntity> existing = videoClipMapper.findByTaskId(task.getId()).stream()
                                    .filter(c -> c.getShotNo() == shotNo)
                                    .collect(java.util.stream.Collectors.toList());

                            VideoClipEntity clip;
                            if (!existing.isEmpty()) {
                                clip = existing.get(0);
                                clip.setVersion(clip.getVersion() + 1);
                            } else {
                                clip = new VideoClipEntity();
                                clip.setId(java.util.UUID.randomUUID());
                                clip.setTaskId(task.getId());
                                clip.setUserId(task.getUserId());
                                clip.setShotNo(shotNo);
                                clip.setVersion(task.getCurrentVersion());
                            }
                            clip.setSource("ai_generated");
                            clip.setUrl((String) clipData.get("url"));
                            clip.setPrompt((String) clipData.get("prompt"));
                            clip.setNegativePrompt((String) clipData.get("negativePrompt"));
                            clip.setProvider((String) clipData.get("provider"));
                            clip.setModelName((String) clipData.get("modelName"));
                            clip.setDuration(getInt(clipData, "duration"));
                            clip.setStatus("generated");
                            clip.setUpdatedAt(java.time.OffsetDateTime.now());

                            if (existing.isEmpty()) {
                                videoClipMapper.insert(clip);
                            } else {
                                videoClipMapper.updateById(clip);
                            }

                            // Consume video_clip quota for AI-generated clip (idempotent)
                            String idempotencyKey = "videoclip:" + clip.getId() + ":video_clip:consume";
                            quotaService.consumeQuota(task.getUserId(), task.getId(), "video_clip", 1, idempotencyKey);
                        }
                    }
                }
                advanceTask(task, "waiting_video_clip_confirmation");
            }
            case "qa" -> {
                // Quality check result — advance based on outcome
                if (request.getQaResult() != null) {
                    Boolean passed = (Boolean) request.getQaResult().get("passed");
                    if (Boolean.TRUE.equals(passed)) {
                        advanceTask(task, "waiting_final_review");
                    } else {
                        advanceTask(task, "repairing");
                    }
                } else {
                    advanceTask(task, "waiting_final_review");
                }
            }
            case "repair" -> {
                // Python 修复完成 → 更新 repair_event → version+1 → 跳回生成状态

                if (request.getRepairResult() != null) {
                    String targetType = (String) request.getRepairResult().get("targetType");
                    // Update the repair event status
                    String targetId = (String) request.getRepairResult().getOrDefault(
                            "repairEventId", request.getRepairResult().get("targetId"));
                    if (targetId != null) {
                        try {
                            RepairEventEntity repairEvent = repairEventMapper.selectById(java.util.UUID.fromString(targetId));
                            if (repairEvent != null) {
                                repairEvent.setStatus("completed");
                                repairEvent.setAfterVersion(task.getCurrentVersion() + 1);
                                repairEvent.setRepairPlan(request.getRepairResult());
                                repairEvent.setUpdatedAt(java.time.OffsetDateTime.now());
                                repairEventMapper.updateById(repairEvent);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // targetId is not a UUID — skip update
                        }
                    }
                    task.setCurrentVersion(task.getCurrentVersion() + 1);

                    // Route to the appropriate retry target based on repair target
                    String retryTarget = determineRepairTargetStatus(targetType);
                    advanceTask(task, retryTarget);
                } else {
                    advanceTask(task, "keyframe_configuring");
                }
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
            //如果当前状态 → 目标状态 不是合法跳转 → InvalidStateTransitionException
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
    //修复不是跳回 repairing 自身，而是跳到具体要重新生成的环节。
    private String determineRepairTargetStatus(String targetType) {
        if (targetType == null) {
            return "keyframe_configuring";
        }
        return switch (targetType) {
            case "keyframe" -> "image_generating";
            case "video_clip" -> "video_clip_generating";
            case "render_manifest", "final_video" -> "rendering";
            default -> "keyframe_configuring";
        };
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
            // Fashion Creative Loop V1 idempotency guards
            case "asset_analysis" -> List.of("waiting_asset_confirmation", "reference_analyzing", "plan_generating",
                    "waiting_plan_selection", "storyboard_generating", "waiting_storyboard_confirmation",
                    "keyframe_configuring", "image_generating", "waiting_image_confirmation",
                    "video_clip_generating", "waiting_video_clip_confirmation", "rendering",
                    "completed", "exported").contains(taskStatus);
            case "reference_analysis" -> List.of("plan_generating", "waiting_plan_selection",
                    "storyboard_generating", "waiting_storyboard_confirmation",
                    "keyframe_configuring", "image_generating", "waiting_image_confirmation",
                    "video_clip_generating", "waiting_video_clip_confirmation", "rendering",
                    "completed", "exported").contains(taskStatus);
            case "creative_plan" -> List.of("plan_generated", "waiting_plan_selection",
                    "storyboard_generating", "waiting_storyboard_confirmation",
                    "keyframe_configuring", "image_generating", "waiting_image_confirmation",
                    "video_clip_generating", "waiting_video_clip_confirmation", "rendering",
                    "completed", "exported").contains(taskStatus);
            case "keyframe" -> List.of("waiting_image_confirmation",
                    "video_clip_generating", "waiting_video_clip_confirmation", "rendering",
                    "completed", "exported").contains(taskStatus);
            case "video_clip" -> List.of("waiting_video_clip_confirmation", "rendering",
                    "completed", "exported").contains(taskStatus);
            case "qa" -> List.of("waiting_final_review", "rendering", "completed", "exported").contains(taskStatus);
            case "repair" -> List.of("completed", "exported").contains(taskStatus);
            default -> false;
        };
    }
/*product_analysis
逻辑： 如果任务状态已经"走过"了当前 stage 对应的位置，就认为已经处理过。不需要查数据库有没有重复记录——看任务状态就知道。
允许重试的状态是 analyzing 和 failed。
如果当前状态是 analyzing（正在分析）或 failed（失败），说明还没成功完成分析，可以（甚至应该）再次处理 → 返回 false（未处理）。
如果当前状态是 video_plan、script_generating 等更后面的状态，说明产品分析早就完成并推进到后续流程了，不需要再处理 → 返回 true（已处理，跳过）。 */

}
