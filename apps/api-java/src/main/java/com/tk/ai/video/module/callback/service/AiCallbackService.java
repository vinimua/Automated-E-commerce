package com.tk.ai.video.module.callback.service;

import com.tk.ai.video.module.callback.dto.AiCallbackRequest;

public interface AiCallbackService {
    void handleCallback(AiCallbackRequest request);
}
