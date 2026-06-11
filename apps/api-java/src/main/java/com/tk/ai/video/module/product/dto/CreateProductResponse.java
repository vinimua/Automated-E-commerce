package com.tk.ai.video.module.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class CreateProductResponse {
    private UUID productId;
}
