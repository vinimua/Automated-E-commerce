package com.tk.ai.video.module.product.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateProductRequest {

    private String name;
    private String description;
    private List<String> sellingPoints;
    private List<String> painPoints;
    private List<String> targetAudience;
    private List<String> scenes;
}
