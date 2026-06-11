package com.tk.ai.video.module.product.service;

import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.product.dto.*;

import java.util.UUID;

public interface ProductService {
    CreateProductResponse create(CreateProductRequest request, UUID userId);
    ProductResponse getById(UUID productId, UUID userId);
    ProductResponse update(UUID productId, UpdateProductRequest request, UUID userId);
    PageResult<ProductResponse> list(int page, int pageSize, UUID userId);
}
