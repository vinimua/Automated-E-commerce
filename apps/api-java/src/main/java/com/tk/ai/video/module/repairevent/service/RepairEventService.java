package com.tk.ai.video.module.repairevent.service;

import com.tk.ai.video.module.repairevent.dto.FeedbackRequest;
import com.tk.ai.video.module.repairevent.dto.RepairEventResponse;

import java.util.List;
import java.util.UUID;

public interface RepairEventService {

    List<RepairEventResponse> getRepairEvents(UUID taskId, UUID userId);

    RepairEventResponse createRepairEvent(UUID taskId, FeedbackRequest request, UUID userId);
}
