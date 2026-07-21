package com.tk.ai.video.common;

import com.tk.ai.video.module.creativestate.entity.CreativeStateEntity;
import com.tk.ai.video.module.creativestate.mapper.CreativeStateMapper;
import com.tk.ai.video.module.product.entity.ProductEntity;
import com.tk.ai.video.module.product.mapper.ProductMapper;
import com.tk.ai.video.module.videotask.entity.VideoTaskEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreativeContextAssembler {

    private final ProductMapper productMapper;
    private final CreativeStateMapper creativeStateMapper;

    public Map<String, Object> assemble(VideoTaskEntity task) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("productProfile", buildProductProfile(productMapper.selectById(task.getProductId())));

        CreativeStateEntity state = creativeStateMapper.findByTaskId(task.getId()).orElse(null);
        context.put("userRequest", state != null && state.getUserRequirementsJson() != null
                ? state.getUserRequirementsJson() : Map.of());
        context.put("referenceAnalysis", state != null && state.getReferenceVideoJson() != null
                ? state.getReferenceVideoJson() : Map.of());
        context.put("assetAnalysis", task.getAssetAnalysis() != null ? task.getAssetAnalysis() : Map.of());

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("taskMode", task.getTaskMode());
        workflow.put("durationSeconds", task.getDuration());
        workflow.put("videoType", task.getVideoType());
        workflow.put("targetProductCategory", task.getProductCategory());
        context.put("workflow", workflow);
        return context;
    }

    private Map<String, Object> buildProductProfile(ProductEntity product) {
        if (product == null) {
            return Map.of();
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", product.getName());
        profile.put("description", product.getDescription());
        profile.put("productLink", product.getProductLink());
        profile.put("category", product.getCategory());
        profile.put("targetMarket", product.getTargetMarket());
        profile.put("language", product.getLanguage());
        profile.put("sellingPoints", product.getSellingPoints());
        profile.put("painPoints", product.getPainPoints());
        profile.put("targetAudience", product.getTargetAudience());
        profile.put("scenes", product.getScenes());
        profile.put("recommendedVideoTypes", product.getRecommendedVideoTypes());
        profile.put("riskTips", product.getRiskTips());
        return profile;
    }
}
