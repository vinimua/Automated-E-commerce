package com.tk.ai.video.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client to Python AI Orchestrator.
 * RestClient is built in the constructor via constructor injection,
 * ensuring @Value fields are populated before the interceptor captures them.
 */
@Slf4j
@Service
public class AiServiceClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String internalToken;

    /**
     * All @Value dependencies are injected via constructor parameters.
     * This guarantees they are available when RestClient is built.
     */
    public AiServiceClient(
            @Value("${ai-orchestrator.base-url}") String baseUrl,
            @Value("${internal-service.token}") String internalToken
    ) {
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;

        // Force HTTP/1.1 — Java 21+ HttpClient defaults to HTTP/2 upgrade,
        // which causes Uvicorn to drop the request body (resulting in 422).
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().add("X-Internal-Service-Token", internalToken);
                    request.getHeaders().add("Content-Type", "application/json");
                    return execution.execute(request, body);
                })
                .build();
    }

    public void startProductAnalysis(
            UUID taskId, UUID productId, UUID userId,
            String productName, String productDescription,
            String productLink, List<String> imageUrls,
            String targetMarket, String language
    ) {
        String correlationId = MDC.get("correlationId");//从日志上下文拿链路 ID
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "productContext", Map.of(
                        "name", productName,
                        "description", productDescription != null ? productDescription : "",
                        "productLink", productLink != null ? productLink : "",
                        "imageUrls", imageUrls,
                        "targetMarket", targetMarket,
                        "language", language
                )
        );

        try {
            restClient.post()
                    .uri(baseUrl + "/ai/workflows/product-analysis")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity(); // ← 不等响应体
            log.info("Started ProductAnalysisWorkflow for taskId={}", taskId);
        } catch (Exception e) {
            log.warn("Failed to notify AI orchestrator for task {}: {}", taskId, e.getMessage());
            // Fire-and-forget: task stays in "analyzing" until AI callback arrives.
            // A scheduled cleanup job handles tasks stuck for >30min.不抛异常 — 任务留在 analyzing 状态等定时清理
        }
    }

    public void startSelectedPlanGeneration(
            UUID taskId, UUID productId, UUID userId,
            UUID selectedPlanId, Map<String, Object> selectedPlan,
            int duration, String videoType, boolean needSubtitles, boolean needVoiceover
    ) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "selectedPlanId", selectedPlanId.toString(),
                "selectedPlan", selectedPlan,
                "duration", duration,
                "videoType", videoType,
                "needSubtitles", needSubtitles,
                "needVoiceover", needVoiceover
        );

        try {
            restClient.post()
                    .uri(baseUrl + "/ai/workflows/selected-plan-generation")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Started SelectedPlanGenerationWorkflow for taskId={}", taskId);
        } catch (Exception e) {
            log.warn("Failed to notify AI orchestrator for task {}: {}", taskId, e.getMessage());
            // Fire-and-forget: task stays in current state until AI callback arrives.
        }
    }

    // ── Fashion Creative Loop V1 dispatch methods ──

    public void startAssetAnalysis(UUID taskId, UUID productId, UUID userId,
                                   Map<String, Object> assetContext) {
        String correlationId = getOrCreateCorrelationId();
        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "assetContext", assetContext != null ? assetContext : Map.of()
        );
        fireAndForget("/ai/workflows/asset-analysis", payload, taskId, "AssetAnalysis");
    }

    public void startReferenceAnalysis(UUID taskId, UUID productId, UUID userId,
                                        Map<String, Object> creativeState) {
        String correlationId = getOrCreateCorrelationId();
        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "creativeState", creativeState != null ? creativeState : Map.of()
        );
        fireAndForget("/ai/workflows/reference-analysis", payload, taskId, "ReferenceAnalysis");
    }

    public void startCreativePlanGeneration(UUID taskId, UUID productId, UUID userId,
                                              Map<String, Object> creativeState) {
        String correlationId = getOrCreateCorrelationId();
        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "creativeState", creativeState != null ? creativeState : Map.of()
        );
        fireAndForget("/ai/workflows/creative-plan-generation", payload, taskId, "CreativePlanGeneration");
    }

    public void startStoryboardGeneration(UUID taskId, UUID productId, UUID userId,
                                           UUID selectedPlanId, Map<String, Object> selectedPlan,
                                           int duration, String videoType) {
        String correlationId = getOrCreateCorrelationId();
        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "selectedPlanId", selectedPlanId.toString(),
                "selectedPlan", selectedPlan != null ? selectedPlan : Map.of(),
                "duration", duration,
                "videoType", videoType
        );
        fireAndForget("/ai/workflows/storyboard-generation", payload, taskId, "StoryboardGeneration");
    }

    public void startKeyframeGeneration(UUID taskId, UUID productId, UUID userId,
                                          Map<String, Object> storyboard) {
        String correlationId = getOrCreateCorrelationId();
        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "storyboard", storyboard != null ? storyboard : Map.of()
        );
        fireAndForget("/ai/workflows/keyframe-generation", payload, taskId, "KeyframeGeneration");
    }

    public void startVideoClipGeneration(UUID taskId, UUID productId, UUID userId,
                                           Map<String, Object> storyboard,
                                           Map<String, Object> keyframes) {
        String correlationId = getOrCreateCorrelationId();
        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "storyboard", storyboard != null ? storyboard : Map.of(),
                "keyframes", keyframes != null ? keyframes : Map.of("keyframes", List.of())
        );
        fireAndForget("/ai/workflows/video-clip-generation", payload, taskId, "VideoClipGeneration");
    }

    public void startRepairWorkflow(UUID taskId, UUID productId, UUID userId,
                                      UUID repairEventId, String feedbackText,
                                      String category, String targetType,
                                      Map<String, Object> currentState) {
        String correlationId = getOrCreateCorrelationId();
        Map<String, Object> payload = Map.of(
                "taskId", taskId.toString(),
                "productId", productId.toString(),
                "userId", userId.toString(),
                "correlationId", correlationId,
                "repairEventId", repairEventId.toString(),
                "feedbackText", feedbackText != null ? feedbackText : "",
                "category", category != null ? category : "general",
                "targetType", targetType != null ? targetType : "video_clip",
                "currentState", currentState != null ? currentState : Map.of()
        );
        fireAndForget("/ai/workflows/repair", payload, taskId, "RepairWorkflow");
    }

    private String getOrCreateCorrelationId() {
        String correlationId = org.slf4j.MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private void fireAndForget(String uri, Map<String, Object> payload, UUID taskId, String workflowName) {
        try {
            restClient.post()
                    .uri(baseUrl + uri)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Started {} for taskId={}", workflowName, taskId);
        } catch (Exception e) {
            log.warn("Failed to notify AI orchestrator for task {} ({}): {}", taskId, workflowName, e.getMessage());
        }
    }
}
