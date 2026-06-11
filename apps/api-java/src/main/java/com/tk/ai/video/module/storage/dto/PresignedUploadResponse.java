package com.tk.ai.video.module.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResponse {

    private String uploadUrl;
    private String fileUrl;
}
