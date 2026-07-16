package com.tk.ai.video.module.taskasset.service.impl;

import com.tk.ai.video.common.AiServiceClient;
import com.tk.ai.video.common.BusinessException;
import com.tk.ai.video.common.CreativeContextAssembler;
import com.tk.ai.video.common.ResourceForbiddenException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.taskasset.dto.ConfirmAssetsRequest;
import com.tk.ai.video.module.taskasset.dto.CreateAssetRequest;
import com.tk.ai.video.module.taskasset.dto.GenerateAssetImageRequest;
import com.tk.ai.video.module.taskasset.dto.RegenerateAssetImageRequest;
import com.tk.ai.video.module.taskasset.dto.TaskAssetResponse;
import com.tk.ai.video.module.taskasset.dto.UpdateAssetRoleRequest;
import com.tk.ai.video.module.taskasset.entity.TaskAssetEntity;
import com.tk.ai.video.module.taskasset.mapper.TaskAssetMapper;
import com.tk.ai.video.module.taskasset.service.TaskAssetService;
import com.tk.ai.video.module.videotask.dto.VideoTaskStatusResponse;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import com.tk.ai.video.module.videotask.mapper.VideoTaskMapper;
import com.tk.ai.video.module.videotask.state.VideoTaskStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssetServiceImpl implements TaskAssetService {

    private static final Set<String> ASSET_UPLOADABLE_STATUSES = Set.of(
            "asset_uploading", "waiting_asset_confirmation", "keyframe_configuring"
    );

    private final TaskAssetMapper taskAssetMapper;
    private final VideoTaskMapper videoTaskMapper;
    private final AiServiceClient aiServiceClient;
    private final CreativeContextAssembler creativeContextAssembler;
    private final com.tk.ai.video.module.creativestate.mapper.CreativeStateMapper creativeStateMapper;

    @Override
    public List<TaskAssetResponse> getAssets(UUID taskId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);
        return taskAssetMapper.findByTaskId(taskId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<TaskAssetResponse> addAsset(UUID taskId, CreateAssetRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!ASSET_UPLOADABLE_STATUSES.contains(task.getStatus())) {
            throw new BusinessException("Cannot add assets when task status is " + task.getStatus());
        }

        TaskAssetEntity entity = new TaskAssetEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(taskId);
        entity.setUserId(userId);
        entity.setProductId(task.getProductId());
        entity.setAssetKind(request.getAssetKind());
        entity.setAssetRole(request.getAssetRole());
        entity.setSource(request.getSource());
        entity.setUrl(request.getUrl());
        entity.setFileName(request.getFileName());
        entity.setMimeType(request.getMimeType());
        entity.setSizeBytes(request.getSizeBytes());
        entity.setDescription(request.getDescription());
        entity.setConfirmed(false);
        entity.setMetadata(request.getMetadata());
        taskAssetMapper.insert(entity);

        log.info("Asset added: taskId={}, assetId={}, role={}", taskId, entity.getId(), entity.getAssetRole());
        return getAssets(taskId, userId);
    }

    @Override
    @Transactional
    public List<TaskAssetResponse> updateAssetRole(UUID taskId, UUID assetId, UpdateAssetRoleRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        TaskAssetEntity asset = taskAssetMapper.findByIdAndTaskId(assetId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskAsset", assetId));

        asset.setAssetRole(request.getAssetRole());
        asset.setUpdatedAt(OffsetDateTime.now());
        taskAssetMapper.updateById(asset);

        log.info("Asset role updated: taskId={}, assetId={}, role={}", taskId, assetId, request.getAssetRole());
        return getAssets(taskId, userId);
    }

    @Override
    @Transactional
    public List<TaskAssetResponse> generateAssetImage(UUID taskId, GenerateAssetImageRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!ASSET_UPLOADABLE_STATUSES.contains(task.getStatus())) {
            throw new BusinessException("Cannot generate asset image when task status is " + task.getStatus());
        }

        List<TaskAssetEntity> allAssets = taskAssetMapper.findByTaskId(taskId);
        List<TaskAssetEntity> sourceAssets = selectSourceAssetsForGeneration(request, allAssets);
        if (sourceAssets.isEmpty()) {
            throw new BusinessException("At least one image asset is required before generating a new image.");
        }

        String assetRole = normalizeGeneratedAssetRole(request.getAssetRole());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("generationRound", 1);
        return generateAndSaveAssetImage(
                task,
                userId,
                request.getPrompt().trim(),
                assetRole,
                sourceAssets,
                metadata,
                Map.of()
        );
    }

    @Override
    @Transactional
    public List<TaskAssetResponse> regenerateAssetImage(UUID taskId, UUID assetId, RegenerateAssetImageRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!ASSET_UPLOADABLE_STATUSES.contains(task.getStatus())) {
            throw new BusinessException("Cannot regenerate asset image when task status is " + task.getStatus());
        }

        String feedback = request.getFeedback().trim();
        TaskAssetEntity parent = taskAssetMapper.findByIdAndTaskId(assetId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskAsset", assetId));
        if (!isGeneratedOrEditedAsset(parent) || !isImageAsset(parent)) {
            throw new BusinessException("Only generated image assets can be regenerated.");
        }
        if (parent.getUrl() == null || parent.getUrl().isBlank()) {
            throw new BusinessException("Cannot regenerate from an asset without image URL.");
        }

        Map<String, Object> parentMetadata = parent.getMetadata() != null ? parent.getMetadata() : Map.of();
        List<TaskAssetEntity> sourceAssets = selectSourceAssetsForRegeneration(parent);
        if (sourceAssets.isEmpty()) {
            throw new BusinessException("At least one image asset is required before regenerating.");
        }

        String previousPrompt = stringValue(parentMetadata.get("prompt"));
        String fullPrompt = buildContextualRegenerationPrompt(previousPrompt, feedback);
        int generationRound = intValue(parentMetadata.get("generationRound"), 1) + 1;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("parentAssetId", parent.getId().toString());
        metadata.put("feedback", feedback);
        metadata.put("previousPrompt", previousPrompt);
        metadata.put("previousResultUrl", parent.getUrl());
        metadata.put("generationRound", generationRound);
        // Store the raw user intent as "prompt" so the NEXT regeneration uses a clean previousPrompt,
        // not the fully assembled editing prompt (which would cause recursive nesting).
        metadata.put("prompt", feedback);

        Map<String, Object> previousResult = new LinkedHashMap<>();
        previousResult.put("assetId", parent.getId().toString());
        previousResult.put("url", parent.getUrl());
        previousResult.put("assetRole", parent.getAssetRole());
        previousResult.put("generationRound", generationRound - 1);

        Map<String, Object> generationContext = new LinkedHashMap<>();
        generationContext.put("feedback", feedback);
        generationContext.put("previousPrompt", previousPrompt);
        generationContext.put("previousResult", previousResult);

        return generateAndSaveAssetImage(
                task,
                userId,
                fullPrompt,
                normalizeGeneratedAssetRole(parent.getAssetRole()),
                sourceAssets,
                metadata,
                generationContext
        );
    }

    private List<TaskAssetResponse> generateAndSaveAssetImage(VideoTaskEntity task,
                                                              UUID userId,
                                                              String prompt,
                                                              String assetRole,
                                                              List<TaskAssetEntity> sourceAssets,
                                                              Map<String, Object> extraMetadata,
                                                              Map<String, Object> generationContext) {
        Map<String, Object> result;
        try {
            result = aiServiceClient.generateAssetImage(
                    task.getId(),
                    task.getProductId(),
                    task.getUserId(),
                    prompt,
                    creativeContextAssembler.assemble(task),
                    sourceAssets.stream().map(this::toAiAsset).toList(),
                    assetRole,
                    generationContext
            );
        } catch (Exception ex) {
            throw new BusinessException("AI image generation failed: " + ex.getMessage());
        }

        Object urlValue = result.get("url");
        if (!(urlValue instanceof String url) || url.isBlank()) {
            throw new BusinessException("AI image generation failed: empty image URL.");
        }

        TaskAssetEntity generated = new TaskAssetEntity();
        generated.setId(UUID.randomUUID());
        generated.setTaskId(task.getId());
        generated.setUserId(userId);
        generated.setProductId(task.getProductId());
        generated.setAssetKind("image");
        generated.setAssetRole(assetRole);
        generated.setSource("ai_generated");
        generated.setUrl(url);
        generated.setFileName("ai-generated-" + generated.getId() + ".png");
        generated.setMimeType("image/png");
        generated.setDescription(prompt);
        generated.setConfirmed(false);

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }
        // Only set prompt if the caller didn't already provide one (regenerate sets it to raw feedback)
        if (!metadata.containsKey("prompt")) {
            metadata.put("prompt", prompt);
        }
        metadata.put("sourceAssetIds", sourceAssets.stream().map(a -> a.getId().toString()).toList());
        metadata.put("provider", result.get("provider"));
        metadata.put("model", result.get("model"));
        metadata.put("qualityScore", result.get("qualityScore"));
        metadata.put("generatedAt", OffsetDateTime.now().toString());
        generated.setMetadata(metadata);

        taskAssetMapper.insert(generated);
        log.info("Generated task asset image: taskId={}, assetId={}, role={}", task.getId(), generated.getId(), assetRole);
        return getAssets(task.getId(), userId);
    }

    @Override
    @Transactional
    public void deleteAsset(UUID taskId, UUID assetId, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        if (!"asset_uploading".equals(task.getStatus()) && !"waiting_asset_confirmation".equals(task.getStatus())) {
            throw new BusinessException("Cannot delete assets when task status is " + task.getStatus());
        }

        TaskAssetEntity asset = taskAssetMapper.findByIdAndTaskId(assetId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskAsset", assetId));

        taskAssetMapper.deleteById(assetId);
        log.info("Asset deleted: taskId={}, assetId={}, kind={}, role={}", taskId, assetId, asset.getAssetKind(), asset.getAssetRole());
    }

    @Override
    @Transactional
    public VideoTaskStatusResponse confirmAssets(UUID taskId, ConfirmAssetsRequest request, UUID userId) {
        VideoTaskEntity task = findTask(taskId);
        checkOwnership(task, userId);

        List<TaskAssetEntity> assets = taskAssetMapper.findByTaskId(taskId);
        if (assets.isEmpty()) {
            throw new BusinessException("At least one asset is required before confirmation.");
        }
        markRequestedAssetsConfirmed(request, assets);

        if ("asset_uploading".equals(task.getStatus())) {
            List<TaskAssetEntity> confirmedAssets = taskAssetMapper.findByTaskId(taskId).stream()
                    .filter(asset -> Boolean.TRUE.equals(asset.getConfirmed()))
                    .toList();
            List<TaskAssetEntity> assetsForAnalysis = selectAssetsForAnalysis(confirmedAssets);
            if (assetsForAnalysis.stream().noneMatch(this::isImageAsset)) {
                throw new BusinessException("At least one confirmed product image is required before asset analysis.");
            }
            if (assetsForAnalysis.isEmpty()) {
                throw new BusinessException("No analyzable image or video assets found for asset analysis.");
            }

            VideoTaskStateMachine.validateTransition(task.getStatus(), "asset_analyzing");
            task.setStatus("asset_analyzing");
            task.setProgress(15);
            task.setUpdatedAt(OffsetDateTime.now());
            videoTaskMapper.updateById(task);

            Map<String, Object> productContext = creativeContextAssembler.assemble(task);
            aiServiceClient.startAssetAnalysis(
                    task.getId(),
                    task.getProductId(),
                    task.getUserId(),
                    productContext,
                    assetsForAnalysis.stream().map(this::toAiAsset).toList()
            );

            log.info("Assets uploaded, starting asset analysis: taskId={}, assets={}", taskId, assetsForAnalysis.size());
            return new VideoTaskStatusResponse(taskId, "asset_analyzing", task.getProgress());
        }

        if (!"waiting_asset_confirmation".equals(task.getStatus())) {
            throw new BusinessException("Cannot confirm analyzed assets when task status is " + task.getStatus());
        }

        // Persist updated creativePrompt to creative state before generating plans
        if (request != null && request.getCreativePrompt() != null && !request.getCreativePrompt().isBlank()) {
            var cs = creativeStateMapper.findByTaskId(taskId).orElse(null);
            if (cs != null) {
                Map<String, Object> userReq = cs.getUserRequirementsJson() != null
                        ? new LinkedHashMap<>(cs.getUserRequirementsJson())
                        : new LinkedHashMap<>();
                userReq.put("rawPrompt", request.getCreativePrompt().trim());
                cs.setUserRequirementsJson(userReq);
                creativeStateMapper.updateById(cs);
            }
        }

        String nextStatus = determineNextStatus(task);
        VideoTaskStateMachine.validateTransition(task.getStatus(), nextStatus);
        task.setStatus(nextStatus);
        task.setProgress("reference_analyzing".equals(nextStatus) ? 25 : 30);
        task.setUpdatedAt(OffsetDateTime.now());
        videoTaskMapper.updateById(task);

        if ("reference_analyzing".equals(nextStatus)) {
            aiServiceClient.startReferenceAnalysis(task.getId(), task.getProductId(), task.getUserId(), Map.of());
        } else if ("plan_generating".equals(nextStatus)) {
            aiServiceClient.startCreativePlanGeneration(
                    task.getId(), task.getProductId(), task.getUserId(), creativeContextAssembler.assemble(task));
        }

        log.info("Assets confirmed: taskId={}, newStatus={}", taskId, nextStatus);
        return new VideoTaskStatusResponse(taskId, nextStatus, task.getProgress());
    }

    private void markRequestedAssetsConfirmed(ConfirmAssetsRequest request, List<TaskAssetEntity> assets) {
        if (request != null && request.getAssetIds() != null && !request.getAssetIds().isEmpty()) {
            Set<UUID> idsToConfirm = Set.copyOf(request.getAssetIds());
            for (TaskAssetEntity asset : assets) {
                if (idsToConfirm.contains(asset.getId())) {
                    asset.setConfirmed(true);
                    asset.setUpdatedAt(OffsetDateTime.now());
                    taskAssetMapper.updateById(asset);
                }
            }
            return;
        }

        for (TaskAssetEntity asset : assets) {
            asset.setConfirmed(true);
            asset.setUpdatedAt(OffsetDateTime.now());
            taskAssetMapper.updateById(asset);
        }
    }

    private String determineNextStatus(VideoTaskEntity task) {
        List<TaskAssetEntity> refVideos = taskAssetMapper.findByTaskId(task.getId()).stream()
                .filter(a -> "reference_video".equals(a.getAssetRole()))
                .collect(Collectors.toList());
        if (!refVideos.isEmpty()) {
            return "reference_analyzing";
        }
        return "plan_generating";
    }

    private VideoTaskEntity findTask(UUID taskId) {
        VideoTaskEntity task = videoTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("VideoTask", taskId);
        }
        return task;
    }

    private void checkOwnership(VideoTaskEntity task, UUID userId) {
        if (!task.getUserId().equals(userId)) {
            throw new ResourceForbiddenException("Task does not belong to current user");
        }
    }

    private List<TaskAssetEntity> selectAssetsForAnalysis(List<TaskAssetEntity> confirmedAssets) {
        List<TaskAssetEntity> generatedOrEditedAssets = confirmedAssets.stream()
                .filter(this::isAnalyzableAsset)
                .filter(this::isGeneratedOrEditedAsset)
                .toList();
        if (!generatedOrEditedAssets.isEmpty()) {
            return generatedOrEditedAssets;
        }
        return confirmedAssets.stream()
                .filter(this::isAnalyzableAsset)
                .toList();
    }

    private List<TaskAssetEntity> selectSourceAssetsForGeneration(GenerateAssetImageRequest request, List<TaskAssetEntity> allAssets) {
        Set<UUID> requestedIds = request.getSourceAssetIds() == null
                ? Set.of()
                : Set.copyOf(request.getSourceAssetIds());
        return allAssets.stream()
                .filter(this::isImageAsset)
                .filter(asset -> asset.getUrl() != null && !asset.getUrl().isBlank())
                .filter(asset -> requestedIds.isEmpty() || requestedIds.contains(asset.getId()))
                .toList();
    }

    private List<TaskAssetEntity> selectSourceAssetsForRegeneration(TaskAssetEntity parent) {
        return List.of(parent);
    }

    private List<TaskAssetEntity> selectSourceAssetsForRegeneration(TaskAssetEntity parent,
                                                                    Map<String, Object> parentMetadata,
                                                                    List<TaskAssetEntity> allAssets) {
        Set<UUID> sourceIds = metadataUuidSet(parentMetadata.get("sourceAssetIds"));
        List<TaskAssetEntity> sourceAssets = new ArrayList<>(allAssets.stream()
                .filter(this::isImageAsset)
                .filter(asset -> asset.getUrl() != null && !asset.getUrl().isBlank())
                .filter(asset -> sourceIds.isEmpty() || sourceIds.contains(asset.getId()))
                .toList());

        boolean includesParent = sourceAssets.stream().anyMatch(asset -> asset.getId().equals(parent.getId()));
        if (!includesParent) {
            sourceAssets.add(parent);
        }
        return sourceAssets;
    }

    private Set<UUID> metadataUuidSet(Object value) {
        if (!(value instanceof List<?> values)) {
            return Set.of();
        }
        Set<UUID> ids = new HashSet<>();
        for (Object item : values) {
            try {
                ids.add(UUID.fromString(String.valueOf(item)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy metadata values.
            }
        }
        return ids;
    }

    private String buildContextualRegenerationPrompt(String previousPrompt, String feedback) {
        String context = previousPrompt == null || previousPrompt.isBlank()
                ? "无。只根据当前图片和本轮修改要求执行。"
                : previousPrompt;
        return """
                你正在对一张已有图片做局部编辑。
                传给你的参考图就是需要编辑的底图，不是风格参考，也不是重新生成参考。

                当前底图是最高优先级事实。历史编辑意图只能帮助理解省略表达，不能覆盖底图像素。

                历史编辑意图（低优先级，仅用于理解“再小一点”“往左一点”等省略反馈）：
                %s

                本轮用户明确要求（最高优先级操作指令）：
                %s

                编辑规则：
                1. 如果本轮要求是“再小一点、再大一点、往左一点、浅一点”等省略表达，请结合历史编辑意图判断它指的是哪个已有元素。
                2. 如果历史编辑意图与底图实际画面冲突，必须相信底图，只编辑底图中真实存在的元素。
                3. 不要重新生成整张图，只做局部修改。
                4. 除用户明确要求修改的区域外，商品主体、版型、颜色、材质、背景、光线、构图都保持一致。
                5. 如果用户要求调整已有元素，必须保留同一个元素的外观特征，只改变用户指定的属性。
                6. 不要移动、删除或新增用户没有提到的元素。
                7. 输出结果应该看起来就是参考图被轻微编辑后的版本，而不是一张新图。
                """.formatted(context, feedback);
    }

    private String buildRegenerationPrompt(String feedback) {
        return """
                你正在对一张已有图片做局部编辑。
                传给你的参考图就是需要编辑的底图，不是风格参考，也不是重新生成参考。

                只做用户明确要求的修改：
                %s

                编辑规则：
                1. 不要重新生成整张图，只做局部修改。
                2. 除用户明确要求修改的区域外，商品主体、版型、颜色、材质、背景、光线、构图都保持一致。
                3. 如果用户要求调整已有元素，例如“把狗变小”，必须保留同一个元素的外观特征，只改变用户指定的属性。
                4. 不要移动、删除或新增用户没有提到的元素。
                5. 输出结果应该看起来就是参考图被轻微编辑后的版本，而不是一张新图。
                """.formatted(feedback);
    }

    private String buildRegenerationPrompt(String previousPrompt, String feedback) {
        String base = (previousPrompt != null && !previousPrompt.isBlank())
                ? "参考图里已有的内容（来自上一轮生成要求）：\n" + previousPrompt
                : "参考图就是上一轮生成的结果。";
        return """
                你正在编辑一张已有的图片。传给你的参考图就是底图本身。
                除了下面明确要求修改的部分，其余所有像素必须原封不动保留。

                %s

                在底图基础上，只做以下修改（不要重新生成，不要替换整张图）：
                %s

                编辑时必须遵守的规则：
                1. 保持视觉身份一致——比如如果用户说"把狗变小"，必须是同一只狗（品种、颜色、姿势、位置都不变），只改变大小。
                2. 保持空间布局——不要移动、删除或新增用户没提到的元素。
                3. 这是局部编辑（类似 inpainting），不是文生图。
                4. 输出必须看起来就是参考图，只是应用了用户要求的修改。
                5. 背景、光线、商品形状、颜色——除非用户明确要改，否则全部保持原样。
                """.formatted(base, feedback);
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : "";
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String normalizeGeneratedAssetRole(String assetRole) {
        if ("generated_result".equals(assetRole) || "image_variant".equals(assetRole)) {
            return assetRole;
        }
        return "image_variant";
    }

    private boolean isGeneratedOrEditedAsset(TaskAssetEntity asset) {
        String source = asset.getSource();
        String role = asset.getAssetRole();
        return "ai_generated".equals(source)
                || "generated_result".equals(role)
                || "image_variant".equals(role);
    }

    private boolean isImageAsset(TaskAssetEntity asset) {
        return "image".equals(asset.getAssetKind());
    }

    private boolean isAnalyzableAsset(TaskAssetEntity asset) {
        if (asset.getUrl() == null || asset.getUrl().isBlank()) {
            return false;
        }
        String kind = asset.getAssetKind();
        return "image".equals(kind) || "video".equals(kind);
    }

    private Map<String, Object> toAiAsset(TaskAssetEntity asset) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetId", asset.getId().toString());
        payload.put("assetKind", asset.getAssetKind());
        payload.put("assetRole", asset.getAssetRole());
        payload.put("source", asset.getSource());
        payload.put("url", asset.getUrl());
        return payload;
    }

    private TaskAssetResponse toResponse(TaskAssetEntity entity) {
        return TaskAssetResponse.builder()
                .assetId(entity.getId())
                .taskId(entity.getTaskId())
                .productId(entity.getProductId())
                .assetKind(entity.getAssetKind())
                .assetRole(entity.getAssetRole())
                .source(entity.getSource())
                .url(entity.getUrl())
                .fileName(entity.getFileName())
                .mimeType(entity.getMimeType())
                .sizeBytes(entity.getSizeBytes())
                .description(entity.getDescription())
                .confirmed(entity.getConfirmed())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
