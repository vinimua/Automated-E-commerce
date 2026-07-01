package com.tk.ai.video.module.repairevent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class RepairEventListResponse {
    private UUID taskId;
    private List<RepairEventResponse> events;
}
