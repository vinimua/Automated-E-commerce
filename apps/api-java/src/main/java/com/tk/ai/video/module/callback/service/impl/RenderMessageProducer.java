package com.tk.ai.video.module.callback.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pushes render tasks to RabbitMQ for the Render Worker to consume.
 * Called by AiCallbackServiceImpl when a render_manifest callback arrives.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenderMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${render-worker.queue}")
    private String renderQueue;

    @Value("${java-api.base-url:http://localhost:8080}")
    private String javaApiBaseUrl;

    /**
     * Send a render task message to the video.render.queue.
     *
     * @param taskId        the video task ID
     * @param renderTaskId  unique render task ID for idempotency
     * @param correlationId correlation ID for tracing
     * @param renderManifest the full RenderManifest from Python AI
     */
    public void sendRenderTask(UUID taskId, String renderTaskId, String correlationId,
                               Map<String, Object> renderManifest) {
        String callbackUrl = javaApiBaseUrl + "/api/render-callbacks/" + taskId;

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("taskId", taskId.toString());
        message.put("renderTaskId", renderTaskId);
        message.put("correlationId", correlationId);
        message.put("renderManifest", renderManifest);
        message.put("callbackUrl", callbackUrl);

        rabbitTemplate.convertAndSend(renderQueue, message);
        log.info("Sent render task to queue {}: taskId={}, renderTaskId={}",
                renderQueue, taskId, renderTaskId);
    }
}
