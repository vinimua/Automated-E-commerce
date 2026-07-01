package com.tk.ai.video.module.creativestate.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateCreativeStateRequest {
    private Map<String, Object> product;
    private Map<String, Object> model;
    private Map<String, Object> scene;
    private Map<String, Object> outfit;
    private Map<String, Object> referenceVideo;
    private Map<String, Object> constraints;
    private Map<String, Object> userRequirements;
}
