package com.tk.ai.video.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client to Python AI Orchestrator.
 * Fire-and-forget dispatch: starts workflows, task progresses via callbacks.
 */
@Slf4j
@Service
public class AiServiceClient {

    private final RestClient restClient;

    @Value("${ai-orchestrator.base-url}")
    private String baseUrl;

    @Value("${internal-service.token}")
    private String internalToken;

    public AiServiceClient() {
        this.restClient = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().add("X-Internal-Service-Token", internalToken);
                    request.getHeaders().add("Content-Type", "application/json");
                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * Start the ProductAnalysisWorkflow.
     * Task status stays in "analyzing" until AI callback arrives.
     */
    public void startProductAnalysis(
            UUID taskId, UUID productId, UUID userId,
            String productName, String productDescription,
            String productLink, java.util.List<String> imageUrls,
            String targetMarket, String language
    ) {
        String correlationId = org.slf4j.MDC.get("correlationId");
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
                    .toBodilessEntity();
            log.info("Started ProductAnalysisWorkflow for taskId={}", taskId);
        } catch (Exception e) {
            log.warn("Failed to notify AI orchestrator for task {}: {}", taskId, e.getMessage());
        }
    }

    /**
     * Start the SelectedPlanGenerationWorkflow.
     * Task status stays in "script_generating" until AI callback arrives.
     */
    public void startSelectedPlanGeneration(
            UUID taskId, UUID productId, UUID userId,
            UUID selectedPlanId, Map<String, Object> selectedPlan,
            int duration, String videoType, boolean needSubtitles, boolean needVoiceover
    ) {
        String correlationId = org.slf4j.MDC.get("correlationId");
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
        }
    }
}
