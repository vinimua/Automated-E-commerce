package com.tk.ai.video.module.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateProductRequest {

    @NotBlank
    private String name;

    private String description;
    private String productLink;

    @NotEmpty
    @Size(min = 1)
    private List<String> imageUrls;

    @NotBlank
    private String targetMarket;

    @NotBlank
    private String language;
}
