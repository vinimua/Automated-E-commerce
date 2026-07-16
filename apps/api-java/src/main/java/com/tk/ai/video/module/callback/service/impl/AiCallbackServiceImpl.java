package com.tk.ai.video.module.callback.service.impl;

import com.tk.ai.video.common.InvalidStateTransitionException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.common.AiServiceClient;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final AiServiceClient aiServiceClient;

    @Override
    @Transactional
    public void handleCallback(AiCallbackRequest request) {
        //入口 + 幂等守卫
        VideoTaskEntity task = videoTaskMapper.selectById(request.getTaskId());
        if (task == null) {
            throw new ResourceNotFoundException("VideoTask", request.getTaskId());
        }

        if (isStageAlreadyProcessed(request, task)) {
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
                if (!"storyboard_generating".equals(task.getStatus())) {
                    log.info("Storyboard callback skipped because task is not generating storyboard: taskId={}, status={}",
                            task.getId(), task.getStatus());
                    return;
                }
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
                advanceTask(task, "waiting_storyboard_confirmation");
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
                if (request.getFashionAssetAnalysis() != null) {
                    task.setAssetAnalysis(new LinkedHashMap<>(request.getFashionAssetAnalysis()));
                    task.setUpdatedAt(OffsetDateTime.now());
                    videoTaskMapper.updateById(task);
                }
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
                    StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId()).orElse(null);
                    Map<Integer, StoryboardShotEntity> shotsByNo = loadStoryboardShotsByNo(storyboard);
                    for (Map<String, Object> kfData : request.getKeyframes()) {
                        int shotNo = getInt(kfData, "shotNo");
                        String kfStatus = (String) kfData.get("status");
                        StoryboardShotEntity shot = shotsByNo.get(shotNo);

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
                        if (storyboard != null) {
                            kf.setStoryboardId(storyboard.getId());
                        }
                        if (shot != null) {
                            kf.setShotId(shot.getId());
                        }

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
                completeActiveRepairEvent(task, "keyframe");
            }
            case "video_clip" -> {
                // AI generated video clips — save to video_clips table
                if (request.getClips() != null) {
                    StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId()).orElse(null);
                    Map<Integer, StoryboardShotEntity> shotsByNo = loadStoryboardShotsByNo(storyboard);
                    Map<Integer, KeyframeEntity> keyframesByNo = keyframeMapper
                            .findByTaskIdAndVersion(task.getId(), task.getCurrentVersion())
                            .stream()
                            .filter(k -> "confirmed".equals(k.getStatus()))
                            .collect(java.util.stream.Collectors.toMap(KeyframeEntity::getShotNo, k -> k, (left, right) -> right));
                    for (Map<String, Object> clipData : request.getClips()) {
                        int shotNo = getInt(clipData, "shotNo");
                        String clipStatus = (String) clipData.get("status");
                        StoryboardShotEntity shot = shotsByNo.get(shotNo);
                        KeyframeEntity keyframe = keyframesByNo.get(shotNo);

                        if ("completed".equals(clipStatus)) {
                            List<VideoClipEntity> existing = videoClipMapper.findByTaskIdAndVersion(task.getId(), task.getCurrentVersion()).stream()
                                    .filter(c -> c.getShotNo() == shotNo)
                                    .collect(java.util.stream.Collectors.toList());

                            VideoClipEntity clip;
                            if (!existing.isEmpty()) {
                                clip = existing.get(0);
                            } else {
                                clip = new VideoClipEntity();
                                clip.setId(java.util.UUID.randomUUID());
                                clip.setTaskId(task.getId());
                                clip.setUserId(task.getUserId());
                                clip.setShotNo(shotNo);
                                clip.setVersion(task.getCurrentVersion());
                            }
                            if (storyboard != null) {
                                clip.setStoryboardId(storyboard.getId());
                            }
                            if (shot != null) {
                                clip.setShotId(shot.getId());
                            }
                            if (keyframe != null) {
                                clip.setKeyframeId(keyframe.getId());
                            }
                            clip.setSource("ai_generated");
                            clip.setUrl((String) clipData.get("url"));
                            clip.setPrompt((String) clipData.get("prompt"));
                            clip.setNegativePrompt((String) clipData.get("negativePrompt"));
                            clip.setProvider((String) clipData.get("provider"));
                            clip.setModelName((String) clipData.get("modelName"));
                            clip.setDuration(getInt(clipData, "duration"));
                            clip.setVersion(task.getCurrentVersion());
                            clip.setStatus("generated");
                            clip.setErrorMessage(null);
                            clip.setUpdatedAt(java.time.OffsetDateTime.now());

                            if (existing.isEmpty()) {
                                videoClipMapper.insert(clip);
                            } else {
                                videoClipMapper.updateById(clip);
                            }

                            // Consume video_clip quota for AI-generated clip (idempotent)
                            String idempotencyKey = "videoclip:" + clip.getId() + ":video_clip:consume";
                            quotaService.consumeQuota(task.getUserId(), task.getId(), "video_clip", 1, idempotencyKey);
                        } else if ("failed".equals(clipStatus)) {
                            List<VideoClipEntity> existing = videoClipMapper.findByTaskIdAndVersion(task.getId(), task.getCurrentVersion()).stream()
                                    .filter(c -> c.getShotNo() == shotNo)
                                    .collect(java.util.stream.Collectors.toList());

                            VideoClipEntity clip;
                            if (!existing.isEmpty()) {
                                clip = existing.get(0);
                            } else {
                                clip = new VideoClipEntity();
                                clip.setId(java.util.UUID.randomUUID());
                                clip.setTaskId(task.getId());
                                clip.setUserId(task.getUserId());
                                clip.setShotNo(shotNo);
                            }
                            if (storyboard != null) {
                                clip.setStoryboardId(storyboard.getId());
                            }
                            if (shot != null) {
                                clip.setShotId(shot.getId());
                            }
                            if (keyframe != null) {
                                clip.setKeyframeId(keyframe.getId());
                            }
                            clip.setSource("ai_generated");
                            clip.setUrl(null);
                            clip.setPrompt((String) clipData.get("prompt"));
                            clip.setNegativePrompt((String) clipData.get("negativePrompt"));
                            clip.setProvider((String) clipData.get("provider"));
                            clip.setModelName((String) clipData.get("modelName"));
                            clip.setDuration(getInt(clipData, "duration"));
                            clip.setVersion(task.getCurrentVersion());
                            clip.setStatus("failed");
                            clip.setErrorMessage((String) clipData.get("errorMessage"));
                            clip.setUpdatedAt(java.time.OffsetDateTime.now());

                            if (existing.isEmpty()) {
                                videoClipMapper.insert(clip);
                            } else {
                                videoClipMapper.updateById(clip);
                            }
                        }
                    }
                }
                advanceTask(task, "waiting_video_clip_confirmation");
                completeActiveRepairEvent(task, "video_clip");
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
                if (request.getRepairResult() != null) {
                    dispatchRepair(request.getRepairResult(), task);
                    return;
                }
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

    private void dispatchRepair(Map<String, Object> repairResult, VideoTaskEntity task) {
        String targetType = String.valueOf(repairResult.getOrDefault("targetType", "final_video"));
        Set<Integer> affectedShotNos = resolveAffectedShotNos(repairResult, task);

        switch (targetType) {
            case "keyframe" -> dispatchKeyframeRepair(task, repairResult, affectedShotNos);
            case "video_clip" -> dispatchVideoClipRepair(task, repairResult, affectedShotNos);
            case "render_manifest", "final_video" -> dispatchRenderRepair(task, repairResult);
            default -> {
                updateRepairEventPlan(task, repairResult, task.getCurrentVersion());
                advanceTask(task, "keyframe_configuring");
            }
        }
    }

    private void dispatchKeyframeRepair(VideoTaskEntity task, Map<String, Object> repairResult, Set<Integer> affectedShotNos) {
        int previousVersion = task.getCurrentVersion();
        int nextVersion = previousVersion + 1;
        StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Storyboard", task.getId()));
        Map<Integer, StoryboardShotEntity> shotsByNo = loadStoryboardShotsByNo(storyboard);
        Map<Integer, KeyframeEntity> previousKeyframes = keyframeMapper.findByTaskIdAndVersion(task.getId(), previousVersion).stream()
                .collect(Collectors.toMap(KeyframeEntity::getShotNo, k -> k, (left, right) -> right));
        Map<Integer, VideoClipEntity> previousClips = videoClipMapper.findByTaskIdAndVersion(task.getId(), previousVersion).stream()
                .collect(Collectors.toMap(VideoClipEntity::getShotNo, c -> c, (left, right) -> right));

        for (StoryboardShotEntity shot : shotsByNo.values()) {
            KeyframeEntity previous = previousKeyframes.get(shot.getShotNo());
            if (affectedShotNos.contains(shot.getShotNo())) {
                upsertRepairKeyframe(task, storyboard, shot, previous, nextVersion, repairResult);
            } else if (previous != null) {
                KeyframeEntity clonedKeyframe = cloneKeyframe(previous, nextVersion);
                VideoClipEntity previousClip = previousClips.get(shot.getShotNo());
                if (previousClip != null && "confirmed".equals(previousClip.getStatus())) {
                    cloneVideoClip(previousClip, nextVersion, clonedKeyframe.getId());
                }
            }
        }

        task.setCurrentVersion(nextVersion);
        task.setStatus("image_generating");
        task.setProgress(60);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);
        updateRepairEventPlan(task, repairResult, nextVersion);

        Map<String, Object> storyboardPayload = buildStoryboardPayload(storyboard, shotsByNo.values().stream().toList(), affectedShotNos);
        storyboardPayload.put("repairContext", repairResult);
        aiServiceClient.startKeyframeGeneration(task.getId(), task.getProductId(), task.getUserId(), storyboardPayload);
        log.info("Repair dispatched to keyframe generation: taskId={}, version={}, affectedShots={}",
                task.getId(), nextVersion, affectedShotNos);
    }

    private void dispatchVideoClipRepair(VideoTaskEntity task, Map<String, Object> repairResult, Set<Integer> affectedShotNos) {
        int previousVersion = task.getCurrentVersion();
        int nextVersion = previousVersion + 1;
        StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Storyboard", task.getId()));
        Map<Integer, StoryboardShotEntity> shotsByNo = loadStoryboardShotsByNo(storyboard);
        Map<Integer, KeyframeEntity> previousKeyframes = keyframeMapper.findByTaskIdAndVersion(task.getId(), previousVersion).stream()
                .filter(k -> "confirmed".equals(k.getStatus()))
                .collect(Collectors.toMap(KeyframeEntity::getShotNo, k -> k, (left, right) -> right));
        Map<Integer, VideoClipEntity> previousClips = videoClipMapper.findByTaskIdAndVersion(task.getId(), previousVersion).stream()
                .collect(Collectors.toMap(VideoClipEntity::getShotNo, c -> c, (left, right) -> right));

        Map<Integer, KeyframeEntity> nextKeyframes = new LinkedHashMap<>();
        for (KeyframeEntity previous : previousKeyframes.values()) {
            nextKeyframes.put(previous.getShotNo(), cloneKeyframe(previous, nextVersion));
        }
        for (StoryboardShotEntity shot : shotsByNo.values()) {
            VideoClipEntity previousClip = previousClips.get(shot.getShotNo());
            if (affectedShotNos.contains(shot.getShotNo())) {
                KeyframeEntity keyframe = nextKeyframes.get(shot.getShotNo());
                if (keyframe != null) {
                    upsertRepairVideoClip(task, storyboard, shot, keyframe, previousClip, nextVersion, repairResult);
                }
            } else if (previousClip != null && "confirmed".equals(previousClip.getStatus())) {
                KeyframeEntity keyframe = nextKeyframes.get(shot.getShotNo());
                cloneVideoClip(previousClip, nextVersion, keyframe != null ? keyframe.getId() : previousClip.getKeyframeId());
            }
        }

        task.setCurrentVersion(nextVersion);
        task.setStatus("video_clip_generating");
        task.setProgress(75);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);
        updateRepairEventPlan(task, repairResult, nextVersion);

        Map<String, Object> storyboardPayload = buildStoryboardPayload(storyboard, shotsByNo.values().stream().toList(), affectedShotNos);
        storyboardPayload.put("repairContext", repairResult);
        aiServiceClient.startVideoClipGeneration(
                task.getId(),
                task.getProductId(),
                task.getUserId(),
                storyboardPayload,
                buildKeyframePayload(nextKeyframes.values().stream().toList(), affectedShotNos, nextVersion)
        );
        log.info("Repair dispatched to video clip generation: taskId={}, version={}, affectedShots={}",
                task.getId(), nextVersion, affectedShotNos);
    }

    private void dispatchRenderRepair(VideoTaskEntity task, Map<String, Object> repairResult) {
        Map<String, Object> manifest = task.getRenderManifest();
        if (manifest == null || manifest.isEmpty()) {
            log.warn("Repair requested render but task has no renderManifest: taskId={}", task.getId());
            advanceTask(task, "failed");
            return;
        }
        Map<String, Object> nextManifest = new LinkedHashMap<>(manifest);
        nextManifest.put("repairContext", repairResult);
        String renderTaskId = UUID.randomUUID().toString();
        task.setRenderTaskId(renderTaskId);
        task.setRenderManifest(nextManifest);
        task.setStatus("rendering");
        task.setProgress(90);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);
        updateRepairEventPlan(task, repairResult, task.getCurrentVersion());

        String correlationId = org.slf4j.MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        renderMessageProducer.sendRenderTask(task.getId(), renderTaskId, correlationId, nextManifest);
        log.info("Repair dispatched to render worker: taskId={}, renderTaskId={}", task.getId(), renderTaskId);
    }

    private void updateRepairEventPlan(VideoTaskEntity task, Map<String, Object> repairResult, int afterVersion) {
        Object repairEventId = repairResult.getOrDefault("repairEventId", repairResult.get("targetId"));
        if (repairEventId == null) {
            return;
        }
        try {
            RepairEventEntity repairEvent = repairEventMapper.selectById(UUID.fromString(String.valueOf(repairEventId)));
            if (repairEvent != null) {
                repairEvent.setStatus("in_progress");
                repairEvent.setAfterVersion(afterVersion);
                repairEvent.setRepairPlan(repairResult);
                repairEvent.setUpdatedAt(OffsetDateTime.now());
                repairEventMapper.updateById(repairEvent);
            }
        } catch (IllegalArgumentException ignored) {
            log.warn("Repair event id is not a UUID: taskId={}, repairEventId={}", task.getId(), repairEventId);
        }
    }

    private void completeActiveRepairEvent(VideoTaskEntity task, String completedTargetType) {
        repairEventMapper.findByTaskId(task.getId()).stream()
                .filter(event -> "in_progress".equals(event.getStatus()))
                .filter(event -> completedTargetType == null || completedTargetType.equals(event.getTargetType()))
                .findFirst()
                .ifPresent(event -> {
                    event.setStatus("completed");
                    event.setAfterVersion(task.getCurrentVersion());
                    event.setUpdatedAt(OffsetDateTime.now());
                    repairEventMapper.updateById(event);
                });
    }

    private Set<Integer> resolveAffectedShotNos(Map<String, Object> repairResult, VideoTaskEntity task) {
        Set<Integer> affected = new java.util.LinkedHashSet<>();
        Object value = repairResult.get("affectedShots");
        if (value instanceof List<?> values) {
            for (Object item : values) {
                if (item instanceof Number number) {
                    affected.add(number.intValue());
                } else if (item instanceof String text) {
                    try {
                        affected.add(Integer.parseInt(text));
                    } catch (NumberFormatException ignored) {
                        // ignore invalid shot numbers
                    }
                }
            }
        }
        if (!affected.isEmpty()) {
            return affected;
        }
        StoryboardEntity storyboard = storyboardMapper.findByTaskId(task.getId()).orElse(null);
        if (storyboard == null) {
            return affected;
        }
        return storyboardShotMapper.findByStoryboardId(storyboard.getId()).stream()
                .map(StoryboardShotEntity::getShotNo)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private void upsertRepairKeyframe(VideoTaskEntity task, StoryboardEntity storyboard, StoryboardShotEntity shot,
                                      KeyframeEntity previous, int version, Map<String, Object> repairResult) {
        KeyframeEntity keyframe = new KeyframeEntity();
        keyframe.setId(UUID.randomUUID());
        keyframe.setTaskId(task.getId());
        keyframe.setStoryboardId(storyboard.getId());
        keyframe.setShotId(shot.getId());
        keyframe.setUserId(task.getUserId());
        keyframe.setShotNo(shot.getShotNo());
        keyframe.setSource("ai_generated");
        keyframe.setImagePurpose(previous != null && previous.getImagePurpose() != null ? previous.getImagePurpose() : "first_frame");
        keyframe.setPrompt(shot.getPrompt());
        keyframe.setNegativePrompt(shot.getNegativePrompt());
        keyframe.setUserInstruction((String) repairResult.getOrDefault("repairNotes", ""));
        keyframe.setStatus("generating");
        keyframe.setVersion(version);
        keyframe.setMetadata(Map.of("repair", repairResult));
        keyframe.setUpdatedAt(OffsetDateTime.now());
        keyframeMapper.insert(keyframe);
    }

    private KeyframeEntity cloneKeyframe(KeyframeEntity source, int version) {
        KeyframeEntity clone = new KeyframeEntity();
        clone.setId(UUID.randomUUID());
        clone.setTaskId(source.getTaskId());
        clone.setStoryboardId(source.getStoryboardId());
        clone.setShotId(source.getShotId());
        clone.setUserId(source.getUserId());
        clone.setShotNo(source.getShotNo());
        clone.setSource(source.getSource());
        clone.setAssetId(source.getAssetId());
        clone.setMaterialId(source.getMaterialId());
        clone.setImagePurpose(source.getImagePurpose());
        clone.setUrl(source.getUrl());
        clone.setPrompt(source.getPrompt());
        clone.setNegativePrompt(source.getNegativePrompt());
        clone.setUserInstruction(source.getUserInstruction());
        clone.setProvider(source.getProvider());
        clone.setModelName(source.getModelName());
        clone.setStatus(source.getStatus());
        clone.setVersion(version);
        clone.setErrorMessage(source.getErrorMessage());
        clone.setMetadata(source.getMetadata());
        clone.setUpdatedAt(OffsetDateTime.now());
        keyframeMapper.insert(clone);
        return clone;
    }

    private void upsertRepairVideoClip(VideoTaskEntity task, StoryboardEntity storyboard, StoryboardShotEntity shot,
                                       KeyframeEntity keyframe, VideoClipEntity previous, int version,
                                       Map<String, Object> repairResult) {
        VideoClipEntity clip = new VideoClipEntity();
        clip.setId(UUID.randomUUID());
        clip.setTaskId(task.getId());
        clip.setStoryboardId(storyboard.getId());
        clip.setShotId(shot.getId());
        clip.setKeyframeId(keyframe.getId());
        clip.setUserId(task.getUserId());
        clip.setShotNo(shot.getShotNo());
        clip.setSource("ai_generated");
        clip.setPrompt(previous != null && previous.getPrompt() != null ? previous.getPrompt() : keyframe.getPrompt());
        clip.setNegativePrompt(previous != null ? previous.getNegativePrompt() : null);
        clip.setStatus("generating");
        clip.setDuration(shot.getDuration());
        clip.setVersion(version);
        clip.setMetadata(Map.of("repair", repairResult));
        clip.setUpdatedAt(OffsetDateTime.now());
        videoClipMapper.insert(clip);
    }

    private VideoClipEntity cloneVideoClip(VideoClipEntity source, int version, UUID keyframeId) {
        VideoClipEntity clone = new VideoClipEntity();
        clone.setId(UUID.randomUUID());
        clone.setTaskId(source.getTaskId());
        clone.setStoryboardId(source.getStoryboardId());
        clone.setShotId(source.getShotId());
        clone.setKeyframeId(keyframeId != null ? keyframeId : source.getKeyframeId());
        clone.setUserId(source.getUserId());
        clone.setShotNo(source.getShotNo());
        clone.setSource(source.getSource());
        clone.setUrl(source.getUrl());
        clone.setPrompt(source.getPrompt());
        clone.setNegativePrompt(source.getNegativePrompt());
        clone.setProvider(source.getProvider());
        clone.setModelName(source.getModelName());
        clone.setStatus(source.getStatus());
        clone.setDuration(source.getDuration());
        clone.setVersion(version);
        clone.setErrorMessage(source.getErrorMessage());
        clone.setMetadata(source.getMetadata());
        clone.setUpdatedAt(OffsetDateTime.now());
        videoClipMapper.insert(clone);
        return clone;
    }

    private Map<String, Object> buildStoryboardPayload(StoryboardEntity storyboard, List<StoryboardShotEntity> shots, Set<Integer> targetShotNos) {
        List<Map<String, Object>> shotMaps = shots.stream()
                .filter(s -> targetShotNos == null || targetShotNos.isEmpty() || targetShotNos.contains(s.getShotNo()))
                .map(s -> {
                    Map<String, Object> shot = new LinkedHashMap<>();
                    shot.put("shotId", s.getId().toString());
                    shot.put("shotNo", s.getShotNo());
                    shot.put("duration", s.getDuration());
                    shot.put("scene", s.getScene() != null ? s.getScene() : "");
                    shot.put("action", s.getAction() != null ? s.getAction() : "");
                    shot.put("subtitle", s.getSubtitle() != null ? s.getSubtitle() : "");
                    shot.put("materialType", s.getMaterialType() != null ? s.getMaterialType() : "ai_image");
                    shot.put("prompt", s.getPrompt() != null ? s.getPrompt() : "");
                    shot.put("negativePrompt", s.getNegativePrompt() != null ? s.getNegativePrompt() : "");
                    shot.put("editInstruction", s.getEditInstruction() != null ? s.getEditInstruction() : "");
                    return shot;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", storyboard.getTitle() != null ? storyboard.getTitle() : "");
        payload.put("hook", storyboard.getHook() != null ? storyboard.getHook() : "");
        payload.put("caption", storyboard.getCaption() != null ? storyboard.getCaption() : "");
        payload.put("hashtags", storyboard.getHashtags() != null ? storyboard.getHashtags() : List.of());
        payload.put("coverText", storyboard.getCoverText() != null ? storyboard.getCoverText() : "");
        payload.put("musicSuggestion", storyboard.getMusicSuggestion() != null ? storyboard.getMusicSuggestion() : "");
        payload.put("shots", shotMaps);
        if (targetShotNos != null && !targetShotNos.isEmpty()) {
            payload.put("targetShotNos", targetShotNos.stream().sorted().collect(Collectors.toList()));
        }
        return payload;
    }

    private Map<String, Object> buildKeyframePayload(List<KeyframeEntity> keyframes, Set<Integer> targetShotNos, int version) {
        List<Map<String, Object>> items = keyframes.stream()
                .filter(k -> targetShotNos == null || targetShotNos.isEmpty() || targetShotNos.contains(k.getShotNo()))
                .map(k -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", k.getId().toString());
                    item.put("shotId", k.getShotId() != null ? k.getShotId().toString() : null);
                    item.put("shotNo", k.getShotNo());
                    item.put("imagePurpose", k.getImagePurpose());
                    item.put("url", k.getUrl());
                    item.put("prompt", k.getPrompt());
                    item.put("source", k.getSource());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", version);
        payload.put("keyframes", items);
        if (targetShotNos != null && !targetShotNos.isEmpty()) {
            payload.put("targetShotNos", targetShotNos.stream().sorted().collect(Collectors.toList()));
        }
        return payload;
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

    private Map<Integer, StoryboardShotEntity> loadStoryboardShotsByNo(StoryboardEntity storyboard) {
        Map<Integer, StoryboardShotEntity> shotsByNo = new LinkedHashMap<>();
        if (storyboard == null) {
            return shotsByNo;
        }
        for (StoryboardShotEntity shot : storyboardShotMapper.findByStoryboardId(storyboard.getId())) {
            shotsByNo.put(shot.getShotNo(), shot);
        }
        return shotsByNo;
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
            case "storyboard" -> List.of("waiting_storyboard_confirmation", "keyframe_configuring",
                    "image_generating", "waiting_image_confirmation", "video_clip_generating",
                    "waiting_video_clip_confirmation", "rendering", "waiting_final_review",
                    "script_generated", "material_generating", "material_generated", "checking",
                    "completed", "exported").contains(taskStatus);
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

    private boolean isStageAlreadyProcessed(AiCallbackRequest request, VideoTaskEntity task) {
        if ("asset_analysis".equals(request.getStage())
                && "success".equals(request.getStatus())
                && task.getAssetAnalysis() == null
                && request.getFashionAssetAnalysis() != null) {
            return false;
        }
        return isStageAlreadyProcessed(request.getStage(), task.getStatus());
    }
/*product_analysis
逻辑： 如果任务状态已经"走过"了当前 stage 对应的位置，就认为已经处理过。不需要查数据库有没有重复记录——看任务状态就知道。
允许重试的状态是 analyzing 和 failed。
如果当前状态是 analyzing（正在分析）或 failed（失败），说明还没成功完成分析，可以（甚至应该）再次处理 → 返回 false（未处理）。
如果当前状态是 video_plan、script_generating 等更后面的状态，说明产品分析早就完成并推进到后续流程了，不需要再处理 → 返回 true（已处理，跳过）。 */

}
