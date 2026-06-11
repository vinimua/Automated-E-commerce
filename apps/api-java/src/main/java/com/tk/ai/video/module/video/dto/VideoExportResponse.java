package com.tk.ai.video.module.video.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class VideoExportResponse {
    private UUID videoId;
    private String status;
    private String downloadUrl;
}
