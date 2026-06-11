package com.tk.ai.video.module.callback.service;

import com.tk.ai.video.module.callback.dto.RenderCallbackRequest;

public interface RenderCallbackService {
    void handleCallback(RenderCallbackRequest request);
}
