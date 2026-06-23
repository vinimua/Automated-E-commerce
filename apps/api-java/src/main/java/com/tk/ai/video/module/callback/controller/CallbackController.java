package com.tk.ai.video.module.callback.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.module.callback.dto.*;
import com.tk.ai.video.module.callback.service.AiCallbackService;
import com.tk.ai.video.module.callback.service.RenderCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CallbackController {

    private final AiCallbackService aiCallbackService;
    private final RenderCallbackService renderCallbackService;

    @PostMapping("/api/ai-callbacks/{taskId}")
    public ApiResponse<CallbackResponse> aiCallback(
            @PathVariable UUID taskId,
            @Valid @RequestBody AiCallbackRequest request
    ) {
        request.setTaskId(taskId);
        aiCallbackService.handleCallback(request);
        CallbackResponse rsp = new CallbackResponse();
        rsp.setReceived(true);
        return ApiResponse.ok(rsp);
    }

    @PostMapping("/api/render-callbacks/{taskId}")
    public ApiResponse<CallbackResponse> renderCallback(
            @PathVariable UUID taskId,
            @Valid @RequestBody RenderCallbackRequest request
    ) {
        request.setTaskId(taskId);
        renderCallbackService.handleCallback(request);
        CallbackResponse rsp = new CallbackResponse();
        rsp.setReceived(true);
        return ApiResponse.ok(rsp);
    }
}
