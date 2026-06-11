package com.tk.ai.video.module.product.controller;

import com.tk.ai.video.common.ApiResponse;
import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.module.product.dto.*;
import com.tk.ai.video.module.product.service.ProductService;
import com.tk.ai.video.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ApiResponse<CreateProductResponse> create(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(productService.create(request, principal.getUserId()));
    }

    @GetMapping
    public ApiResponse<PageResult<ProductResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(productService.list(page, pageSize, principal.getUserId()));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> getById(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(productService.getById(productId, principal.getUserId()));
    }

    @PatchMapping("/{productId}")
    public ApiResponse<ProductResponse> update(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(productService.update(productId, request, principal.getUserId()));
    }
}
