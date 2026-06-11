package com.tk.ai.video.module.product.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ProductResponse {

    private UUID id;
    private String name;
    private String description;
    private String productLink;
    private String category;
    private String targetMarket;
    private String language;
    private List<String> sellingPoints;
    private List<String> painPoints;
    private List<String> targetAudience;
    private List<String> scenes;
    private Integer videoScore;
    private List<String> riskTips;
    private List<String> imageUrls;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
