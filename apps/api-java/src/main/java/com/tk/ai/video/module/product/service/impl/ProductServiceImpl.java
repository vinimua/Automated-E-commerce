package com.tk.ai.video.module.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tk.ai.video.common.PageResult;
import com.tk.ai.video.common.ResourceForbiddenException;
import com.tk.ai.video.common.ResourceNotFoundException;
import com.tk.ai.video.module.product.dto.*;
import com.tk.ai.video.module.product.entity.ProductEntity;
import com.tk.ai.video.module.product.entity.ProductImageEntity;
import com.tk.ai.video.module.product.mapper.ProductImageMapper;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;

    @Override
    @Transactional
    public CreateProductResponse create(CreateProductRequest request, UUID userId) {
        ProductEntity product = new ProductEntity();
        product.setUserId(userId);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setProductLink(request.getProductLink());
        product.setTargetMarket(request.getTargetMarket());
        product.setLanguage(request.getLanguage());
        product.setId(UUID.randomUUID());
        product.setStatus("active");
        productMapper.insert(product);

        // Insert product images
        for (int i = 0; i < request.getImageUrls().size(); i++) {
            ProductImageEntity image = new ProductImageEntity();
            image.setProductId(product.getId());
            image.setUserId(userId);
            image.setId(UUID.randomUUID());
            image.setUrl(request.getImageUrls().get(i));
            image.setIsPrimary(i == 0); // first image is primary
            productImageMapper.insert(image);
        }

        log.info("Product created: id={}, name={}", product.getId(), product.getName());
        return new CreateProductResponse(product.getId());
    }

    @Override
    public ProductResponse getById(UUID productId, UUID userId) {
        ProductEntity product = findProduct(productId);
        checkOwnership(product, userId);
        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID productId, UpdateProductRequest request, UUID userId) {
        ProductEntity product = findProduct(productId);
        checkOwnership(product, userId);

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getSellingPoints() != null) product.setSellingPoints(request.getSellingPoints());
        if (request.getPainPoints() != null) product.setPainPoints(request.getPainPoints());
        if (request.getTargetAudience() != null) product.setTargetAudience(request.getTargetAudience());
        if (request.getScenes() != null) product.setScenes(request.getScenes());

        productMapper.updateById(product);
        return toResponse(product);
    }

    @Override
    public PageResult<ProductResponse> list(int page, int pageSize, UUID userId) {
        Page<ProductEntity> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductEntity::getUserId, userId)
               .ne(ProductEntity::getStatus, "deleted")
               .orderByDesc(ProductEntity::getCreatedAt);

        Page<ProductEntity> result = productMapper.selectPage(p, wrapper);
        List<ProductResponse> items = result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PageResult<>(items, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal(), (int) result.getPages());
    }

    private ProductEntity findProduct(UUID productId) {
        ProductEntity product = productMapper.selectById(productId);
        if (product == null) {
            throw new ResourceNotFoundException("Product", productId);
        }
        return product;
    }

    private void checkOwnership(ProductEntity product, UUID userId) {
        if (!product.getUserId().equals(userId)) {
            throw new ResourceForbiddenException("Product does not belong to current user");
        }
    }

    private ProductResponse toResponse(ProductEntity product) {
        List<ProductImageEntity> images = productImageMapper.findByProductId(product.getId());

        ProductResponse rsp = new ProductResponse();
        rsp.setId(product.getId());
        rsp.setName(product.getName());
        rsp.setDescription(product.getDescription());
        rsp.setProductLink(product.getProductLink());
        rsp.setCategory(product.getCategory());
        rsp.setTargetMarket(product.getTargetMarket());
        rsp.setLanguage(product.getLanguage());
        rsp.setSellingPoints(product.getSellingPoints());
        rsp.setPainPoints(product.getPainPoints());
        rsp.setTargetAudience(product.getTargetAudience());
        rsp.setScenes(product.getScenes());
        rsp.setVideoScore(product.getVideoScore());
        rsp.setRiskTips(product.getRiskTips());
        rsp.setImageUrls(images.stream().map(ProductImageEntity::getUrl).collect(Collectors.toList()));
        rsp.setStatus(product.getStatus());
        rsp.setCreatedAt(product.getCreatedAt());
        rsp.setUpdatedAt(product.getUpdatedAt());
        return rsp;
    }
}
