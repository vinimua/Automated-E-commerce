package com.tk.ai.video.module.creativestate.service;

import com.tk.ai.video.module.creativestate.dto.CreativeStateResponse;
import com.tk.ai.video.module.creativestate.dto.UpdateCreativeStateRequest;

import java.util.UUID;

public interface CreativeStateService {

    CreativeStateResponse getCreativeState(UUID taskId, UUID userId);

    CreativeStateResponse updateCreativeState(UUID taskId, UpdateCreativeStateRequest request, UUID userId);
}
