package com.tk.ai.video.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDetail {

    private String errorCode;
    private String errorMessage;
    private String failedStage;
    private boolean retryable;
    private String provider;
    private Map<String, Object> rawError;
}
